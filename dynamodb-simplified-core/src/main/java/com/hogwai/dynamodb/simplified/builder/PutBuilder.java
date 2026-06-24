package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class PutBuilder<T> {
    private final DynamoDbTable<T> table;
    private final T item;
    private ConditionExpression conditionExpression;

    /**
     * Constructs a new {@code PutBuilder} for the given table and item.
     *
     * @param table the DynamoDB table
     * @param item  the item to put
     */
    public PutBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item) {
        this.table = table;
        this.item = item;
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
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
    public @NonNull PutBuilder<T> condition(@Nullable ConditionExpression condition) {
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
    public @NonNull PutBuilder<T> onlyIfNotExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().notExists(attribute).build();
        return this;
    }

    /**
     * Executes the put operation.
     */
    public void execute() {
        @SuppressWarnings("unchecked")
        PutItemEnhancedRequest.Builder<T> requestBuilder = PutItemEnhancedRequest
                .builder((Class<T>) item.getClass())
                .item(item);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        try {
            table.putItem(requestBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        }
    }
}
