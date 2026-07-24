package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.VersionHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async put-item builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractPutBuilder<T, S extends AbstractPutBuilder<T, S>> {
    protected static final Logger LOG = Logging.getLogger(AbstractPutBuilder.class);

    protected final T item;
    protected ConditionExpression conditionExpression;
    protected ReturnValue returnValues;
    protected boolean optimisticLocking;

    protected AbstractPutBuilder(@NonNull T item) {
        this.item = item;
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     */
    protected abstract S self();

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    public @NonNull S condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return self();
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    public @NonNull S condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return self();
    }

    /**
     * Adds a condition that the specified attribute must not already exist
     * on an item with the same key for the put to succeed.
     *
     * @param attribute the attribute name to check for absence
     * @return this builder for chaining
     */
    public @NonNull S onlyIfNotExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().notExists(attribute).build();
        return self();
    }

    /**
     * Configures which item attributes to return after the put.
     * <p>
     * When set, uses the low-level {@code PutItemRequest} with the specified
     * {@code ReturnValues}. Common values: {@link ReturnValue#ALL_OLD}
     * (returns the previous item if one existed), {@link ReturnValue#NONE}.
     * <p>
     * To retrieve the previous item when using {@code ReturnValue#ALL_OLD},
     * use {@code executeReturning()} instead of {@code execute()}.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    public @NonNull S returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return self();
    }

    /**
     * Enables optimistic locking for this put operation.
     * <p>
     * When enabled, the library adds a condition expression checking that the
     * version attribute hasn't changed, and increments the version on success.
     *
     * @return this builder for chaining
     */
    public @NonNull S withOptimisticLocking() {
        this.optimisticLocking = true;
        return self();
    }

    /**
     * Applies optimistic locking by merging a version condition into the
     * existing condition expression (if any).
     */
    protected void applyOptimisticLocking() {
        if (!optimisticLocking) {
            return;
        }
        ConditionExpression versionCondition = VersionHelper.buildCondition(item);
        if (versionCondition == null) {
            return;
        }

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            conditionExpression = ConditionExpression.builder()
                    .group(conditionExpression)
                    .and()
                    .group(versionCondition)
                    .build();
        } else {
            conditionExpression = versionCondition;
        }
    }

    /**
     * Increments the version field on the item if optimistic locking is enabled.
     */
    protected void incrementVersion() {
        if (optimisticLocking) {
            VersionHelper.incrementVersion(item);
        }
    }

    /**
     * Builds a high-level {@link PutItemEnhancedRequest} with the item
     * and optional condition expression.
     */
    @SuppressWarnings("unchecked")
    protected @NonNull PutItemEnhancedRequest<T> buildEnhancedRequest() {
        PutItemEnhancedRequest.Builder<T> requestBuilder = PutItemEnhancedRequest
                .builder((Class<T>) item.getClass())
                .item(item);
        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }
        return requestBuilder.build();
    }

    /**
     * Builds a low-level {@link PutItemRequest} from the given item attribute map,
     * using the configured return values and condition expression.
     */
    protected @NonNull PutItemRequest buildLowLevelRequest(@NonNull Map<String, AttributeValue> itemMap) {
        PutItemRequest.Builder requestBuilder = PutItemRequest.builder()
                .tableName(tableName())
                .item(itemMap)
                .returnValues(returnValues);
        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder
                    .conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(conditionExpression.getExpressionNames())
                    .expressionAttributeValues(conditionExpression.getExpressionValues());
        }
        return requestBuilder.build();
    }

    /**
     * Returns the DynamoDB table name.
     */
    protected abstract @NonNull String tableName();
}
