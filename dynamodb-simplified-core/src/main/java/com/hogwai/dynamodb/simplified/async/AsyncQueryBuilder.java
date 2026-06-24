package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.PageCollector;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A fluent async builder for querying items in a DynamoDB table.
 * <p>
 * Mirrors the sync {@code QueryBuilder} but returns {@link CompletableFuture}
 * from all execution methods and uses the AWS SDK's async {@link PagePublisher}
 * under the hood.
 * <p>
 * Key conditions, filters, projections, sort order, pagination, and consistent
 * read settings are all supported. For index-specific queries, use
 * {@link AsyncIndex} instead.
 *
 * @param <T> the type of the item
 */
public class AsyncQueryBuilder<T> {
    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private QueryConditional keyCondition;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Boolean scanIndexForward = true;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;

    /**
     * Constructs a new {@code AsyncQueryBuilder} for the given async table.
     *
     * @param table the async DynamoDB table
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncTable<T> table) {
        this.table = table;
        this.index = null;
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for querying the given async secondary index.
     *
     * @param index the async DynamoDB secondary index
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncIndex<T> index) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKey(@NonNull Object pkValue) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyEquals(@NonNull Object pkValue, @NonNull Object skValue) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyBeginsWith(@NonNull Object pkValue, @NonNull String skPrefix) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyBetween(
            @NonNull Object pkValue, @NonNull Object skLow, @NonNull Object skHigh) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyGreaterThan(@NonNull Object pkValue, @NonNull Object skValue) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyGreaterThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyLessThan(@NonNull Object pkValue, @NonNull Object skValue) {
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
    public @NonNull AsyncQueryBuilder<T> partitionKeyAndSortKeyLessThanOrEqual(@NonNull Object pkValue, @NonNull Object skValue) {
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
    public @NonNull AsyncQueryBuilder<T> filter(@NonNull Consumer<FilterExpression> filterBuilder) {
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
    public @NonNull AsyncQueryBuilder<T> filter(@Nullable FilterExpression filter) {
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
    public @NonNull AsyncQueryBuilder<T> project(@NonNull String... attributes) {
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
    public @NonNull AsyncQueryBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
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
    public @NonNull AsyncQueryBuilder<T> descending() {
        this.scanIndexForward = false;
        return this;
    }

    /**
     * Sets the query to return results in ascending sort key order (the default).
     *
     * @return this builder for chaining
     */
    public @NonNull AsyncQueryBuilder<T> ascending() {
        this.scanIndexForward = true;
        return this;
    }

    /**
     * Limits the number of items evaluated per page.
     *
     * @param limit the maximum number of items to evaluate per page
     * @return this builder for chaining
     */
    public @NonNull AsyncQueryBuilder<T> limit(int limit) {
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
    public @NonNull AsyncQueryBuilder<T> startFrom(@Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.exclusiveStartKey = lastEvaluatedKey;
        return this;
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    public @NonNull AsyncQueryBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    // ============ Execution ============

    /**
     * Executes the query asynchronously and returns all matching items
     * aggregated from all pages.
     *
     * @return a {@link CompletableFuture} containing a list of matching items
     */
    public @NonNull CompletableFuture<List<T>> execute() {
        return executeAsPages()
                .thenApply(pages -> pages.stream()
                        .flatMap(page -> page.items().stream())
                        .toList());
    }

    /**
     * Executes the query asynchronously and returns only the first page of
     * results along with the last evaluated key for pagination.
     *
     * @return a {@link CompletableFuture} containing a {@link PagedResult}
     *         with the first page of items and the last evaluated key
     *         (may be {@code null} if no more pages)
     */
    public @NonNull CompletableFuture<PagedResult<T>> executeWithPagination() {
        return executeAsPages()
                .thenApply(pages -> {
                    Iterator<Page<T>> iter = pages.iterator();
                    if (iter.hasNext()) {
                        Page<T> first = iter.next();
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    return new PagedResult<>(Collections.emptyList(), null);
                });
    }

    /**
     * Executes the query asynchronously and returns the first matching item,
     * if any.
     *
     * @return a {@link CompletableFuture} containing an {@link Optional}
     *         with the first item, or empty if no items match
     */
    public @NonNull CompletableFuture<Optional<T>> executeAndGetFirst() {
        return execute().thenApply(items -> items.stream().findFirst());
    }

    /**
     * Returns the total number of matching items asynchronously by iterating
     * all query result pages.
     *
     * @return a {@link CompletableFuture} containing the total count of
     *         matching items
     */
    public @NonNull CompletableFuture<Long> count() {
        return executeAsPages()
                .thenApply(pages -> {
                    long total = 0;
                    for (Page<T> page : pages) {
                        total += page.count();
                    }
                    return total;
                });
    }

    // ============ Internal ============

    /**
     * Builds the request, executes it via the async table, and collects all
     * pages into a list through a {@link PagePublisher}.
     */
    private @NonNull CompletableFuture<List<Page<T>>> executeAsPages() {
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
                    projectionExpression.getProjectedAttributes().toArray(new String[0])
            );
        }

        if (limit != null) {
            requestBuilder.limit(limit);
        }

        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        if (index != null) {
            return PageCollector.collectPages(index.query(requestBuilder.build()));
        }
        return PageCollector.collectPages(table.query(requestBuilder.build()));
    }


}
