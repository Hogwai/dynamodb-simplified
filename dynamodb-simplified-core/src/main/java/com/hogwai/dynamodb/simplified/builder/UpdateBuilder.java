package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A fluent builder for updating an item in a DynamoDB table.
 * Supports both full-item replacement and partial updates via an
 * {@link UpdateExpression}. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class UpdateBuilder<T> {
    private final DynamoDbTable<T> table;
    private final T item;
    private final DynamoDbClient dynamoDbClient;
    private UpdateExpression updateExpression;
    private ConditionExpression conditionExpression;
    private boolean ignoreNulls = true;

    /**
     * Constructs a new {@code UpdateBuilder} for the given table and item.
     *
     * @param table           the DynamoDB table
     * @param item            the item to update (full replacement data, or
     *                        a template object when used with {@link #update(Consumer)})
     * @param dynamoDbClient  the low-level DynamoDB client (required for partial updates)
     */
    public UpdateBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item, @NonNull DynamoDbClient dynamoDbClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbClient = dynamoDbClient;
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
     * Executes the update operation. If a partial update expression was configured
     * via {@link #update(Consumer)}, a targeted partial update is performed via the
     * low-level DynamoDB client. Otherwise, the full item is replaced.
     *
     * @return the updated item
     */
    public @NonNull T execute() {
        if (!ignoreNulls && updateExpression != null && !updateExpression.isEmpty()) {
            throw new IllegalStateException(
                "ignoreNulls(false) has no effect when using partial updates via update(Consumer). "
                + "Remove the ignoreNulls() call or use a full-item update instead.");
        }
        if (updateExpression != null && !updateExpression.isEmpty()) {
            return executeWithExpression();
        }
        return executeWithFullItem();
    }

    // ---- Full item replacement (existing behavior) ----

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
        }
    }

    // ---- Partial update via low-level client ----

    private T executeWithExpression() {
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

        Map<String, AttributeValue> result;
        try {
            result = dynamoDbClient.updateItem(requestBuilder.build()).attributes();
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        }

        return table.tableSchema().mapToItem(result);
    }

    // ---- Key extraction from item ----

    private Map<String, AttributeValue> buildKeyMap() {
        Key key = table.keyFrom(item);
        return key.primaryKeyMap(table.tableSchema());
    }

}
