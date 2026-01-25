package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class QueryBuilder<T> {
    private final DynamoDbTable<T> table;
    private QueryConditional keyCondition;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Boolean scanIndexForward = true;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private String indexName;
    private Boolean consistentRead = false;

    public QueryBuilder(DynamoDbTable<T> table) {
        this.table = table;
    }

    // ============ Key Conditions ============

    public QueryBuilder<T> partitionKey(Object pkValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(toAttributeValue(pkValue)).build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyEquals(Object pkValue, Object skValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyBeginsWith(Object pkValue, String skPrefix) {
        this.keyCondition = QueryConditional.sortBeginsWith(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(skPrefix)
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyBetween(Object pkValue, Object skLow, Object skHigh) {
        this.keyCondition = QueryConditional.sortBetween(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skLow))
                   .build(),
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skHigh))
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyGreaterThan(Object pkValue, Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThan(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyGreaterThanOrEqual(Object pkValue, Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyLessThan(Object pkValue, Object skValue) {
        this.keyCondition = QueryConditional.sortLessThan(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    public QueryBuilder<T> partitionKeyAndSortKeyLessThanOrEqual(Object pkValue, Object skValue) {
        this.keyCondition = QueryConditional.sortLessThanOrEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    // ============ Filter ============

    public QueryBuilder<T> filter(Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    public QueryBuilder<T> filter(FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    public QueryBuilder<T> project(String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    public QueryBuilder<T> project(Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Options ============

    public QueryBuilder<T> descending() {
        this.scanIndexForward = false;
        return this;
    }

    public QueryBuilder<T> ascending() {
        this.scanIndexForward = true;
        return this;
    }

    public QueryBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public QueryBuilder<T> startFrom(Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    public QueryBuilder<T> useIndex(String indexName) {
        this.indexName = indexName;
        return this;
    }

    public QueryBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    // ============ Execution ============

    public List<T> execute() {
        return executeAsPages().stream()
                               .flatMap(page -> page.items().stream())
                               .toList();
    }

    public PagedResult<T> executeWithPagination() {
        Iterator<Page<T>> pages = executeAsPages().iterator();
        if (pages.hasNext()) {
            Page<T> firstPage = pages.next();
            return new PagedResult<>(
                    firstPage.items(),
                    firstPage.lastEvaluatedKey()
            );
        }
        return new PagedResult<>(Collections.emptyList(), null);
    }

    public Optional<T> executeAndGetFirst() {
        return execute().stream().findFirst();
    }

    public long count() {
        // Pour un count efficace, on utilise la requête avec select COUNT
        return execute().size(); // Simplifié - voir note ci-dessous
    }

    private PageIterable<T> executeAsPages() {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                                                                          .queryConditional(keyCondition)
                                                                          .scanIndexForward(scanIndexForward)
                                                                          .consistentRead(consistentRead);

        // Merge expression names from filter and projection
        Map<String, String> allNames = new HashMap<>();
        Map<String, AttributeValue> allValues = new HashMap<>();

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(
                    software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                                                                       .expression(filterExpression.getExpression())
                                                                       .expressionNames(filterExpression.getExpressionNames())
                                                                       .expressionValues(filterExpression.getExpressionValues())
                                                                       .build()
            );
            allNames.putAll(filterExpression.getExpressionNames());
            allValues.putAll(filterExpression.getExpressionValues());
        }

        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            allNames.putAll(projectionExpression.getExpressionNames());

            // Build attribute names to project
            requestBuilder.attributesToProject(
                    projectionExpression.getExpressionNames().values().toArray(new String[0])
            );
        }

        if (limit != null) {
            requestBuilder.limit(limit);
        }

        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        if (indexName != null) {
            return (PageIterable<T>) table.index(indexName).query(requestBuilder.build());
        }

        return table.query(requestBuilder.build());
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