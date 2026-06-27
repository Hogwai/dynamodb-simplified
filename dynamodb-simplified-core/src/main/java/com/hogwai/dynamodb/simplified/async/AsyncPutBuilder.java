package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncPutBuilder<T> {
    private static final Logger LOG = Logging.getLogger(AsyncPutBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final T item;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private ConditionExpression conditionExpression;
    private ReturnValue returnValues;

    AsyncPutBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull T item,
                    @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return this;
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return this;
    }

    /**
     * Adds a condition that the specified attribute must not already exist
     * on an item with the same key for the put to succeed.
     *
     * @param attribute the attribute name to check for absence
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> onlyIfNotExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().notExists(attribute).build();
        return this;
    }

    /**
     * Configures which item attributes to return after the put.
     * <p>
     * When set, uses the low-level {@code PutItemRequest} with the specified
     * {@code ReturnValues}. Common values: {@link ReturnValue#ALL_OLD}
     * (returns the previous item if one existed), {@link ReturnValue#NONE}.
     * <p>
     * To retrieve the previous item when using {@code ReturnValue#ALL_OLD},
     * use {@link #executeReturning()} instead of {@link #execute()}.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return this;
    }

    /**
     * Executes the put operation.
     *
     * @return a {@link CompletableFuture} that completes when the item has been put
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        long start = System.nanoTime();
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(ignored -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncPut on table '{}' completed in {}ms (with return values)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return null;
                    });
        }
        @SuppressWarnings("unchecked")
        PutItemEnhancedRequest.Builder<T> requestBuilder = PutItemEnhancedRequest
                .builder((Class<T>) item.getClass())
                .item(item);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        return table.putItem(requestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("PutItem", table.tableName()))
                .thenRun(() -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncPut on table '{}' completed in {}ms",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                });
    }

    /**
     * Executes the put operation and returns the previous item if
     * {@link #returnValues(ReturnValue)} was set to {@code ALL_OLD}.
     *
     * @return a CompletableFuture containing the previous item, or empty
     */
    @NonNull
    public CompletableFuture<Optional<T>> executeReturning() {
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(Optional::ofNullable);
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    // ---- Low-level put with return values ----

    private CompletableFuture<T> executeWithReturnValues() {
        Map<String, AttributeValue> itemMap = table.tableSchema().itemToMap(item, false);

        PutItemRequest.Builder requestBuilder = PutItemRequest.builder()
                .tableName(table.tableName())
                .item(itemMap)
                .returnValues(returnValues);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder
                    .conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(conditionExpression.getExpressionNames())
                    .expressionAttributeValues(conditionExpression.getExpressionValues());
        }

        return dynamoDbAsyncClient.putItem(requestBuilder.build())
                .thenApply(response -> {
                    if (response.attributes() == null || response.attributes().isEmpty()) {
                        return null;
                    }
                    return table.tableSchema().mapToItem(response.attributes());
                })
                .exceptionally(AsyncExceptionMapper.handler("PutItem", table.tableName()));
    }
}
