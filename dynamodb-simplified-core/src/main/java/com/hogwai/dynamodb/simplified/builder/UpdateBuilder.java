package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.Versioned;
import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.internal.VersionHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fluent builder for updating an item in a DynamoDB table.
 * Supports both full-item replacement and partial updates via an
 * {@link UpdateExpression}. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class UpdateBuilder<T> {
    private static final Logger LOG = Logging.getLogger(UpdateBuilder.class);

    private final DynamoDbTable<T> table;
    private final T item;
    private final DynamoDbClient dynamoDbClient;
    @Nullable
    private Map<String, AttributeValue> keyMap;
    private UpdateExpression updateExpression;
    private ConditionExpression conditionExpression;
    private boolean ignoreNulls = true;
    private ReturnValue returnValues;
    private boolean optimisticLocking;

    /**
     * Constructs a new {@code UpdateBuilder} for the given table and item.
     *
     * @param table          the DynamoDB table
     * @param item           the item to update (full replacement data, or
     *                       a template object when used with {@link #update(Consumer)})
     * @param dynamoDbClient the low-level DynamoDB client (required for partial updates)
     */
    public UpdateBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item, @NonNull DynamoDbClient dynamoDbClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Key-only constructor for use from {@link Table} convenience methods.
     * Skips the item parameter and builds the key map directly from partition
     * (and optionally sort) key values.
     */
    public UpdateBuilder(@NonNull DynamoDbTable<T> table, @NonNull DynamoDbClient dynamoDbClient,
                         @NonNull Object partitionKey, @Nullable Object sortKey) {
        this.table = table;
        this.item = null;
        this.dynamoDbClient = dynamoDbClient;
        buildKeyMapFromValues(partitionKey, sortKey);
    }

    /**
     * Defines a partial update expression ({@code SET}, {@code REMOVE}, {@code ADD}, {@code DELETE}).
     * When set, this replaces the full-item replacement with a targeted partial update.
     *
     * @param updateBuilder a consumer that configures the {@link UpdateExpression}
     * @return this builder for chaining
     */
    public @NonNull UpdateBuilder<T> update(@NonNull Consumer<UpdateExpression> updateBuilder) {
        this.updateExpression = UpdateExpression.builder();
        updateBuilder.accept(this.updateExpression);
        return this;
    }

    /**
     * Configures a condition expression that gates the update operation.
     * DynamoDB evaluates this condition <b>before</b> updating the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    public @NonNull UpdateBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return this;
    }

    /**
     * Configures a condition expression that gates the update operation.
     * DynamoDB evaluates this condition <b>before</b> updating the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    public @NonNull UpdateBuilder<T> condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return this;
    }

    /**
     * Controls whether null-valued attributes in the item are ignored during
     * full-item replacement. Has no effect when using a partial update expression.
     *
     * @param ignoreNulls {@code true} (default) to skip null attributes,
     *                    {@code false} to persist them
     * @return this builder for chaining
     */
    public @NonNull UpdateBuilder<T> ignoreNulls(boolean ignoreNulls) {
        this.ignoreNulls = ignoreNulls;
        return this;
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
    public @NonNull UpdateBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return this;
    }

    /**
     * Enables optimistic locking for this update operation.
     * <p>
     * When enabled, the library adds a condition expression checking that the
     * version attribute hasn't changed, and increments the version on success.
     * The item must implement {@link com.hogwai.dynamodb.simplified.Versioned}.
     * <p>
     * This is only supported for full-item replacement. For partial updates
     * ({@link #update(Consumer)}), the item must be provided in the constructor.
     *
     * @return this builder for chaining
     */
    public @NonNull UpdateBuilder<T> withOptimisticLocking() {
        this.optimisticLocking = true;
        return this;
    }

    // ---- Optimistic locking ----

    private void applyOptimisticLocking() {
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

    private void incrementVersion() {
        if (optimisticLocking && item instanceof Versioned v) {
            VersionHelper.incrementVersion(v);
        }
    }

    /**
     * Executes the update operation. If a partial update expression was configured
     * via {@link #update(Consumer)}, a targeted partial update is performed via the
     * low-level DynamoDB client. Otherwise, the full item is replaced.
     *
     * @return the updated item, or empty if not found
     */
    @NonNull
    public Optional<T> execute() {
        if (!ignoreNulls && updateExpression != null && !updateExpression.isEmpty()) {
            throw new IllegalStateException(
                    "ignoreNulls(false) has no effect when using partial updates via update(Consumer). "
                            + "Remove the ignoreNulls() call or use a full-item update instead.");
        }
        boolean hasExpression = hasUpdateExpression();
        applyOptimisticLocking();
        long start = System.nanoTime();
        if (hasExpression) {
            T result = executeWithExpression();
            incrementVersion();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Update on table '{}' completed in {}ms (expression)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return Optional.ofNullable(result);
        }
        T result = executeWithFullItem();
        incrementVersion();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update on table '{}' completed in {}ms (full item)",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
        }
        return Optional.ofNullable(result);
    }

    private boolean hasUpdateExpression() {
        boolean hasExpression = updateExpression != null && !updateExpression.isEmpty();
        if (returnValues != null && !hasExpression) {
            throw new IllegalStateException(
                    "ReturnValues is not supported for full-item replacement. "
                            + "Use partial updates with update(expr -> ...) to configure ReturnValues.");
        }
        if (item == null && !hasExpression) {
            throw new IllegalStateException(
                    "Key-only update requires a partial update expression. "
                            + "Use update(expr -> ...) to configure an update expression.");
        }
        return hasExpression;
    }

    // ---- Full item replacement (existing behavior) ----

    @SuppressWarnings("unchecked")
    private T executeWithFullItem() {
        UpdateItemEnhancedRequest.Builder<T> requestBuilder =
                UpdateItemEnhancedRequest.builder((Class<T>) item.getClass())
                        .item(item)
                        .ignoreNullsMode(ignoreNulls ? IgnoreNullsMode.SCALAR_ONLY : IgnoreNullsMode.DEFAULT);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        try {
            return table.updateItem(requestBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("UpdateItem", table.tableName(), e);
        }
    }

    // ---- Partial update via low-level client ----

    private T executeWithExpression() {
        Map<String, AttributeValue> expressionKeyMap = buildKeyMap();

        Map<String, String> allNames = new HashMap<>(updateExpression.getExpressionNames());
        Map<String, AttributeValue> allValues = new HashMap<>(updateExpression.getExpressionValues());

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName(table.tableName())
                .key(expressionKeyMap)
                .updateExpression(updateExpression.getExpression())
                .expressionAttributeNames(allNames)
                .expressionAttributeValues(allValues)
                .returnValues(returnValues != null ? returnValues : ReturnValue.ALL_NEW);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            allNames.putAll(conditionExpression.getExpressionNames());
            allValues.putAll(conditionExpression.getExpressionValues());
            requestBuilder
                    .conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(allNames)
                    .expressionAttributeValues(allValues);
        }

        Map<String, AttributeValue> result;
        try {
            result = dynamoDbClient.updateItem(requestBuilder.build()).attributes();
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("UpdateItem", table.tableName(), e);
        }

        if (result == null || result.isEmpty()) {
            return null;
        }
        return table.tableSchema().mapToItem(result);
    }

    // ---- Key extraction from item ----

    private Map<String, AttributeValue> buildKeyMap() {
        if (keyMap != null) {
            return keyMap;
        }
        Key key = table.keyFrom(item);
        return key.primaryKeyMap(table.tableSchema());
    }

    private void buildKeyMapFromValues(@NonNull Object partitionKey, @Nullable Object sortKey) {
        TableMetadata metadata = table.tableSchema().tableMetadata();
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(metadata.primaryPartitionKey(), AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            map.put(
                    metadata.primarySortKey()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Table " + table.tableName() + " has no sort key, but a sort key value was provided.")),
                    AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        this.keyMap = map;
    }

}
