package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for updating an item in a DynamoDB table.
 * Supports both full-item replacement and partial updates via an
 * {@link UpdateExpression}. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
@SuppressWarnings("PMD.CognitiveComplexity")
public class AsyncUpdateBuilder<T> {
    private static final Logger LOG = Logging.getLogger(AsyncUpdateBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final T item;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    @Nullable
    private Map<String, AttributeValue> keyMap;
    private UpdateExpression updateExpression;
    private ConditionExpression conditionExpression;
    private boolean ignoreNulls = true;
    private ReturnValue returnValues;

    AsyncUpdateBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull T item,
                       @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Package-private key-only constructor for use from {@link AsyncTable}.
     * Skips the item parameter and builds the key map directly from partition
     * (and optionally sort) key values.
     */
    AsyncUpdateBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull DynamoDbAsyncClient dynamoDbAsyncClient,
                       @NonNull Object partitionKey, @Nullable Object sortKey) {
        this.table = table;
        this.item = null;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        buildKeyMapFromValues(partitionKey, sortKey);
    }

    /**
     * Defines a partial update expression ({@code SET}, {@code REMOVE}, {@code ADD}, {@code DELETE}).
     * When set, this replaces the full-item replacement with a targeted partial update.
     *
     * @param updateBuilder a consumer that configures the {@link UpdateExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncUpdateBuilder<T> update(@NonNull Consumer<UpdateExpression> updateBuilder) {
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
    @NonNull
    public AsyncUpdateBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
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
    @NonNull
    public AsyncUpdateBuilder<T> condition(@Nullable ConditionExpression condition) {
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
    @NonNull
    public AsyncUpdateBuilder<T> ignoreNulls(boolean ignoreNulls) {
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
    @NonNull
    public AsyncUpdateBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return this;
    }

    /**
     * Executes the update operation. If a partial update expression was configured
     * via {@link #update(Consumer)}, a targeted partial update is performed via the
     * low-level async DynamoDB client. Otherwise, the full item is replaced.
     *
     * @return a {@link CompletableFuture} containing the updated item, or empty if not found
     */
    @NonNull
    public CompletableFuture<Optional<T>> execute() {
        if (!ignoreNulls && updateExpression != null && !updateExpression.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "ignoreNulls(false) has no effect when using partial updates via update(Consumer). "
                            + "Remove the ignoreNulls() call or use a full-item update instead."));
        }
        if (returnValues != null && !(updateExpression != null && !updateExpression.isEmpty())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "ReturnValues is not supported for full-item replacement. " +
                            "Use partial updates with update(expr -> ...) to configure ReturnValues."));
        }
        if (item == null && !(updateExpression != null && !updateExpression.isEmpty())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Key-only update requires a partial update expression. "
                            + "Use update(expr -> ...) to configure an update expression."));
        }
        long start = System.nanoTime();
        if (updateExpression != null && !updateExpression.isEmpty()) {
            return executeWithExpression()
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncUpdate on table '{}' completed in {}ms (expression)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return Optional.ofNullable(result);
                    });
        }
        return executeWithFullItem()
                .thenApply(result -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncUpdate on table '{}' completed in {}ms (full item)",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return Optional.ofNullable(result);
                });
    }

    // ---- Full item replacement ----

    private CompletableFuture<T> executeWithFullItem() {
        UpdateItemEnhancedRequest.Builder<T> requestBuilder =
                UpdateItemEnhancedRequest.builder((Class<T>) item.getClass())
                        .item(item)
                        .ignoreNullsMode(ignoreNulls ? IgnoreNullsMode.SCALAR_ONLY : IgnoreNullsMode.DEFAULT);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        return table.updateItem(requestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("UpdateItem", table.tableName()));
    }

    // ---- Partial update via low-level async client ----

    private CompletableFuture<T> executeWithExpression() {
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

        return dynamoDbAsyncClient.updateItem(requestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("UpdateItem", table.tableName()))
                .thenApply(response -> table.tableSchema().mapToItem(response.attributes()));
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
