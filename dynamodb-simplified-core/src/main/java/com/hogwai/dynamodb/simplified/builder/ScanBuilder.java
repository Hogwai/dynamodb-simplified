package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A fluent builder for scanning a DynamoDB table with optional filter, projection,
 * parallel scan segments, and pagination. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class ScanBuilder<T> {
    private final DynamoDbTable<T> table;
    private final DynamoDbIndex<T> index;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Integer totalSegments;
    private Integer segment;
    private Boolean consistentRead = false;
    private ReturnConsumedCapacity returnConsumedCapacity;

    /**
     * Constructs a new {@code ScanBuilder} for the given table.
     *
     * @param table the DynamoDB table
     */
    public ScanBuilder(@NonNull DynamoDbTable<T> table) {
        this.table = table;
        this.index = null;
    }

    /**
     * Constructs a new {@code ScanBuilder} for the given secondary index.
     *
     * @param index the DynamoDB index
     */
    public ScanBuilder(@NonNull DynamoDbIndex<T> index) {
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
    public @NonNull ScanBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
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
    public @NonNull ScanBuilder<T> filter(@Nullable FilterExpression filter) {
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
    public @NonNull ScanBuilder<T> project(@NonNull String... attributes) {
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
    public @NonNull ScanBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
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
    public @NonNull ScanBuilder<T> parallelScan(int totalSegments, int segment) {
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
    public @NonNull ScanBuilder<T> limit(int limit) {
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
    public @NonNull ScanBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
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
    public @NonNull ScanBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    /**
     * Configures the level of consumed capacity that should be returned by the scan.
     *
     * @param returnConsumedCapacity the {@link ReturnConsumedCapacity} value
     * @return this builder for chaining
     */
    public @NonNull ScanBuilder<T> returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    // ============ Execution ============

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
        return executeAsPages().stream()
                               .flatMap(page -> page.items().stream())
                               .toList();
    }

    /**
     * Executes the scan and returns the first matching item, if any.
     *
     * @return an {@link Optional} containing the first item, or empty if no items match
     */
    public @NonNull Optional<T> executeAndGetFirst() {
        return executeAll().stream().findFirst();
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
        return executeAsPages().stream()
                               .flatMap(page -> page.items().stream());
    }

    /**
     * Executes the scan and returns only the first page of results along with
     * the last evaluated key for pagination.
     *
     * @return a {@link PagedResult} containing the first page of items and
     *         the last evaluated key (may be {@code null} if no more pages)
     */
    public @NonNull PagedResult<T> executeWithPagination() {
        Iterator<Page<T>> pages = executeAsPages().iterator();
        if (pages.hasNext()) {
            Page<T> firstPage = pages.next();
            return new PagedResult<>(
                    firstPage.items(),
                    firstPage.lastEvaluatedKey()
            );
        }
        return new PagedResult<>(Collections.emptyList(), null);
    }

    /**
     * Returns the total number of items by iterating all scan result pages.
     *
     * @return the total count of scanned items matching the filter
     */
    public long count() {
        long total = 0;
        for (Page<T> page : executeAsPages()) {
            total += page.count();
        }
        return total;
    }

    private SdkIterable<Page<T>> executeAsPages() {
        ScanEnhancedRequest request = buildScanRequest();
        if (index != null) {
            return index.scan(request);
        }
        Objects.requireNonNull(table, "table must not be null");
        return table.scan(request);
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
            requestBuilder.segment(segment);
            requestBuilder.totalSegments(totalSegments);
        }

        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }

        return requestBuilder.build();
    }
}
