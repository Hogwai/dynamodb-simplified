package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async delete-item builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractDeleteBuilder<T, S extends AbstractDeleteBuilder<T, S>> {
    protected static final Logger LOG = Logging.getLogger(AbstractDeleteBuilder.class);

    protected final Object partitionKey;
    @Nullable
    protected final Object sortKey;
    protected ConditionExpression conditionExpression;
    protected ReturnValue returnValues;

    protected AbstractDeleteBuilder(@NonNull Object partitionKey, @Nullable Object sortKey) {
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     */
    protected abstract S self();

    /**
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
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
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
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
     * Adds a condition that the specified attribute must exist on the item
     * for the deletion to succeed.
     *
     * @param attribute the attribute name to check for existence
     * @return this builder for chaining
     */
    public @NonNull S onlyIfExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().exists(attribute).build();
        return self();
    }

    /**
     * Configures the return values for the delete operation.
     * When set (e.g., {@link ReturnValue#ALL_OLD}), the operation falls back to the
     * low-level client to include the return values in the request.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    public @NonNull S returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return self();
    }

    /**
     * Builds a high-level {@link DeleteItemEnhancedRequest} from the current key
     * and optional condition expression.
     */
    protected @NonNull DeleteItemEnhancedRequest buildEnhancedRequest() {
        DeleteItemEnhancedRequest.Builder requestBuilder =
                DeleteItemEnhancedRequest.builder().key(k -> {
                    k.partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
                    }
                });
        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }
        return requestBuilder.build();
    }

    /**
     * Builds a low-level {@link DeleteItemRequest} from the given key map,
     * using the configured return values and condition expression.
     */
    protected @NonNull DeleteItemRequest buildLowLevelRequest(@NonNull Map<String, AttributeValue> keyMap) {
        DeleteItemRequest.Builder requestBuilder = DeleteItemRequest.builder()
                .tableName(tableName())
                .key(keyMap)
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
     * Builds a key map for low-level requests using the given key attribute names.
     */
    protected @NonNull Map<String, AttributeValue> buildKeyMap(@NonNull String pkName, @Nullable String skName) {
        Map<String, AttributeValue> keyMap = new HashMap<>();
        keyMap.put(pkName, AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (skName != null && sortKey != null) {
            keyMap.put(skName, AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return keyMap;
    }

    /**
     * Returns the DynamoDB table name.
     */
    protected abstract @NonNull String tableName();
}
