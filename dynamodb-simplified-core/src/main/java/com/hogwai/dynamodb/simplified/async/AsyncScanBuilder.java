package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.internal.PageCollector;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
public class AsyncScanBuilder<T> {
    private static final Logger LOG = Logging.getLogger(AsyncScanBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;
    private ReturnConsumedCapacity returnConsumedCapacity;
    private Integer totalSegments;
    private Integer segment;
    private Select select;

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
     * @param table                the async DynamoDB table
     * @param dynamoDbAsyncClient  the low-level async DynamoDB client (nullable)
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncTable<T> table, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.index = null;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Constructs a new {@code AsyncScanBuilder} for the given async index with a low-level client.
     *
     * @param index                the async DynamoDB secondary index
     * @param dynamoDbAsyncClient  the low-level async DynamoDB client (nullable)
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncIndex<T> index, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = null;
        this.index = index;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    // ============ Filter ============

    /**
     * Configures a scan filter expression using a {@link FilterExpression} consumer.
     *
     * @param filterBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    /**
     * Configures a scan filter expression from a pre-built {@link FilterExpression}.
     *
     * @param filter the filter expression
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    /**
     * Configures a scan filter from a map of attribute-value pairs.
     * <p>
     * All conditions are combined with AND. Each entry is treated as
     * an equality filter. For other condition types, use {@link #filter(Consumer)}.
     *
     * @param conditions a map of attribute names to their expected values
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> filter(@NonNull Map<String, Object> conditions) {
        FilterExpression filter = FilterExpression.builder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            if (!first) {
                filter.and();
            }
            filter.eq(entry.getKey(), entry.getValue());
            first = false;
        }
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Parallel Scan ============

    /**
     * Configures a parallel scan by specifying the total number of segments and
     * the segment index for this scan worker.
     *
     * @param totalSegments the total number of segments to divide the table into
     * @param segment       the segment index for this worker (0-based)
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> parallelScan(int totalSegments, int segment) {
        this.totalSegments = totalSegments;
        this.segment = segment;
        return this;
    }

    // ============ Options ============

    /**
     * Limits the number of items evaluated per page.
     *
     * @param limit the maximum number of items to evaluate per page
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets the exclusive start key for paginated scans.
     * Typically obtained from the {@link PagedResult#lastEvaluatedKey()} of a previous scan.
     *
     * @param lastEvaluatedKey the key map from which to start the next page
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    // ============ Return Consumed Capacity ============

    /**
     * Configures whether to return the consumed capacity for the scan operation.
     *
     * @param returnConsumedCapacity the {@link ReturnConsumedCapacity} value
     * @return this builder for chaining
     */
    @NonNull
    public AsyncScanBuilder<T> returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Sets the select parameter for the scan, controlling which attributes are returned.
     * Use {@link Select#COUNT} to request only the item count from the server.
     *
     * @param select the select parameter (nullable)
     * @return this builder for chaining
     */
    public @NonNull AsyncScanBuilder<T> select(@Nullable Select select) {
        this.select = select;
        return this;
    }

    // ============ Execution ============

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
                new IllegalStateException("Cannot call executeAll() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    List<T> results = pages.stream()
                            .flatMap(page -> page.items().stream())
                            .toList();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncScan on table '{}' returned {} items in {}ms",
                                getTableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return results;
                });
    }

    /**
     * Executes the scan asynchronously and returns only the first page of
     * results along with the last evaluated key for pagination.
     *
     * @return a {@link CompletableFuture} containing a {@link PagedResult}
     *         with the first page of items and the last evaluated key
     *         (may be {@code null} if no more pages)
     */
    public @NonNull CompletableFuture<PagedResult<T>> executeWithPagination() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeWithPagination() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    Iterator<Page<T>> iter = pages.iterator();
                    if (iter.hasNext()) {
                        Page<T> first = iter.next();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncScan on table '{}' returned {} items in {}ms (first page)",
                                    getTableName(), first.items().size(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncScan on table '{}' returned 0 items in {}ms (first page)",
                                getTableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return new PagedResult<>(Collections.emptyList(), null);
                });
    }

    /**
     * Executes the scan asynchronously and returns the first matching item, if any.
     * <p>Only the first page of results is loaded — subsequent pages are never fetched.</p>
     *
     * @return a {@link CompletableFuture} containing an {@link Optional}
     *         with the first item, or empty if no items match
     */
    public @NonNull CompletableFuture<Optional<T>> executeAndGetFirst() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeAndGetFirst() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeWithPagination().thenApply(firstPage -> {
            Optional<T> result = firstPage.items().stream().findFirst();
            if (LOG.isDebugEnabled()) {
                LOG.debug("AsyncScan on table '{}' returned first item in {}ms",
                        getTableName(), (System.nanoTime() - start) / 1_000_000);
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
     *         that emits scan items
     * @throws IllegalStateException if called with Select.COUNT
     */
    public @NonNull CompletableFuture<SdkPublisher<T>> executeStream() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeStream() with Select.COUNT. Use count() instead."));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncScan stream on table '{}'", getTableName());
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
     *         scanned items matching the filter
     */
    public @NonNull CompletableFuture<Long> count() {
        long start = System.nanoTime();
        if (dynamoDbAsyncClient != null) {
            return countWithLowLevel(select != null ? select : Select.COUNT)
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncScan count on table '{}' returned {} items in {}ms",
                                    getTableName(), result, (System.nanoTime() - start) / 1_000_000);
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
                                getTableName(), total, (System.nanoTime() - start) / 1_000_000);
                    }
                    return total;
                });
    }

    private ScanRequest buildCountRequest(Select select) {
        String tableName = table != null ? table.tableName() : index.tableName();
        ScanRequest.Builder builder = ScanRequest.builder()
            .tableName(tableName).select(select);
        applyFilterIfPresent(builder);
        applyLimitIfPresent(builder);
        applyExclusiveStartKeyIfPresent(builder);
        applyConsistentReadIfPresent(builder);
        applyParallelScanIfPresent(builder);
        applyReturnConsumedCapacityIfPresent(builder);
        applyIndexNameIfPresent(builder);
        return builder.build();
    }

    private void applyFilterIfPresent(ScanRequest.Builder builder) {
        if (filterExpression != null && !filterExpression.isEmpty()) {
            builder.filterExpression(filterExpression.getExpression());
            builder.expressionAttributeNames(filterExpression.getExpressionNames());
            builder.expressionAttributeValues(filterExpression.getExpressionValues());
        }
    }

    private void applyLimitIfPresent(ScanRequest.Builder builder) {
        if (limit != null) {
            builder.limit(limit);
        }
    }

    private void applyExclusiveStartKeyIfPresent(ScanRequest.Builder builder) {
        if (exclusiveStartKey != null) {
            builder.exclusiveStartKey(exclusiveStartKey);
        }
    }

    private void applyConsistentReadIfPresent(ScanRequest.Builder builder) {
        if (consistentRead != null) {
            builder.consistentRead(consistentRead);
        }
    }

    private void applyParallelScanIfPresent(ScanRequest.Builder builder) {
        if (totalSegments != null && segment != null) {
            builder.totalSegments(totalSegments);
            builder.segment(segment);
        }
    }

    private void applyReturnConsumedCapacityIfPresent(ScanRequest.Builder builder) {
        if (returnConsumedCapacity != null) {
            builder.returnConsumedCapacity(returnConsumedCapacity);
        }
    }

    private void applyIndexNameIfPresent(ScanRequest.Builder builder) {
        if (index != null) {
            builder.indexName(index.indexName());
        }
    }

    private @NonNull CompletableFuture<Long> countWithLowLevel(Select select) {
        ScanRequest request = buildCountRequest(select);
        return dynamoDbAsyncClient.scan(request)
            .thenApply(r -> (long) r.count())
            .exceptionally(AsyncExceptionMapper.handler("Scan", request.tableName()));
    }

    // ============ Internal ============

    private String getTableName() {
        return table != null ? table.tableName() : index.tableName();
    }

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
        return result.exceptionally(AsyncExceptionMapper.handler("Scan", getTableName()));
    }

    private ScanEnhancedRequest buildScanRequest() {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .consistentRead(consistentRead);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(
                    filterExpression.toSdkExpression()
            );
        }

        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            requestBuilder.attributesToProject(
                    projectionExpression.getProjectedAttributes().toArray(new String[0])
            );
        }

        if (limit != null) {
            requestBuilder.limit(limit);
        }

        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        if (totalSegments != null && segment != null) {
            requestBuilder.totalSegments(totalSegments);
            requestBuilder.segment(segment);
        }

        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }

        return requestBuilder.build();
    }
}
