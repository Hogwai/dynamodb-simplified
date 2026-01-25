package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.function.Consumer;

public class DeleteBuilder<T> {
    private final DynamoDbTable<T> table;
    private final Object partitionKey;
    private final Object sortKey;
    private FilterExpression conditionExpression;

    public DeleteBuilder(DynamoDbTable<T> table, Object partitionKey, Object sortKey) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    public DeleteBuilder<T> condition(Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    public DeleteBuilder<T> onlyIfExists(String attribute) {
        this.conditionExpression = FilterExpression.builder().exists(attribute);
        return this;
    }

    public T execute() {
        Key.Builder keyBuilder = Key.builder().partitionValue(toAttributeValue(partitionKey));
        if (sortKey != null) {
            keyBuilder.sortValue(toAttributeValue(sortKey));
        }

        DeleteItemEnhancedRequest.Builder requestBuilder = DeleteItemEnhancedRequest.builder()
                                                                                    .key(k -> keyBuilder.build());

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

    private AttributeValue toAttributeValue(Object value) {
        if (value instanceof String stringValue) {
            return AttributeValue.builder().s(stringValue).build();
        }
        if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        }
        throw new IllegalArgumentException("Unsupported key type: " + value.getClass());
    }
}
