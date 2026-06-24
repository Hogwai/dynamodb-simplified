package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A fluent builder for scanning items in a DynamoDB secondary index (GSI or LSI).
 * <p>
 * Obtained via {@link Index#scan()}. Supports filter, projection, and pagination
 * scoped to the index.
 *
 * @param <T> the type of the item
 */
public class IndexScanBuilder<T> {
    private final DynamoDbIndex<T> index;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;

    IndexScanBuilder(@NonNull DynamoDbIndex<T> index) {
        this.index = index;
    }

    // ============ Filter ============

    @NonNull
    public IndexScanBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    @NonNull
    public IndexScanBuilder<T> filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    @NonNull
    public IndexScanBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    @NonNull
    public IndexScanBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Options ============

    @NonNull
    public IndexScanBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    @NonNull
    public IndexScanBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    @NonNull
    public IndexScanBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    // ============ Execution ============

    @NonNull
    public List<T> execute() {
        return executeAsPages().stream()
                               .flatMap(page -> page.items().stream())
                               .toList();
    }

    @NonNull
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

    public long count() {
        long total = 0;
        for (Page<T> page : executeAsPages()) {
            total += page.count();
        }
        return total;
    }

    private SdkIterable<Page<T>> executeAsPages() {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                                                                        .consistentRead(consistentRead);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(
                    Expression.builder()
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

        return index.scan(requestBuilder.build());
    }
}
