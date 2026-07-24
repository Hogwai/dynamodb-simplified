package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async get-item builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractGetItemBuilder<T, S extends AbstractGetItemBuilder<T, S>> {
    protected static final Logger LOG = Logging.getLogger(AbstractGetItemBuilder.class);

    protected final Object partitionKey;
    @Nullable
    protected final Object sortKey;
    protected ProjectionExpression projectionExpression;
    protected boolean consistentRead = false;

    protected AbstractGetItemBuilder(@NonNull Object partitionKey, @Nullable Object sortKey) {
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     */
    protected abstract S self();

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
     * Builds a high-level {@link GetItemEnhancedRequest} from the current key and settings.
     */
    protected @NonNull GetItemEnhancedRequest buildEnhancedRequest() {
        return GetItemEnhancedRequest.builder()
                .key(k -> {
                    k.partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
                    }
                })
                .consistentRead(consistentRead)
                .build();
    }

    /**
     * Builds a low-level {@link GetItemRequest} from the given key map.
     * Requires a non-null {@link #projectionExpression}.
     */
    protected @NonNull GetItemRequest buildLowLevelRequest(@NonNull Map<String, AttributeValue> keyMap) {
        return GetItemRequest.builder()
                .tableName(tableName())
                .key(keyMap)
                .projectionExpression(projectionExpression.getExpression())
                .expressionAttributeNames(projectionExpression.getExpressionNames())
                .consistentRead(consistentRead)
                .build();
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
