package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractQueryBuilder;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent async builder for querying items in a DynamoDB table.
 * <p>
 * Mirrors the sync {@code QueryBuilder} but returns {@link CompletableFuture}
 * from all execution methods and uses the AWS SDK's async {@link PagePublisher}
 * under the hood.
 * <p>
 * Key conditions, filters, projections, sort order, pagination, and consistent
 * read settings are all supported. For index-specific queries, use
 * {@link AsyncIndex} instead.
 *
 * @param <T> the type of the item
 */
public class AsyncQueryBuilder<T> extends AbstractQueryBuilder<T, AsyncQueryBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(AsyncQueryBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncQueryBuilder} for the given async table.
     *
     * @param table the async DynamoDB table
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncTable<T> table) {
        this(table, null);
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for querying the given async secondary index.
     *
     * @param index the async DynamoDB secondary index
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncIndex<T> index) {
        this(index, null);
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for the given async table with a low-level client.
     *
     * @param table               the async DynamoDB table
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable)
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncTable<T> table, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.table = table;
        this.index = null;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for querying the given async secondary index
     * with a low-level client.
     *
     * @param index               the async DynamoDB secondary index
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable)
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncIndex<T> index, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.table = null;
        this.index = index;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncQueryBuilder<T> self() {
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
     * Executes the query asynchronously and returns all matching items
     * aggregated from all pages.
     * <p>
     * <b>Memory warning:</b> This method eagerly loads <em>all</em> matching
     * items into memory. If the result set may be large, consider using
     * paginated methods such as
     * {@link #executeWithPagination()} to process items incrementally.
     *
     * @return a {@link CompletableFuture} containing a list of matching items
     */
    @NonNull
    public CompletableFuture<List<T>> executeAll() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeAll()")));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    List<T> results = pages.stream()
                            .flatMap(page -> page.items().stream())
                            .toList();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery on table '{}' returned {} items in {}ms",
                                tableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return results;
                });
    }

    /**
     * Executes the query asynchronously and returns only the first page of
     * results along with the last evaluated key for pagination.
     *
     * @return a {@link CompletableFuture} containing a {@link PagedResult}
     * with the first page of items and the last evaluated key
     * (may be {@code null} if no more pages)
     */
    public @NonNull CompletableFuture<PagedResult<T>> executeWithPagination() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeWithPagination()")));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    Iterator<Page<T>> iter = pages.iterator();
                    if (iter.hasNext()) {
                        Page<T> first = iter.next();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncQuery on table '{}' returned {} items in {}ms (first page)",
                                    tableName(), first.items().size(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery on table '{}' returned 0 items in {}ms (first page)",
                                tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return new PagedResult<>(Collections.emptyList(), null);
                });
    }

    /**
     * Executes the query asynchronously and returns the first matching item,
     * if any.
     *
     * @return a {@link CompletableFuture} containing an {@link Optional}
     * with the first item, or empty if no items match
     */
    public @NonNull CompletableFuture<Optional<T>> executeAndGetFirst() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeAndGetFirst()")));
        }
        long start = System.nanoTime();
        return executeWithPagination().thenApply(firstPage -> {
            Optional<T> result = firstPage.items().stream().findFirst();
            if (LOG.isDebugEnabled()) {
                LOG.debug("AsyncQuery on table '{}' returned first item in {}ms",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        });
    }

    /**
     * Returns a reactive publisher that streams query results lazily.
     *
     * @return an {@link SdkPublisher} that emits query items
     * @throws IllegalStateException if called with Select.COUNT
     */
    @NonNull
    public SdkPublisher<T> streamResults() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("streamResults()"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncQuery stream on table '{}'", tableName());
        }
        return buildPagePublisher().flatMapIterable(Page::items);
    }

    /**
     * Returns the total number of matching items asynchronously by iterating
     * all query result pages.
     * <p>
     * When a low-level {@link DynamoDbAsyncClient} is available, uses
     * {@code Select.COUNT} server-side to avoid transferring item data.
     * Falls back to the enhanced client page iteration otherwise.
     *
     * @return a {@link CompletableFuture} containing the total count of
     * matching items
     */
    public @NonNull CompletableFuture<Long> count() {
        long start = System.nanoTime();
        if (dynamoDbAsyncClient != null) {
            return countWithLowLevel(select != null ? select : Select.COUNT)
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncQuery count on table '{}' returned {} items in {}ms",
                                    tableName(), result, (System.nanoTime() - start) / 1_000_000);
                        }
                        return result;
                    });
        }
        return executeAsPages()
                .thenApply(pages -> {
                    long total = 0;
                    for (Page<T> page : pages) {
                        total += page.count();
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery count on table '{}' returned {} items in {}ms",
                                tableName(), total, (System.nanoTime() - start) / 1_000_000);
                    }
                    return total;
                });
    }

    // endregion

    // region Low-Level Count

    private @NonNull CompletableFuture<Long> countWithLowLevel(Select select) {
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

        return dynamoDbAsyncClient.query(requestBuilder.build())
                .thenApply(r -> (long) r.count())
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.QUERY.getOperationName(), tblName));
    }

    // endregion

    // region Internal

    /**
     * Builds the query request and returns the raw publisher without collecting pages.
     */
    private @NonNull SdkPublisher<Page<T>> buildPagePublisher() {
        requirePartitionKey();
        QueryEnhancedRequest request = buildEnhancedRequest();
        if (index != null) {
            return index.query(request);
        }
        return table.query(request);
    }

    /**
     * Builds the request, executes it via the async table, and collects all
     * pages into a list through a {@link PagePublisher}.
     */
    private @NonNull CompletableFuture<List<Page<T>>> executeAsPages() {
        try {
            requirePartitionKey();
            QueryEnhancedRequest request = buildEnhancedRequest();
            if (index != null) {
                return PageCollector.collectPages(index.query(request));
            }
            return PageCollector.collectPages(table.query(request));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // endregion
}
// endregion
