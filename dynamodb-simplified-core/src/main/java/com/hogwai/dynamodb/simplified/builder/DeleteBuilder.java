package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
    private FilterExpression conditionExpression;

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
     * Configures a condition expression using a {@link FilterExpression} consumer.
     * The delete will only succeed if the condition evaluates to true.
     *
     * @param conditionBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    public @NonNull DeleteBuilder<T> condition(@NonNull Consumer<FilterExpression> conditionBuilder) {
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
    public @NonNull DeleteBuilder<T> condition(@Nullable FilterExpression condition) {
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
        this.conditionExpression = FilterExpression.builder().exists(attribute);
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

        return table.deleteItem(requestBuilder.build());
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
