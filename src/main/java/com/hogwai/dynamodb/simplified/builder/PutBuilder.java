package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;

import java.util.function.Consumer;

public class PutBuilder<T> {
    private final DynamoDbTable<T> table;
    private final T item;
    private FilterExpression conditionExpression;

    public PutBuilder(DynamoDbTable<T> table, T item) {
        this.table = table;
        this.item = item;
    }

    public PutBuilder<T> condition(Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    public PutBuilder<T> onlyIfNotExists(String attribute) {
        this.conditionExpression = FilterExpression.builder().notExists(attribute);
        return this;
    }

    public void execute() {
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

        table.putItem(requestBuilder.build());
    }
}
