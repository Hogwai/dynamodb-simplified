package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ScanBuilder<T> {
    private final DynamoDbTable<T> table;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Integer totalSegments;
    private Integer segment;
    private Boolean consistentRead = false;

    public ScanBuilder(DynamoDbTable<T> table) {
        this.table = table;
    }

    // ============ Filter ============

    public ScanBuilder<T> filter(Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    public ScanBuilder<T> filter(FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    public ScanBuilder<T> project(String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    public ScanBuilder<T> project(Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Parallel Scan ============

    public ScanBuilder<T> parallelScan(int totalSegments, int segment) {
        this.totalSegments = totalSegments;
        this.segment = segment;
        return this;
    }

    // ============ Options ============

    public ScanBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public ScanBuilder<T> startFrom(Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    public ScanBuilder<T> consistentRead(boolean consistentRead) {
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

    private PageIterable<T> executeAsPages() {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                                                                        .consistentRead(consistentRead);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(
                    software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                                                                       .expression(filterExpression.getExpression())
                                                                       .expressionNames(filterExpression.getExpressionNames())
                                                                       .expressionValues(filterExpression.getExpressionValues())
                                                                       .build()
            );
        }

        if (projectionExpression != null && !projectionExpression.isEmpty()) {
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

        if (totalSegments != null && segment != null) {
            requestBuilder.segment(segment);
            requestBuilder.totalSegments(totalSegments);
        }

        return table.scan(requestBuilder.build());
    }
}
