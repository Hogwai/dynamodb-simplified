package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * A fluent builder for querying items in a DynamoDB table or index.
 * Supports key conditions, filters, projections, sorting, pagination, and
 * global secondary index usage. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class QueryBuilder<T> {
    private final DynamoDbTable<T> table;
    private final DynamoDbIndex<T> index;
    private QueryConditional keyCondition;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Boolean scanIndexForward = true;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private String indexName;
    private Boolean consistentRead = false;

    /**
     * Constructs a new {@code QueryBuilder} for the given table.
     *
     * @param table the DynamoDB table
     */
    public QueryBuilder(@NonNull DynamoDbTable<T> table) {
        this.table = table;
        this.index = null;
    }

    /**
     * Constructs a new {@code QueryBuilder} for querying the given secondary index.
     *
     * @param index the DynamoDB secondary index
     */
    public QueryBuilder(@NonNull DynamoDbIndex<T> index) {
        this.table = null;
        this.index = index;
    }

    // ============ Key Conditions ============

    /**
     * Sets the key condition to match all items with the given partition key value.
     *
     * @param pkValue the partition key value
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKey(@NonNull Object pkValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue)).build()
        );
        return this;
    }

    /**
     * Sets the key condition to match the item with the exact partition key
     * and sort key values.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key value
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyEquals(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.keyEqualTo(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key begins with the
     * given prefix within the specified partition.
     *
     * @param pkValue  the partition key value
     * @param skPrefix the sort key prefix string
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyBeginsWith(@NonNull Object pkValue, @NonNull String skPrefix) {
        this.keyCondition = QueryConditional.sortBeginsWith(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(skPrefix)
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key falls within
     * the specified range (inclusive) within the given partition.
     *
     * @param pkValue the partition key value
     * @param skLow   the lower bound of the sort key range (inclusive)
     * @param skHigh  the upper bound of the sort key range (inclusive)
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyBetween(@NonNull Object pkValue, @NonNull Object skLow, @NonNull Object skHigh) {
        this.keyCondition = QueryConditional.sortBetween(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skLow))
                   .build(),
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skHigh))
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key is strictly
     * greater than the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (exclusive)
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyGreaterThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThan(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key is greater than
     * or equal to the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (inclusive)
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyGreaterThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key is strictly
     * less than the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (exclusive)
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyLessThan(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortLessThan(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    /**
     * Sets the key condition to match items whose sort key is less than
     * or equal to the given value within the specified partition.
     *
     * @param pkValue the partition key value
     * @param skValue the sort key threshold (inclusive)
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> partitionKeyAndSortKeyLessThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
        this.keyCondition = QueryConditional.sortLessThanOrEqualTo(
                Key.builder()
                   .partitionValue(AttributeValueConverter.toKeyAttributeValue(pkValue))
                   .sortValue(AttributeValueConverter.toKeyAttributeValue(skValue))
                   .build()
        );
        return this;
    }

    // ============ Filter ============

    /**
     * Configures a post-query filter expression using a {@link FilterExpression} consumer.
     *
     * @param filterBuilder a consumer that configures the {@link FilterExpression}
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
        this.filterExpression = FilterExpression.builder();
        filterBuilder.accept(this.filterExpression);
        return this;
    }

    /**
     * Configures a post-query filter expression from a pre-built {@link FilterExpression}.
     *
     * @param filter the filter expression
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> filter(@Nullable FilterExpression filter) {
        this.filterExpression = filter;
        return this;
    }

    // ============ Projection ============

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    // ============ Options ============

    /**
     * Sets the query to return results in descending sort key order.
     *
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> descending() {
        this.scanIndexForward = false;
        return this;
    }

    /**
     * Sets the query to return results in ascending sort key order (the default).
     *
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> ascending() {
        this.scanIndexForward = true;
        return this;
    }

    /**
     * Limits the number of items evaluated per page.
     *
     * @param limit the maximum number of items to evaluate per page
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets the exclusive start key for paginated queries.
     * Typically obtained from the {@link PagedResult#getLastEvaluatedKey()} of a previous query.
     *
     * @param lastEvaluatedKey the key map from which to start the next page
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    /**
     * Specifies a global secondary index to query against instead of the table.
     * <p>
     * <b>Deprecated:</b> Use {@code table.index("name").query()} instead,
     * which returns a cleaner per-index builder.
     *
     * @param indexName the name of the global secondary index
     * @return this builder for chaining
     * @deprecated since 1.0, for removal — use {@link com.hogwai.dynamodb.simplified.builder.Index#query()}
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public @NonNull QueryBuilder<T> useIndex(@NonNull String indexName) {
        this.indexName = indexName;
        return this;
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    public @NonNull QueryBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    // ============ Execution ============

    /**
     * Executes the query and returns all matching items aggregated from all pages.
     *
     * @return a list of matching items
     */
    public @NonNull List<T> execute() {
        return executeAsPages().stream()
                               .flatMap(page -> page.items().stream())
                               .toList();
    }

    /**
     * Executes the query and returns only the first page of results along with
     * the last evaluated key for pagination.
     *
     * @return a {@link PagedResult} containing the first page of items and
     *         the last evaluated key (may be {@code null} if no more pages)
     */
    public @NonNull PagedResult<T> executeWithPagination() {
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

    /**
     * Executes the query and returns the first matching item, if any.
     *
     * @return an {@link Optional} containing the first item, or empty if no items match
     */
    public @NonNull Optional<T> executeAndGetFirst() {
        return execute().stream().findFirst();
    }

    /**
     * Returns the total number of matching items by iterating all query result pages.
     * <p>
     * Note: currently fetches items server-side but discards them. A future optimization
     * could use the low-level {@code QueryRequest} with {@code Select: COUNT} to avoid
     * transferring item data entirely.
     *
     * @return the total count of matching items
     */
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
                    filterExpression.toSdkExpression()
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

        if (index != null) {
            return index.query(requestBuilder.build());
        }

        if (indexName != null) {
            return table.index(indexName).query(requestBuilder.build());
        }

        return table.query(requestBuilder.build());
    }


}
