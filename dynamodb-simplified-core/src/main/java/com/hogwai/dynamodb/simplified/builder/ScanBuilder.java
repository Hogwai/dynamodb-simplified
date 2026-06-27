package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A fluent builder for scanning a DynamoDB table with optional filter, projection,
 * parallel scan segments, and pagination. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public class ScanBuilder<T> {
    private static final Logger LOG = Logging.getLogger(ScanBuilder.class);

    private final DynamoDbTable<T> table;
    private final DynamoDbIndex<T> index;
    private final DynamoDbClient dynamoDbClient;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Integer totalSegments;
    private Integer segment;
    private Boolean consistentRead = false;
    private ReturnConsumedCapacity returnConsumedCapacity;
    private Select select;

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
        this.table = null;
        this.index = index;
        this.dynamoDbClient = dynamoDbClient;
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

    /**
     * Configures a scan filter from a map of attribute-value pairs.
     * <p>
     * All conditions are combined with AND. Each entry is treated as
     * an equality filter. For other condition types, use {@link #filter(Consumer)}.
     *
     * @param conditions a map of attribute names to their expected values
     * @return this builder for chaining
     */
    public @NonNull ScanBuilder<T> filter(@NonNull Map<String, Object> conditions) {
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

    /**
     * Sets the select parameter for the scan, controlling which attributes are returned.
     * Use {@link Select#COUNT} to request only the item count from the server.
     *
     * @param select the select parameter (nullable)
     * @return this builder for chaining
     */
    public @NonNull ScanBuilder<T> select(@Nullable Select select) {
        this.select = select;
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
        if (select == Select.COUNT) {
            throw new IllegalStateException("Cannot call executeAll() with Select.COUNT. Use count() instead.");
        }
        long start = System.nanoTime();
        try {
            List<T> results = executeAsPages().stream()
                    .flatMap(page -> page.items().stream())
                    .toList();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scan on table '{}' returned {} items in {}ms",
                        getTableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
            }
            return results;
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", getTableName(), e);
        }
    }

    /**
     * Executes the scan and returns the first matching item, if any.
     *
     * @return an {@link Optional} containing the first item, or empty if no items match
     */
    public @NonNull Optional<T> executeAndGetFirst() {
        if (select == Select.COUNT) {
            throw new IllegalStateException("Cannot call executeAndGetFirst() with Select.COUNT. Use count() instead.");
        }
        long start = System.nanoTime();
        try {
            PagedResult<T> firstPage = executeWithPagination();
            Optional<T> result = firstPage.items().stream().findFirst();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scan on table '{}' returned first item in {}ms",
                        getTableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", getTableName(), e);
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
            throw new IllegalStateException("Cannot call executeStream() with Select.COUNT. Use count() instead.");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scan stream on table '{}'", getTableName());
        }
        try {
            return executeAsPages().stream()
                    .flatMap(page -> page.items().stream());
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", getTableName(), e);
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
            throw new IllegalStateException("Cannot call executeWithPagination() with Select.COUNT. Use count() instead.");
        }
        long start = System.nanoTime();
        try {
            Iterator<Page<T>> pages = executeAsPages().iterator();
            if (pages.hasNext()) {
                Page<T> firstPage = pages.next();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scan on table '{}' returned {} items in {}ms (first page)",
                            getTableName(), firstPage.items().size(), (System.nanoTime() - start) / 1_000_000);
                }
                return new PagedResult<>(
                        firstPage.items(),
                        firstPage.lastEvaluatedKey()
                );
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scan on table '{}' returned 0 items in {}ms (first page)",
                        getTableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return new PagedResult<>(Collections.emptyList(), null);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", getTableName(), e);
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
                            getTableName(), result, (System.nanoTime() - start) / 1_000_000);
                }
                return result;
            }
            long total = 0;
            for (Page<T> page : executeAsPages()) {
                total += page.count();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Count on table '{}' returned {} items in {}ms",
                        getTableName(), total, (System.nanoTime() - start) / 1_000_000);
            }
            return total;
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", getTableName(), e);
        }
    }

    private long countWithLowLevel(Select select) {
        ScanRequest request = buildCountRequest(select);
        try {
            return dynamoDbClient.scan(request).count();
        } catch (DynamoDbException e) {
            throw new OperationFailedException("Scan", request.tableName(), e);
        }
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

    private String getTableName() {
        return table != null ? table.tableName() : index.tableName();
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
