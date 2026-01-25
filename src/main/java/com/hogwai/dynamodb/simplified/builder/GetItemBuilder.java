package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Optional;
import java.util.function.Consumer;

public class GetItemBuilder<T> {
    private final DynamoDbTable<T> table;
    private final Object partitionKey;
    private final Object sortKey;
    private ProjectionExpression projectionExpression;
    private boolean consistentRead = false;

    public GetItemBuilder(DynamoDbTable<T> table, Object partitionKey, Object sortKey) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    public GetItemBuilder<T> project(String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    public GetItemBuilder<T> project(Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    public GetItemBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    public Optional<T> execute() {
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            return executeWithProjection();
        }
        return executeSimple();
    }

    private Optional<T> executeSimple() {
        Key.Builder keyBuilder = Key.builder().partitionValue(toAttributeValue(partitionKey));
        if (sortKey != null) {
            keyBuilder.sortValue(toAttributeValue(sortKey));
        }

        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                                                               .key(keyBuilder.build())
                                                               .consistentRead(consistentRead)
                                                               .build();

        return Optional.ofNullable(table.getItem(request));
    }

    private Optional<T> executeWithProjection() {
        Key.Builder keyBuilder = Key.builder().partitionValue(toAttributeValue(partitionKey));
        if (sortKey != null) {
            keyBuilder.sortValue(toAttributeValue(sortKey));
        }
        Key key = keyBuilder.build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                           .queryConditional(QueryConditional.keyEqualTo(key))
                                                           .consistentRead(consistentRead)
                                                           .limit(1)
                                                           .attributesToProject(projectionExpression.getExpressionNames().values().toArray(new String[0]))
                                                           .build();

        return table.query(request)
                    .items()
                    .stream()
                    .findFirst();
    }

    private AttributeValue toAttributeValue(Object value) {
        if (value instanceof String stringValue) {
            return AttributeValue.builder().s(stringValue).build();
        }
        if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        }
        throw new IllegalArgumentException("Unsupported key type: %s".formatted(value.getClass()));
    }
}