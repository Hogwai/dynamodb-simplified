package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.PageCollector;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A fluent async builder for scanning a DynamoDB table with optional filter,
 * projection, and pagination.
 * <p>
 * Mirrors the sync {@code ScanBuilder} but returns {@link CompletableFuture}
 * from all execution methods and uses the AWS SDK's async {@link PagePublisher}
 * under the hood.
 *
 * @param <T> the type of the item
 */
public class AsyncScanBuilder<T> {
    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;
    private ReturnConsumedCapacity returnConsumedCapacity;
    private Integer totalSegments;
    private Integer segment;

    /**
     * Constructs a new {@code AsyncScanBuilder} for the given async table.
     *
     * @param table the async DynamoDB table
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncTable<T> table) {
        this.table = table;
        this.index = null;
    }

    /**
     * Constructs a new {@code AsyncScanBuilder} for scanning the given async secondary index.
     *
     * @param index the async DynamoDB secondary index
     */
    public AsyncScanBuilder(@NonNull DynamoDbAsyncIndex<T> index) {
        this.table = null;
        this.index = index;
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
     * Typically obtained from the {@link PagedResult#getLastEvaluatedKey()} of a previous scan.
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
        return executeAsPages()
                .thenApply(pages -> pages.stream()
                        .flatMap(page -> page.items().stream())
                        .toList());
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
        return executeAsPages()
                .thenApply(pages -> {
                    Iterator<Page<T>> iter = pages.iterator();
                    if (iter.hasNext()) {
                        Page<T> first = iter.next();
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    return new PagedResult<>(Collections.emptyList(), null);
                });
    }

    /**
     * Returns the total number of items asynchronously by iterating all scan
     * result pages.
     *
     * @return a {@link CompletableFuture} containing the total count of
     *         scanned items matching the filter
     */
    public @NonNull CompletableFuture<Long> count() {
        return executeAsPages()
                .thenApply(pages -> {
                    long total = 0;
                    for (Page<T> page : pages) {
                        total += page.count();
                    }
                    return total;
                });
    }

    // ============ Internal ============

    /**
     * Builds the request, executes it via the async table, and collects all
     * pages into a list through a {@link PagePublisher}.
     */
    private @NonNull CompletableFuture<List<Page<T>>> executeAsPages() {
        ScanEnhancedRequest request = buildScanRequest();
        if (index != null) {
            return PageCollector.collectPages(index.scan(request));
        }
        Objects.requireNonNull(table, "table must not be null");
        return PageCollector.collectPages(table.scan(request));
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
