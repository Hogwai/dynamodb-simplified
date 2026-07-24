package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractScanBuilder;
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
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent async builder for scanning a DynamoDB table with optional filter,
 * projection, and pagination.
 * <p>
 * Mirrors the sync {@code ScanBuilder} but returns {@link CompletableFuture}
 * from all execution methods and uses the AWS SDK's async
 * {@link software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher} under the hood.
 *
 * @param <T> the type of the item
 */
public class AsyncScanBuilder<T> extends AbstractScanBuilder<T, AsyncScanBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(AsyncScanBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncScanBuilder} for the given async table.
     *
     * @param table the async DynamoDB table
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncTable<T> table) {
        this(table, null);
    }

    /**
     * Constructs a new {@code AsyncScanBuilder} for scanning the given async secondary index.
     *
     * @param index the async DynamoDB secondary index
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncIndex<T> index) {
        this(index, null);
    }

    /**
     * Constructs a new {@code AsyncScanBuilder} for the given async table with a low-level client.
     *
     * @param table               the async DynamoDB table
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable)
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncTable<T> table, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.table = table;
        this.index = null;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Constructs a new {@code AsyncScanBuilder} for the given async index with a low-level client.
     *
     * @param index               the async DynamoDB secondary index
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable)
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncIndex<T> index, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.table = null;
        this.index = index;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncScanBuilder<T> self() {
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
     * Executes the scan asynchronously and returns all matching items
     * aggregated from all pages.
     * <p>
     * <b>Memory warning:</b> This method collects all pages into a single in-memory
     * list before returning. For scans that may return a large number of items,
     * consider using {@link #executeWithPagination()} or processing pages
     * individually to avoid excessive memory usage.
     *
     * @return a {@link CompletableFuture} containing a list of matching items
     */
    public @NonNull CompletableFuture<List<T>> executeAll() {
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
                        LOG.debug("AsyncScan on table '{}' returned {} items in {}ms",
                                tableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return results;
                });
    }

    /**
     * Executes the scan asynchronously and returns only the first page of
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
                            LOG.debug("AsyncScan on table '{}' returned {} items in {}ms (first page)",
                                    tableName(), first.items().size(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncScan on table '{}' returned 0 items in {}ms (first page)",
                                tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return new PagedResult<>(Collections.emptyList(), null);
                });
    }

    /**
     * Executes the scan asynchronously and returns the first matching item, if any.
     * <p>Only the first page of results is loaded: subsequent pages are never fetched.</p>
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
                LOG.debug("AsyncScan on table '{}' returned first item in {}ms",
                        tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        });
    }

    /**
     * Returns a reactive publisher that lazily emits items as pages arrive.
     * <p>
     * Unlike {@link #executeAll()} which loads all pages into memory, this method
     * streams items one-by-one as each page is received from DynamoDB.
     *
     * @return a {@link CompletableFuture} containing an {@link SdkPublisher}
     * that emits scan items
     * @throws IllegalStateException if called with Select.COUNT
     */
    public @NonNull CompletableFuture<SdkPublisher<T>> executeStream() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(Messages.SELECT_COUNT_FMT.formatted("executeStream()")));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncScan stream on table '{}'", tableName());
        }
        return CompletableFuture.completedFuture(
                buildPagePublisher().flatMapIterable(Page::items));
    }

    /**
     * Returns the total number of items asynchronously by iterating all scan
     * result pages.
     * <p>
     * When a low-level {@link DynamoDbAsyncClient} is available, uses
     * {@code Select.COUNT} server-side to avoid transferring item data.
     * Falls back to the enhanced client page iteration otherwise.
     *
     * @return a {@link CompletableFuture} containing the total count of
     * scanned items matching the filter
     */
    public @NonNull CompletableFuture<Long> count() {
        long start = System.nanoTime();
        if (dynamoDbAsyncClient != null) {
            return countWithLowLevel(select != null ? select : Select.COUNT)
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncScan count on table '{}' returned {} items in {}ms",
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
                        LOG.debug("AsyncScan count on table '{}' returned {} items in {}ms",
                                tableName(), total, (System.nanoTime() - start) / 1_000_000);
                    }
                    return total;
                });
    }

    private @NonNull CompletableFuture<Long> countWithLowLevel(Select select) {
        ScanRequest request = buildCountRequest(select);
        return dynamoDbAsyncClient.scan(request)
                .thenApply(r -> (long) r.count())
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.SCAN.getOperationName(), request.tableName()));
    }

    // endregion

    // region Internal

    /**
     * Builds the scan request and returns the raw publisher without collecting pages.
     */
    private @NonNull SdkPublisher<Page<T>> buildPagePublisher() {
        ScanEnhancedRequest request = buildScanRequest();
        if (index != null) {
            return index.scan(request);
        }
        return table.scan(request);
    }

    /**
     * Builds the request, executes it via the async table, and collects all
     * pages into a list through a {@link PagePublisher}.
     */
    private @NonNull CompletableFuture<List<Page<T>>> executeAsPages() {
        ScanEnhancedRequest request = buildScanRequest();
        CompletableFuture<List<Page<T>>> result;
        if (index != null) {
            result = PageCollector.collectPages(index.scan(request));
        } else {
            Objects.requireNonNull(table, "table must not be null");
            result = PageCollector.collectPages(table.scan(request));
        }
        return result.exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.SCAN.getOperationName(), tableName()));
    }

    // endregion
}
