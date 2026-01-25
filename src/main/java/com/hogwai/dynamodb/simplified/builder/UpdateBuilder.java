package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class UpdateBuilder<T> {
    private final DynamoDbTable<T> table;
    private final T item;
    private UpdateExpression updateExpression;
    private FilterExpression conditionExpression;
    private boolean ignoreNulls = true;

    public UpdateBuilder(DynamoDbTable<T> table, T item) {
        this.table = table;
        this.item = item;
    }

    public UpdateBuilder<T> update(Consumer<UpdateExpression> updateBuilder) {
        this.updateExpression = UpdateExpression.builder();
        updateBuilder.accept(this.updateExpression);
        return this;
    }

    public UpdateBuilder<T> condition(Consumer<FilterExpression> conditionBuilder) {
        this.conditionExpression = FilterExpression.builder();
        conditionBuilder.accept(this.conditionExpression);
        return this;
    }

    public UpdateBuilder<T> ignoreNulls(boolean ignoreNulls) {
        this.ignoreNulls = ignoreNulls;
        return this;
    }

    public T execute() {
        UpdateItemEnhancedRequest.Builder<T> requestBuilder =
                UpdateItemEnhancedRequest.builder((Class<T>) item.getClass())
                                         .item(item)
                                         .ignoreNulls(ignoreNulls);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    Expression.builder()
                              .expression(conditionExpression.getExpression())
                              .expressionNames(conditionExpression.getExpressionNames())
                              .expressionValues(conditionExpression.getExpressionValues())
                              .build()
            );
        }

        return table.updateItem(requestBuilder.build());
    }
}
