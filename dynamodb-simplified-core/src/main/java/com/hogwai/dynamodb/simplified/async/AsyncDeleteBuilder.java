package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
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
    private FilterExpression conditionExpression;

    AsyncDeleteBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                       @Nullable Object sortKey) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    /**
     * Configures a condition expression using a {@link FilterExpression} consumer.
     * The delete will only succeed if the condition evaluates to true.
     *
     * @param conditionBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> condition(@NonNull Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    /**
     * Configures a condition expression from a pre-built {@link FilterExpression}.
     * The delete will only succeed if the condition evaluates to true.
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncDeleteBuilder<T> condition(@Nullable FilterExpression condition) {
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
        this.conditionExpression = FilterExpression.builder().exists(attribute);
        return this;
    }

    /**
     * Executes the delete operation.
     *
     * @return a {@link CompletableFuture} that completes when the item has been deleted
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        DeleteItemEnhancedRequest.Builder requestBuilder =
                DeleteItemEnhancedRequest.builder().key(k -> {
                    k.partitionValue(toAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(toAttributeValue(sortKey));
                    }
                });

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    Expression.builder()
                              .expression(conditionExpression.getExpression())
                              .expressionNames(conditionExpression.getExpressionNames())
                              .expressionValues(conditionExpression.getExpressionValues())
                              .build()
            );
        }

        return table.deleteItem(requestBuilder.build())
                .<Void>thenApply(ignored -> null)
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

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
