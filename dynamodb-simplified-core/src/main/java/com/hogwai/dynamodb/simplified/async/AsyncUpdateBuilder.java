package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for updating an item in a DynamoDB table.
 * Supports both full-item replacement and partial updates via an
 * {@link UpdateExpression}. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncUpdateBuilder<T> {
    private final DynamoDbAsyncTable<T> table;
    private final T item;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private UpdateExpression updateExpression;
    private FilterExpression conditionExpression;
    private boolean ignoreNulls = true;

    AsyncUpdateBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull T item,
                       @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
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
     * Configures a condition expression using a {@link FilterExpression} consumer.
     * The update will only succeed if the condition evaluates to true.
     *
     * @param conditionBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncUpdateBuilder<T> condition(@NonNull Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    /**
     * Configures a condition expression from a pre-built {@link FilterExpression}.
     * The update will only succeed if the condition evaluates to true.
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncUpdateBuilder<T> condition(@Nullable FilterExpression condition) {
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
     * Executes the update operation. If a partial update expression was configured
     * via {@link #update(Consumer)}, a targeted partial update is performed via the
     * low-level async DynamoDB client. Otherwise, the full item is replaced.
     *
     * @return a {@link CompletableFuture} containing the updated item
     */
    @NonNull
    public CompletableFuture<T> execute() {
        if (updateExpression != null && !updateExpression.isEmpty()) {
            return executeWithExpression();
        }
        return executeWithFullItem();
    }

    // ---- Full item replacement ----

    private CompletableFuture<T> executeWithFullItem() {
        UpdateItemEnhancedRequest.Builder<T> requestBuilder =
                UpdateItemEnhancedRequest.builder((Class<T>) item.getClass())
                                         .item(item)
                                         .ignoreNullsMode(ignoreNulls ? IgnoreNullsMode.SCALAR_ONLY : IgnoreNullsMode.DEFAULT);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    Expression.builder()
                              .expression(conditionExpression.getExpression())
                              .expressionNames(conditionExpression.getExpressionNames())
                              .expressionValues(conditionExpression.getExpressionValues())
                              .build()
            );
        }

        return table.updateItem(requestBuilder.build())
                .exceptionally(e -> {
                    if (e instanceof ConditionalCheckFailedException ccf) {
                        throw ConditionFailedException.fromSdk(ccf);
                    }
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new DynamoSimplifiedException(e);
                });
    }

    // ---- Partial update via low-level async client ----

    private CompletableFuture<T> executeWithExpression() {
        Map<String, AttributeValue> keyMap = buildKeyMap();

        Map<String, String> allNames = new HashMap<>(updateExpression.getExpressionNames());
        Map<String, AttributeValue> allValues = new HashMap<>(updateExpression.getExpressionValues());

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName(table.tableName())
                .key(keyMap)
                .updateExpression(updateExpression.getExpression())
                .expressionAttributeNames(allNames)
                .expressionAttributeValues(allValues)
                .returnValues(ReturnValue.ALL_NEW);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            allNames.putAll(conditionExpression.getExpressionNames());
            allValues.putAll(conditionExpression.getExpressionValues());
            requestBuilder
                    .conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(allNames)
                    .expressionAttributeValues(allValues);
        }

        return dynamoDbAsyncClient.updateItem(requestBuilder.build())
                .exceptionally(e -> {
                    if (e instanceof ConditionalCheckFailedException ccf) {
                        throw ConditionFailedException.fromSdk(ccf);
                    }
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new DynamoSimplifiedException(e);
                })
                .thenApply(response -> table.tableSchema().mapToItem(response.attributes()));
    }

    // ---- Key extraction from item ----

    private Map<String, AttributeValue> buildKeyMap() {
        Key key = table.keyFrom(item);
        return key.primaryKeyMap(table.tableSchema());
    }
}
