package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class DeleteBuilder<T> {
    private final DynamoDbTable<T> table;
    private final Object partitionKey;
    private final Object sortKey;
    private ConditionExpression conditionExpression;

    /**
     * Constructs a new {@code DeleteBuilder} for the given table and key.
     *
     * @param table        the DynamoDB table
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value (may be {@code null} if the table has no sort key)
     */
    public DeleteBuilder(@NonNull DynamoDbTable<T> table, @NonNull Object partitionKey, @Nullable Object sortKey) {
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
    public @NonNull DeleteBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
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
    public @NonNull DeleteBuilder<T> condition(@Nullable ConditionExpression condition) {
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
    public @NonNull DeleteBuilder<T> onlyIfExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().exists(attribute).build();
        return this;
    }

    /**
     * Executes the delete operation and returns the deleted item.
     *
     * @return the deleted item, or {@code null} if no item matched the key
     */
    public @Nullable T execute() {
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

        try {
            return table.deleteItem(requestBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        }
    }


}
