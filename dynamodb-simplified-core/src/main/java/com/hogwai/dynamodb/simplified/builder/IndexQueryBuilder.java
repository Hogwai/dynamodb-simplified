package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * A fluent builder for querying items in a DynamoDB secondary index (GSI or LSI).
 * <p>
 * Obtained via {@link Index#query()}. Supports key conditions, filters, projections,
 * sorting, and pagination scoped to the index.
 *
 * @param <T> the type of the item
 */
public class IndexQueryBuilder<T> {
    private final DynamoDbIndex<T> index;
    private QueryConditional keyCondition;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Boolean scanIndexForward = true;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;

    IndexQueryBuilder(@NonNull DynamoDbIndex<T> index) {
        this.index = index;
    }

    // ============ Key Conditions ============

    @NonNull
    public IndexQueryBuilder<T> partitionKey(@NonNull Object pkValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(toAttributeValue(pkValue)).build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyEquals(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyBeginsWith(@NonNull Object pkValue, @NonNull String skPrefix) {
        this.keyCondition = QueryConditional.sortBeginsWith(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(skPrefix)
                   .build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyBetween(@NonNull Object pkValue, @NonNull Object skLow, @NonNull Object skHigh) {
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

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyGreaterThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThan(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyGreaterThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyLessThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortLessThan(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> partitionKeyAndSortKeyLessThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortLessThanOrEqualTo(
                Key.builder()
                   .partitionValue(toAttributeValue(pkValue))
                   .sortValue(toAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    // ============ Filter ============

    @NonNull
    public IndexQueryBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    @NonNull
    public IndexQueryBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Options ============

    @NonNull
    public IndexQueryBuilder<T> descending() {
        this.scanIndexForward = false;
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> ascending() {
        this.scanIndexForward = true;
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    @NonNull
    public IndexQueryBuilder<T> consistentRead(boolean consistentRead) {
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

    @NonNull
    public Optional<T> executeAndGetFirst() {
        return execute().stream().findFirst();
    }

    public long count() {
        long total = 0;
        for (Page<T> page : executeAsPages()) {
            total += page.count();
        }
        return total;
    }

    // ============ Internal ============

    private SdkIterable<Page<T>> executeAsPages() {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                                                                         .queryConditional(keyCondition)
                                                                         .scanIndexForward(scanIndexForward)
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

        return index.query(requestBuilder.build());
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
