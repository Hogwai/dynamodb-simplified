package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async scan builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractScanBuilder<T, S extends AbstractScanBuilder<T, S>> {
    protected static final Logger LOG = Logging.getLogger(AbstractScanBuilder.class);

    protected FilterExpression filterExpression;
    protected ProjectionExpression projectionExpression;
    protected Integer limit;
    protected Map<String, AttributeValue> exclusiveStartKey;
    protected Boolean consistentRead = false;
    protected ReturnConsumedCapacity returnConsumedCapacity;
    protected Select select;
    protected Integer totalSegments;
    protected Integer segment;

    protected AbstractScanBuilder() {
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     */
    protected abstract S self();

    /**
     * Returns the DynamoDB table name.
     */
    protected abstract @NonNull String tableName();

    /**
     * Returns the DynamoDB index name, or {@code null} if the scan targets the base table.
     */
    @Nullable
    protected abstract String indexName();

    // region Filter

    /**
     * Configures a scan filter expression using a {@link FilterExpression} consumer.
     *
     * @param filterBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    public @NonNull S filter(@NonNull Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return self();
    }

    /**
     * Configures a scan filter expression from a pre-built {@link FilterExpression}.
     *
     * @param filter the filter expression
     * @return this builder for chaining
     */
    public @NonNull S filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return self();
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
    public @NonNull S filter(@NonNull Map<String, Object> conditions) {
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
        return self();
    }

    // endregion

    // region Projection

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    public @NonNull S project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return self();
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    public @NonNull S project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return self();
    }

    // endregion

    // region Parallel Scan

    /**
     * Configures a parallel scan by specifying the total number of segments and
     * the segment index for this scan worker.
     *
     * @param totalSegments the total number of segments to divide the table into
     * @param segment       the segment index for this worker (0-based)
     * @return this builder for chaining
     */
    public @NonNull S parallelScan(int totalSegments, int segment) {
        this.totalSegments = totalSegments;
        this.segment = segment;
        return self();
    }

    // endregion

    // region Options

    /**
     * Limits the number of items evaluated per page.
     *
     * @param limit the maximum number of items to evaluate per page
     * @return this builder for chaining
     */
    public @NonNull S limit(int limit) {
        this.limit = limit;
        return self();
    }

    /**
     * Sets the exclusive start key for paginated scans.
     * Typically obtained from the {@link dev.hogwai.dynamodb.simplified.result.PagedResult#lastEvaluatedKey()}
     * of a previous scan.
     *
     * @param lastEvaluatedKey the key map from which to start the next page
     * @return this builder for chaining
     */
    public @NonNull S startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return self();
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    public @NonNull S consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return self();
    }

    /**
     * Configures the level of consumed capacity that should be returned by the scan.
     *
     * @param returnConsumedCapacity the {@link ReturnConsumedCapacity} value
     * @return this builder for chaining
     */
    public @NonNull S returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return self();
    }

    /**
     * Sets the select parameter for the scan, controlling which attributes are returned.
     * Use {@link Select#COUNT} to request only the item count from the server.
     *
     * @param select the select parameter (nullable)
     * @return this builder for chaining
     */
    public @NonNull S select(@Nullable Select select) {
        this.select = select;
        return self();
    }

    // endregion

    // region Request Building

    /**
     * Builds a high-level {@link ScanEnhancedRequest} from the configured parameters.
     */
    protected @NonNull ScanEnhancedRequest buildScanRequest() {
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

    /**
     * Builds a low-level {@link ScanRequest} for count operations
     * using the given {@link Select} mode.
     */
    protected @NonNull ScanRequest buildCountRequest(@NonNull Select select) {
        ScanRequest.Builder builder = ScanRequest.builder()
                .tableName(tableName()).select(select);
        applyFilterIfPresent(builder);
        applyLimitIfPresent(builder);
        applyExclusiveStartKeyIfPresent(builder);
        applyConsistentReadIfPresent(builder);
        applyParallelScanIfPresent(builder);
        applyReturnConsumedCapacityIfPresent(builder);
        applyIndexNameIfPresent(builder);
        return builder.build();
    }

    // endregion

    // region Low-level request helpers

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
        String name = indexName();
        if (name != null) {
            builder.indexName(name);
        }
    }

    // endregion
}
