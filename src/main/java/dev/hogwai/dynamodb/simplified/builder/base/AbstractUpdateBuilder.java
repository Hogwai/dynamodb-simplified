package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.expression.UpdateExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.VersionHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async update-item builders.
 * <p>
 * Supports both full-item replacement and partial updates via an
 * {@link UpdateExpression}. Concrete subclasses provide the DynamoDB
 * table and client references and implement the execute logic.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractUpdateBuilder<T, S extends AbstractUpdateBuilder<T, S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractUpdateBuilder.class);

    protected final T item;
    @Nullable
    protected Map<String, AttributeValue> keyMap;
    protected UpdateExpression updateExpression;
    protected ConditionExpression conditionExpression;
    protected boolean ignoreNulls = true;
    protected ReturnValue returnValues;
    protected boolean optimisticLocking;

    /**
     * Constructs a new {@code AbstractUpdateBuilder} with an item for
     * full-item replacement or as a template for partial updates.
     *
     * @param item the item to update
     */
    protected AbstractUpdateBuilder(@NonNull T item) {
        this.item = item;
    }

    /**
     * No-arg constructor for the key-only path (partial updates without a full item).
     * Subclasses must call {@link #buildKeyMapFromValues(Object, Object)} after
     * setting up the table reference.
     */
    protected AbstractUpdateBuilder() {
        this.item = null;
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
     * Returns a {@link Key} built from the current item.
     * Used internally to build key maps for partial update requests.
     */
    protected abstract @NonNull Key getKeyFromItem();

    /**
     * Returns the table schema for the item type.
     * Used internally for key and attribute conversion.
     */
    protected abstract @NonNull TableSchema<T> getTableSchema();

    /**
     * Defines a partial update expression ({@code SET}, {@code REMOVE}, {@code ADD}, {@code DELETE}).
     * When set, this replaces the full-item replacement with a targeted partial update.
     *
     * @param updateBuilder a consumer that configures the {@link UpdateExpression}
     * @return this builder for chaining
     */
    public @NonNull S update(@NonNull Consumer<UpdateExpression> updateBuilder) {
        this.updateExpression = UpdateExpression.builder();
        updateBuilder.accept(this.updateExpression);
        return self();
    }

    /**
     * Configures a condition expression that gates the update operation.
     * DynamoDB evaluates this condition <b>before</b> updating the item
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
     * Configures a condition expression that gates the update operation.
     * DynamoDB evaluates this condition <b>before</b> updating the item
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
     * Controls whether null-valued attributes in the item are ignored during
     * full-item replacement. Has no effect when using a partial update expression.
     *
     * @param ignoreNulls {@code true} (default) to skip null attributes,
     *                    {@code false} to persist them
     * @return this builder for chaining
     */
    public @NonNull S ignoreNulls(boolean ignoreNulls) {
        this.ignoreNulls = ignoreNulls;
        return self();
    }

    /**
     * Configures which item attributes to return after the update.
     * <p>
     * When set, controls the {@code ReturnValues} parameter of the underlying
     * {@code UpdateItemRequest}. Common values: {@link ReturnValue#ALL_NEW}
     * (default if not set), {@link ReturnValue#NONE}, {@link ReturnValue#ALL_OLD},
     * {@link ReturnValue#UPDATED_NEW}, {@link ReturnValue#UPDATED_OLD}.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    public @NonNull S returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return self();
    }

    /**
     * Enables optimistic locking for this update operation.
     * <p>
     * When enabled, the library adds a condition expression checking that the
     * version attribute hasn't changed, and increments the version on success.
     * <p>
     * This is only supported for full-item replacement. For partial updates
     * ({@link #update(Consumer)}), the item must be provided in the constructor.
     *
     * @return this builder for chaining
     */
    public @NonNull S withOptimisticLocking() {
        this.optimisticLocking = true;
        return self();
    }

    // region Optimistic locking

    /**
     * Applies optimistic locking by merging a version condition into the
     * existing condition expression (if any). Has no effect when the item
     * is {@code null} (key-only update) or when optimistic locking is not enabled.
     */
    protected void applyOptimisticLocking() {
        if (!optimisticLocking || item == null) {
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
     * Increments the version field on the item if optimistic locking is enabled
     * and the item is not null.
     */
    protected void incrementVersion() {
        if (optimisticLocking && item != null) {
            VersionHelper.incrementVersion(item);
        }
    }

    // endregion

    // region Key helpers

    /**
     * Builds a key map for low-level update requests from the item or from a
     * pre-built key map (set via {@link #buildKeyMapFromValues(Object, Object)}).
     */
    protected @NonNull Map<String, AttributeValue> buildKeyMap() {
        if (keyMap != null) {
            return keyMap;
        }
        Key key = getKeyFromItem();
        return key.primaryKeyMap(getTableSchema());
    }

    /**
     * Builds a key map directly from partition and sort key values and stores it
     * for later use by {@link #buildKeyMap()}. Used by the key-only constructor path.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value, or {@code null} if the table has no sort key
     */
    protected final void buildKeyMapFromValues(@NonNull Object partitionKey, @Nullable Object sortKey) {
        TableMetadata metadata = getTableSchema().tableMetadata();
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(metadata.primaryPartitionKey(), AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            map.put(
                    metadata.primarySortKey()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Table " + tableName() + " has no sort key, but a sort key value was provided.")),
                    AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        this.keyMap = map;
    }

    // endregion
}
