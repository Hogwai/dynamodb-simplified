package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncDeleteBuilder<T> {
    private static final Logger LOG = Logging.getLogger(AsyncDeleteBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final Object partitionKey;
    @Nullable
    private final Object sortKey;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private ConditionExpression conditionExpression;
    private ReturnValue returnValues;

    AsyncDeleteBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                       @Nullable Object sortKey, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return this;
    }

    /**
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return this;
    }

    /**
     * Adds a condition that the specified attribute must exist on the item
     * for the deletion to succeed.
     *
     * @param attribute the attribute name to check for existence
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> onlyIfExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().exists(attribute).build();
        return this;
    }

    /**
     * Configures the return values for the delete operation.
     * When set (e.g., {@link ReturnValue#ALL_OLD}), the operation falls back to the
     * low-level {@link DynamoDbAsyncClient#deleteItem(DeleteItemRequest)} to include the
     * return values in the request.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return this;
    }

    /**
     * Executes the delete operation and returns the deleted item.
     *
     * @return a {@link CompletableFuture} containing the deleted item, or empty
     */
    @NonNull
    public CompletableFuture<Optional<T>> execute() {
        long start = System.nanoTime();
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncDelete on table '{}' completed in {}ms (with return values)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return Optional.ofNullable(result);
                    });
        }

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

        return table.deleteItem(requestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("DeleteItem", table.tableName()))
                .thenApply(result -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncDelete on table '{}' completed in {}ms",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return Optional.ofNullable(result);
                });
    }

    // ---- Low-level path for return values ----

    private CompletableFuture<T> executeWithReturnValues() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put(pkName, AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (skName != null && sortKey != null) {
            key.put(skName, AttributeValueConverter.toKeyAttributeValue(sortKey));
        }

        DeleteItemRequest.Builder requestBuilder = DeleteItemRequest.builder()
                .tableName(table.tableName())
                .key(key)
                .returnValues(returnValues);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(conditionExpression.getExpressionNames())
                    .expressionAttributeValues(conditionExpression.getExpressionValues());
        }

        return dynamoDbAsyncClient.deleteItem(requestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("DeleteItem", table.tableName()))
                .thenApply(response -> {
                    if (response.attributes() == null || response.attributes().isEmpty()) {
                        return null;
                    }
                    return table.tableSchema().mapToItem(response.attributes());
                });
    }


}
