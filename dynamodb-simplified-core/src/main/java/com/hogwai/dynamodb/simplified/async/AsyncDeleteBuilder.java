package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncDeleteBuilder<T> {
    private final DynamoDbAsyncTable<T> table;
    private final Object partitionKey;
    @Nullable
    private final Object sortKey;
    private ConditionExpression conditionExpression;

    AsyncDeleteBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                       @Nullable Object sortKey) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
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
     * Executes the delete operation and returns the deleted item.
     *
     * @return a {@link CompletableFuture} containing the deleted item
     */
    @NonNull
    public CompletableFuture<T> execute() {
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


}
