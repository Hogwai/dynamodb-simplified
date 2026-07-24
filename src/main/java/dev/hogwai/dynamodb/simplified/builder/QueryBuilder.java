package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractQueryBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.Messages;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.*;
import java.util.stream.Stream;

/**
 * A fluent builder for querying items in a DynamoDB table or index.
 * Supports key conditions, filters, projections, sorting, pagination, and
 * global secondary index usage. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class QueryBuilder<T> extends AbstractQueryBuilder<T, QueryBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(QueryBuilder.class);

    private final DynamoDbTable<T> table;
    private final DynamoDbIndex<T> index;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code QueryBuilder} for the given table.
     *
     * @param table the DynamoDB table
     */
    public QueryBuilder(@NonNull DynamoDbTable<T> table) {
        this(table, null);
    }

    /**
     * Constructs a new {@code QueryBuilder} for querying the given secondary index.
     *
     * @param index the DynamoDB secondary index
     */
    public QueryBuilder(@NonNull DynamoDbIndex<T> index) {
        this(index, null);
    }

    /**
     * Constructs a new {@code QueryBuilder} for the given table with a low-level client.
     *
     * @param table          the DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client (nullable)
     */
    public QueryBuilder(@NonNull DynamoDbTable<T> table, @Nullable DynamoDbClient dynamoDbClient) {
        super();
        this.table = table;
        this.index = null;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Constructs a new {@code QueryBuilder} for querying the given secondary index
     * with a low-level client.
     *
     * @param index          the DynamoDB secondary index
     * @param dynamoDbClient the low-level DynamoDB client (nullable)
     */
    public QueryBuilder(@NonNull DynamoDbIndex<T> index, @Nullable DynamoDbClient dynamoDbClient) {
        super();
        this.table = null;
        this.index = index;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull QueryBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table != null ? table.tableName() : index.tableName();
    }

    @Override
    @Nullable
    protected String indexName() {
        return index != null ? index.indexName() : null;
    }

    @Override
    protected @NonNull String primaryPartitionKey() {
        return table != null
                ? table.tableSchema().tableMetadata().primaryPartitionKey()
                : index.tableSchema().tableMetadata().primaryPartitionKey();
    }

    @Override
    protected @NonNull Optional<String> primarySortKey() {
        return table != null
                ? table.tableSchema().tableMetadata().primarySortKey()
                : index.tableSchema().tableMetadata().primarySortKey();
    }

    // region Execution

    /**
     * Executes the query and returns all matching items aggregated from all pages.
     * <p>
     * <b>Memory warning:</b> all results are loaded into memory. For large result sets,
     * consider using {@link #executeStream()} for lazy iteration or {@link #executeWithPagination()}
     * for page-by-page processing.
     *
     * @return a list of matching items
     */
    @NonNull
    public List<T> executeAll() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeAll"));
        }
        long start = System.nanoTime();
        try {
            List<T> results = executeAsPages().stream()
                    .flatMap(page -> page.items().stream())
                    .toList();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Query on table '{}' returned {} items in {}ms",
                        tableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
            }
            return results;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the query and returns a lazy stream of all matching items.
     * <p>
     * The stream lazily fetches pages as needed, making it suitable for large result sets.
     *
     * @return a stream of matching items
     */
    @NonNull
    public Stream<T> executeStream() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeStream"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Query stream on table '{}'", tableName());
        }
        try {
            return executeAsPages().stream()
                    .flatMap(page -> page.items().stream());
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the query and returns only the first page of results along with
     * the last evaluated key for pagination.
     *
     * @return a {@link PagedResult} containing the first page of items and
     * the last evaluated key (may be {@code null} if no more pages)
     */
    public @NonNull PagedResult<T> executeWithPagination() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeWithPagination"));
        }
        long start = System.nanoTime();
        try {
            Iterator<Page<T>> pages = executeAsPages().iterator();
            if (pages.hasNext()) {
                Page<T> firstPage = pages.next();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Query on table '{}' returned {} items in {}ms (first page)",
                            tableName(), firstPage.items().size(), (System.nanoTime() - start) / 1_000_000);
                }
                return new PagedResult<>(
                        firstPage.items(),
                        firstPage.lastEvaluatedKey()
                );
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Query on table '{}' returned 0 items in {}ms (first page)",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return new PagedResult<>(Collections.emptyList(), null);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the query and returns the first matching item, if any.
     *
     * @return an {@link Optional} containing the first item, or empty if no items match
     */
    public @NonNull Optional<T> executeAndGetFirst() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeAndGetFirst"));
        }
        long start = System.nanoTime();
        try {
            PagedResult<T> firstPage = executeWithPagination();
            Optional<T> result = firstPage.items().stream().findFirst();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Query on table '{}' returned first item in {}ms",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        }
    }

    /**
     * Returns the total number of matching items by iterating all query result pages.
     * <p>
     * When a low-level {@link DynamoDbClient} is available, uses {@code Select.COUNT}
     * server-side to avoid transferring item data. Falls back to the enhanced client
     * page iteration otherwise.
     *
     * @return the total count of matching items
     */
    public long count() {
        long start = System.nanoTime();
        try {
            if (dynamoDbClient != null) {
                long result = countWithLowLevel(select != null ? select : Select.COUNT);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Count on table '{}' returned {} items in {}ms",
                            tableName(), result, (System.nanoTime() - start) / 1_000_000);
                }
                return result;
            }
            long total = 0;
            for (Page<T> page : executeAsPages()) {
                total += page.count();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Count on table '{}' returned {} items in {}ms",
                        tableName(), total, (System.nanoTime() - start) / 1_000_000);
            }
            return total;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tableName(), e);
        }
    }

    // endregion

    // region Low-Level Count

    private long countWithLowLevel(Select select) {
        requirePartitionKey();
        String tblName = tableName();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();

        String keyConditionExpression = buildKeyConditionExpression(expressionNames, expressionValues);

        QueryRequest.Builder requestBuilder = QueryRequest.builder()
                .tableName(tblName)
                .keyConditionExpression(keyConditionExpression)
                .expressionAttributeNames(expressionNames)
                .expressionAttributeValues(expressionValues)
                .select(select);

        applyFilterExpression(requestBuilder, expressionNames, expressionValues);
        applyQueryOptions(requestBuilder);

        if (index != null) {
            requestBuilder.indexName(index.indexName());
        }

        try {
            return dynamoDbClient.query(requestBuilder.build()).count();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.QUERY.getOperationName(), tblName, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.QUERY.getOperationName(), tblName, e);
        }
    }

    // endregion

    // region Internal

    private SdkIterable<Page<T>> executeAsPages() {
        requirePartitionKey();
        QueryEnhancedRequest request = buildEnhancedRequest();
        if (index != null) {
            return index.query(request);
        }
        return table.query(request);
    }

    // endregion
}
// endregion
