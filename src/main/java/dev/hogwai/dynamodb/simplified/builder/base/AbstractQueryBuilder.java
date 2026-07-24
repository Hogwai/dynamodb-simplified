package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async query builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractQueryBuilder<T, S extends AbstractQueryBuilder<T, S>> {
    protected static final Logger LOG = Logging.getLogger(AbstractQueryBuilder.class);

    protected QueryConditional keyCondition;
    protected FilterExpression filterExpression;
    protected ProjectionExpression projectionExpression;
    protected Boolean scanIndexForward = true;
    protected Integer limit;
    protected Map<String, AttributeValue> exclusiveStartKey;
    protected Boolean consistentRead = false;
    protected ReturnConsumedCapacity returnConsumedCapacity;
    protected Select select;

    /**
     * Describes the type of sort key comparison for a key condition expression.
     */
    protected enum KeyConditionOp {
        EQ, BEGINS_WITH, BETWEEN, GT, GE, LT, LE
    }

    protected KeyConditionOp keyOp;
    protected @Nullable Object pkValue;
    protected @Nullable Object skValue;
    protected @Nullable Object skValue2; // for BETWEEN only

    protected AbstractQueryBuilder() {
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
     * Returns the DynamoDB index name, or {@code null} if the query targets the base table.
     */
    @Nullable
    protected abstract String indexName();

    /**
     * Returns the primary partition key attribute name for the targeted table or index.
     */
    protected abstract @NonNull String primaryPartitionKey();

    /**
     * Returns the primary sort key attribute name for the targeted table or index,
     * or empty if the table/index has no sort key.
     */
    protected abstract @NonNull Optional<String> primarySortKey();

    // region Key Conditions

    /**
     * Sets the key condition to match all items with the given partition key value.
     *
     * @param pkValue the partition key value
     * @return this builder for chaining
     */
    @SuppressWarnings("PMD.NullAssignment")
    public @NonNull S partitionKey(@NonNull Object pkValue) {
        this.pkValue = pkValue;
        this.keyOp = KeyConditionOp.EQ;
        this.skValue = null;
        this.skValue2 = null;
        this.keyCondition = QueryConditional.keyEqualTo(
                KeyUtils.buildKey(AttributeValueConverter.toKeyAttributeValue(pkValue), null)
        );
        return self();
    }

    /**
     * Sets the key condition to match the item with the exact partition key
     * and sort key values.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key value
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyEquals(@NonNull Object pkValue, @NonNull Object skValue) {
        this.partitionKey(pkValue);
        this.skValue = skValue;
        this.keyCondition = QueryConditional.keyEqualTo(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skValue))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key begins with the
     * given prefix within the specified partition.
     *
     * @param pkValue  the partition key value
     * @param skPrefix the sort key prefix string
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyBeginsWith(@NonNull Object pkValue, @NonNull String skPrefix) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.BEGINS_WITH;
        this.skValue = skPrefix;
        this.keyCondition = QueryConditional.sortBeginsWith(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skPrefix))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key falls within
     * the specified range (inclusive) within the given partition.
     *
     * @param pkValue the partition key value
     * @param skLow   the lower bound of the sort key range (inclusive)
     * @param skHigh  the upper bound of the sort key range (inclusive)
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyBetween(
            @NonNull Object pkValue, @NonNull Object skLow, @NonNull Object skHigh) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.BETWEEN;
        this.skValue = skLow;
        this.skValue2 = skHigh;
        this.keyCondition = QueryConditional.sortBetween(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skLow)),
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skHigh))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key is strictly
     * greater than the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (exclusive)
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyGreaterThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.GT;
        this.skValue = skValue;
        this.keyCondition = QueryConditional.sortGreaterThan(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skValue))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key is greater than
     * or equal to the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (inclusive)
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyGreaterThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.GE;
        this.skValue = skValue;
        this.keyCondition = QueryConditional.sortGreaterThanOrEqualTo(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skValue))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key is strictly
     * less than the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (exclusive)
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyLessThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.LT;
        this.skValue = skValue;
        this.keyCondition = QueryConditional.sortLessThan(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skValue))
        );
        return self();
    }

    /**
     * Sets the key condition to match items whose sort key is less than
     * or equal to the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (inclusive)
     * @return this builder for chaining
     */
    public @NonNull S partitionKeyAndSortKeyLessThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.LE;
        this.skValue = skValue;
        this.keyCondition = QueryConditional.sortLessThanOrEqualTo(
                KeyUtils.buildKey(
                        AttributeValueConverter.toKeyAttributeValue(pkValue),
                        AttributeValueConverter.toKeyAttributeValue(skValue))
        );
        return self();
    }

    // endregion

    // region Filter

    /**
     * Configures a post-query filter expression using a {@link FilterExpression} consumer.
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
     * Configures a post-query filter expression from a pre-built {@link FilterExpression}.
     *
     * @param filter the filter expression
     * @return this builder for chaining
     */
    public @NonNull S filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return self();
    }

    /**
     * Configures a query filter from a map of attribute-value pairs.
     * <p>
     * All conditions are combined with AND. Each entry is treated as
     * an equality filter. For other condition types, use the consumer-based
     * {@link #filter(Consumer)} method.
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

    // region Options

    /**
     * Sets the query to return results in descending sort key order.
     *
     * @return this builder for chaining
     */
    public @NonNull S descending() {
        this.scanIndexForward = false;
        return self();
    }

    /**
     * Sets the query to return results in ascending sort key order (the default).
     *
     * @return this builder for chaining
     */
    public @NonNull S ascending() {
        this.scanIndexForward = true;
        return self();
    }

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
     * Sets the exclusive start key for paginated queries.
     * Typically obtained from the last evaluated key of a previous query.
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
     * Sets the return consumed capacity option for the query.
     *
     * @param returnConsumedCapacity the return consumed capacity value
     * @return this builder for chaining
     */
    public @NonNull S returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return self();
    }

    /**
     * Sets the select parameter for the query, controlling which attributes are returned.
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
     * Builds a high-level {@link QueryEnhancedRequest} from the configured parameters.
     */
    protected @NonNull QueryEnhancedRequest buildEnhancedRequest() {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(keyCondition)
                .scanIndexForward(scanIndexForward)
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

        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }

        return requestBuilder.build();
    }

    /**
     * Validates that the partition key has been set.
     *
     * @throws IllegalStateException if {@code pkValue} is null
     */
    protected void requirePartitionKey() {
        if (pkValue == null) {
            throw new IllegalStateException(Messages.PK_NOT_SET);
        }
    }

    /**
     * Builds a DynamoDB key condition expression string for low-level count operations.
     */
    protected @NonNull String buildKeyConditionExpression(
            Map<String, String> expressionNames,
            Map<String, AttributeValue> expressionValues) {
        String pkName = primaryPartitionKey();
        expressionNames.put(ExpressionConstants.PK, pkName);
        expressionValues.put(ExpressionConstants.PK_VAL, AttributeValueConverter.toKeyAttributeValue(pkValue));

        StringBuilder keyExpr = new StringBuilder();
        keyExpr.append(ExpressionConstants.PK).append(" = ").append(ExpressionConstants.PK_VAL);

        Optional<String> skNameOpt = primarySortKey();
        if (skValue != null && skNameOpt.isPresent()) {
            expressionNames.put(ExpressionConstants.SK, skNameOpt.get());
            expressionValues.put(ExpressionConstants.SK_VAL0, AttributeValueConverter.toKeyAttributeValue(skValue));

            switch (keyOp) {
                case BEGINS_WITH -> keyExpr.append(ExpressionConstants.AND)
                        .append(ExpressionConstants.BEGINS_WITH).append(ExpressionConstants.SK).append(", ")
                        .append(ExpressionConstants.SK_VAL0).append(')');
                case BETWEEN -> {
                    expressionValues.put(ExpressionConstants.SK_VAL1, AttributeValueConverter.toKeyAttributeValue(skValue2));
                    keyExpr.append(ExpressionConstants.AND)
                            .append(ExpressionConstants.SK).append(" BETWEEN ")
                            .append(ExpressionConstants.SK_VAL0).append(ExpressionConstants.AND)
                            .append(ExpressionConstants.SK_VAL1);
                }
                case GT ->
                        keyExpr.append(ExpressionConstants.AND).append(ExpressionConstants.SK).append(" > ").append(ExpressionConstants.SK_VAL0);
                case GE -> keyExpr.append(ExpressionConstants.AND).append(ExpressionConstants.SK)
                        .append(ExpressionConstants.GE).append(ExpressionConstants.SK_VAL0);
                case LT ->
                        keyExpr.append(ExpressionConstants.AND).append(ExpressionConstants.SK).append(" < ").append(ExpressionConstants.SK_VAL0);
                case LE -> keyExpr.append(ExpressionConstants.AND).append(ExpressionConstants.SK)
                        .append(ExpressionConstants.LE).append(ExpressionConstants.SK_VAL0);
                case EQ ->
                        keyExpr.append(ExpressionConstants.AND).append(ExpressionConstants.SK).append(" = ").append(ExpressionConstants.SK_VAL0);
            }
        }
        return keyExpr.toString();
    }

    /**
     * Applies the configured filter expression to a low-level {@link QueryRequest.Builder}.
     */
    protected void applyFilterExpression(
            QueryRequest.Builder requestBuilder,
            Map<String, String> keyNames,
            Map<String, AttributeValue> keyValues) {
        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(filterExpression.getExpression());
            requestBuilder.expressionAttributeNames(mergeMaps(keyNames, filterExpression.getExpressionNames()));
            requestBuilder.expressionAttributeValues(mergeMaps(keyValues, filterExpression.getExpressionValues()));
        }
    }

    /**
     * Applies the configured query options (limit, start key, consistent read,
     * scan index forward, return consumed capacity) to a low-level
     * {@link QueryRequest.Builder}.
     */
    protected void applyQueryOptions(QueryRequest.Builder requestBuilder) {
        if (limit != null) {
            requestBuilder.limit(limit);
        }
        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }
        if (consistentRead != null) {
            requestBuilder.consistentRead(consistentRead);
        }
        if (scanIndexForward != null) {
            requestBuilder.scanIndexForward(scanIndexForward);
        }
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
    }

    /**
     * Merges two maps, with the override map taking precedence.
     */
    protected static <K, V> Map<K, V> mergeMaps(Map<K, V> base, Map<K, V> override) {
        Map<K, V> merged = new HashMap<>(base);
        merged.putAll(override);
        return merged;
    }

    // endregion
}
