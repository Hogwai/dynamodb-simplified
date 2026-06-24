package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncPutBuilder<T> {
    private final DynamoDbAsyncTable<T> table;
    private final T item;
    private FilterExpression conditionExpression;

    AsyncPutBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull T item) {
        this.table = table;
        this.item = item;
    }

    /**
     * Configures a condition expression using a {@link FilterExpression} consumer.
     * The put will only succeed if the condition evaluates to true.
     *
     * @param conditionBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> condition(@NonNull Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    /**
     * Configures a condition expression from a pre-built {@link FilterExpression}.
     * The put will only succeed if the condition evaluates to true.
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    @NonNull
    public AsyncPutBuilder<T> condition(@Nullable FilterExpression condition) {
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
        this.conditionExpression = FilterExpression.builder().notExists(attribute);
        return this;
    }

    /**
     * Executes the put operation.
     *
     * @return a {@link CompletableFuture} that completes when the item has been put
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        PutItemEnhancedRequest.Builder<T> requestBuilder = PutItemEnhancedRequest
                .builder((Class<T>) item.getClass())
                .item(item);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    Expression.builder()
                              .expression(conditionExpression.getExpression())
                              .expressionNames(conditionExpression.getExpressionNames())
                              .expressionValues(conditionExpression.getExpressionValues())
                              .build()
            );
        }

        return table.putItem(requestBuilder.build())
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
