package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractScanBuilder;
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
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.*;
import java.util.stream.Stream;

/**
 * A fluent builder for scanning a DynamoDB table with optional filter, projection,
 * parallel scan segments, and pagination. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class ScanBuilder<T> extends AbstractScanBuilder<T, ScanBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(ScanBuilder.class);

    private final DynamoDbTable<T> table;
    private final DynamoDbIndex<T> index;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code ScanBuilder} for the given table.
     *
     * @param table the DynamoDB table
     */
    public ScanBuilder(@NonNull DynamoDbTable<T> table) {
        this(table, null);
    }

    /**
     * Constructs a new {@code ScanBuilder} for the given secondary index.
     *
     * @param index the DynamoDB index
     */
    public ScanBuilder(@NonNull DynamoDbIndex<T> index) {
        this(index, null);
    }

    /**
     * Constructs a new {@code ScanBuilder} for the given table with a low-level client.
     *
     * @param table          the DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client (nullable)
     */
    public ScanBuilder(@NonNull DynamoDbTable<T> table, @Nullable DynamoDbClient dynamoDbClient) {
        super();
        this.table = table;
        this.index = null;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Constructs a new {@code ScanBuilder} for the given secondary index with a low-level client.
     *
     * @param index          the DynamoDB index
     * @param dynamoDbClient the low-level DynamoDB client (nullable)
     */
    public ScanBuilder(@NonNull DynamoDbIndex<T> index, @Nullable DynamoDbClient dynamoDbClient) {
        super();
        this.table = null;
        this.index = index;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull ScanBuilder<T> self() {
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

    // region Execution

    /**
     * Executes the scan and returns all matching items aggregated from all pages.
     * <p>
     * <b>Warning:</b> This method loads all results into memory. For large result sets,
     * consider using {@link #executeStream()} for lazy streaming or paginate manually
     * with {@link #executeWithPagination()}.
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
                LOG.debug("Scan on table '{}' returned {} items in {}ms",
                        tableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
            }
            return results;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the scan and returns the first matching item, if any.
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
                LOG.debug("Scan on table '{}' returned first item in {}ms",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the scan and returns a lazy {@link Stream} of all matching items.
     * The stream is backed by the paginated scan results, so items are fetched
     * on demand as the stream is consumed.
     *
     * @return a stream of matching items
     */
    @NonNull
    public Stream<T> executeStream() {
        if (select == Select.COUNT) {
            throw new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeStream"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scan stream on table '{}'", tableName());
        }
        try {
            return executeAsPages().stream()
                    .flatMap(page -> page.items().stream());
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        }
    }

    /**
     * Executes the scan and returns only the first page of results along with
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
                    LOG.debug("Scan on table '{}' returned {} items in {}ms (first page)",
                            tableName(), firstPage.items().size(), (System.nanoTime() - start) / 1_000_000);
                }
                return new PagedResult<>(
                        firstPage.items(),
                        firstPage.lastEvaluatedKey()
                );
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scan on table '{}' returned 0 items in {}ms (first page)",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return new PagedResult<>(Collections.emptyList(), null);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        }
    }

    /**
     * Returns the total number of items by iterating all scan result pages.
     * <p>
     * When a low-level {@link DynamoDbClient} is available, uses {@code Select.COUNT}
     * server-side to avoid transferring item data. Falls back to the enhanced client
     * page iteration otherwise.
     *
     * @return the total count of scanned items matching the filter
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
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), tableName(), e);
        }
    }

    private long countWithLowLevel(Select select) {
        ScanRequest request = buildCountRequest(select);
        try {
            return dynamoDbClient.scan(request).count();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.SCAN.getOperationName(), request.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.SCAN.getOperationName(), request.tableName(), e);
        }
    }

    // endregion

    // region Internal

    private SdkIterable<Page<T>> executeAsPages() {
        ScanEnhancedRequest request = buildScanRequest();
        if (index != null) {
            return index.scan(request);
        }
        Objects.requireNonNull(table, "table must not be null");
        return table.scan(request);
    }

    // endregion
}
