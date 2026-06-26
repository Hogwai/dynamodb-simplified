package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.internal.PageCollector;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.Collections;
import java.util.HashMap;
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
    private static final Logger LOG = Logging.getLogger(AsyncQueryBuilder.class);
    private static final String CONDITION_JOINER = " AND ";

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncIndex<T> index;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private QueryConditional keyCondition;
    private FilterExpression filterExpression;
    private ProjectionExpression projectionExpression;
    private Boolean scanIndexForward = true;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    private Boolean consistentRead = false;
    private ReturnConsumedCapacity returnConsumedCapacity;
    private Select select;

    /**
     * Describes the type of sort key comparison for a key condition expression.
     */
    enum KeyConditionOp {
        EQ, BEGINS_WITH, BETWEEN, GT, GE, LT, LE
    }
    private KeyConditionOp keyOp;
    private @Nullable Object pkValue;
    private @Nullable Object skValue;
    private @Nullable Object skValue2; // for BETWEEN only

    /**
     * Constructs a new {@code AsyncQueryBuilder} for the given async table.
     *
     * @param table the async DynamoDB table
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncTable<T> table) {
        this(table, null);
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for querying the given async secondary index.
     *
     * @param index the async DynamoDB secondary index
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncIndex<T> index) {
        this(index, null);
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for the given async table with a low-level client.
     *
     * @param table                the async DynamoDB table
     * @param dynamoDbAsyncClient  the low-level async DynamoDB client (nullable)
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncTable<T> table, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.index = null;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Constructs a new {@code AsyncQueryBuilder} for querying the given async secondary index
     * with a low-level client.
     *
     * @param index                the async DynamoDB secondary index
     * @param dynamoDbAsyncClient  the low-level async DynamoDB client (nullable)
     */
    public AsyncQueryBuilder(@NonNull DynamoDbAsyncIndex<T> index, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = null;
        this.index = index;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    // ============ Key Conditions ============

    /**
     * Sets the key condition to match all items with the given partition key value.
     *
     * @param pkValue the partition key value
     * @return this builder for chaining
     */
    @SuppressWarnings("PMD.NullAssignment")
    public @NonNull AsyncQueryBuilder<T> partitionKey(@NonNull Object pkValue) {
        this.pkValue = pkValue;
        this.keyOp = KeyConditionOp.EQ;
        this.skValue = null;
        this.skValue2 = null;
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
        this.partitionKey(pkValue);
        this.skValue = skValue;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.BEGINS_WITH;
        this.skValue = skPrefix;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.BETWEEN;
        this.skValue = skLow;
        this.skValue2 = skHigh;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.GT;
        this.skValue = skValue;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.GE;
        this.skValue = skValue;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.LT;
        this.skValue = skValue;
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
        this.partitionKey(pkValue);
        this.keyOp = KeyConditionOp.LE;
        this.skValue = skValue;
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
     * Typically obtained from the {@link PagedResult#lastEvaluatedKey()} of a previous query.
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

    /**
     * Sets the return consumed capacity option for the query.
     *
     * @param returnConsumedCapacity the return consumed capacity value
     * @return this builder for chaining
     */
    public @NonNull AsyncQueryBuilder<T> returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Sets the select parameter for the query, controlling which attributes are returned.
     * Use {@link Select#COUNT} to request only the item count from the server.
     *
     * @param select the select parameter (nullable)
     * @return this builder for chaining
     */
    public @NonNull AsyncQueryBuilder<T> select(@Nullable Select select) {
        this.select = select;
        return this;
    }

    // ============ Execution ============

    /**
     * Executes the query asynchronously and returns all matching items
     * aggregated from all pages.
     * <p>
     * <b>Memory warning:</b> This method eagerly loads <em>all</em> matching
     * items into memory. If the result set may be large, consider using
     * {@link #executeAsPages()} or paginated methods such as
     * {@link #executeWithPagination()} to process items incrementally.
     *
     * @return a {@link CompletableFuture} containing a list of matching items
     */
    @NonNull
    public CompletableFuture<List<T>> executeAll() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeAll() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    List<T> results = pages.stream()
                            .flatMap(page -> page.items().stream())
                            .toList();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery on table '{}' returned {} items in {}ms",
                                getTableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return results;
                });
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
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeWithPagination() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeAsPages()
                .thenApply(pages -> {
                    Iterator<Page<T>> iter = pages.iterator();
                    if (iter.hasNext()) {
                        Page<T> first = iter.next();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncQuery on table '{}' returned {} items in {}ms (first page)",
                                    getTableName(), first.items().size(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return new PagedResult<>(first.items(), first.lastEvaluatedKey());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery on table '{}' returned 0 items in {}ms (first page)",
                                getTableName(), (System.nanoTime() - start) / 1_000_000);
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
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeAndGetFirst() with Select.COUNT. Use count() instead."));
        }
        long start = System.nanoTime();
        return executeWithPagination().thenApply(firstPage -> {
            Optional<T> result = firstPage.items().stream().findFirst();
            if (LOG.isDebugEnabled()) {
                LOG.debug("AsyncQuery on table '{}' returned first item in {}ms",
                        getTableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        });
    }

    /**
     * Returns a reactive publisher that lazily emits items as pages arrive.
     * <p>
     * Unlike {@link #executeAll()} which loads all pages into memory, this method
     * streams items one-by-one as each page is received from DynamoDB.
     *
     * @return a {@link CompletableFuture} containing an {@link SdkPublisher}
     *         that emits query items
     * @throws IllegalStateException if called with Select.COUNT
     */
    public @NonNull CompletableFuture<SdkPublisher<T>> executeStream() {
        if (select == Select.COUNT) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot call executeStream() with Select.COUNT. Use count() instead."));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncQuery stream on table '{}'", getTableName());
        }
        return CompletableFuture.completedFuture(
                buildPagePublisher().flatMapIterable(Page::items));
    }

    /**
     * Returns the total number of matching items asynchronously by iterating
     * all query result pages.
     * <p>
     * When a low-level {@link DynamoDbAsyncClient} is available, uses
     * {@code Select.COUNT} server-side to avoid transferring item data.
     * Falls back to the enhanced client page iteration otherwise.
     *
     * @return a {@link CompletableFuture} containing the total count of
     *         matching items
     */
    public @NonNull CompletableFuture<Long> count() {
        long start = System.nanoTime();
        if (dynamoDbAsyncClient != null) {
            return countWithLowLevel(select != null ? select : Select.COUNT)
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncQuery count on table '{}' returned {} items in {}ms",
                                    getTableName(), result, (System.nanoTime() - start) / 1_000_000);
                        }
                        return result;
                    });
        }
        return executeAsPages()
                .thenApply(pages -> {
                    long total = 0;
                    for (Page<T> page : pages) {
                        total += page.count();
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncQuery count on table '{}' returned {} items in {}ms",
                                getTableName(), total, (System.nanoTime() - start) / 1_000_000);
                    }
                    return total;
                });
    }

    // ============ Low-Level Count ============

    private @NonNull CompletableFuture<Long> countWithLowLevel(Select select) {
        if (pkValue == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Partition key value must be set before executing a query. "
                        + "Call partitionKey(), partitionKeyBeginsWith(), or a similar method first."));
        }
        String tableName = table != null ? table.tableName() : index.tableName();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();

        String keyConditionExpression = buildKeyConditionExpression(expressionNames, expressionValues);

        QueryRequest.Builder requestBuilder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(keyConditionExpression)
            .expressionAttributeNames(expressionNames)
            .expressionAttributeValues(expressionValues)
            .select(select);

        applyFilterExpression(requestBuilder, expressionNames, expressionValues);
        applyQueryOptions(requestBuilder);

        if (index != null) {
            requestBuilder.indexName(index.indexName());
        }

        return dynamoDbAsyncClient.query(requestBuilder.build())
            .thenApply(r -> (long) r.count())
            .exceptionally(AsyncExceptionMapper.handler("Query", tableName));
    }

    private String buildKeyConditionExpression(Map<String, String> expressionNames, Map<String, AttributeValue> expressionValues) {
        String pkName = table != null
            ? table.tableSchema().tableMetadata().primaryPartitionKey()
            : index.tableSchema().tableMetadata().primaryPartitionKey();

        StringBuilder keyExpr = new StringBuilder();
        String pkPlaceholder = "#pk";
        expressionNames.put(pkPlaceholder, pkName);
        String pkValPlaceholder = ":pk0";
        expressionValues.put(pkValPlaceholder, AttributeValueConverter.toKeyAttributeValue(pkValue));
        keyExpr.append(pkPlaceholder).append(" = ").append(pkValPlaceholder);

        Optional<String> skName = table != null
            ? table.tableSchema().tableMetadata().primarySortKey()
            : index.tableSchema().tableMetadata().primarySortKey();

        if (skValue != null && skName.isPresent()) {
            String skPlaceholder = "#sk";
            expressionNames.put(skPlaceholder, skName.get());
            String skValPlaceholder = ":sk0";
            expressionValues.put(skValPlaceholder, AttributeValueConverter.toKeyAttributeValue(skValue));

            switch (keyOp) {
                case BEGINS_WITH ->
                    keyExpr.append(CONDITION_JOINER)
                        .append("begins_with(").append(skPlaceholder).append(", ")
                        .append(skValPlaceholder).append(')');
                case BETWEEN -> {
                    String skValPlaceholder2 = ":sk1";
                    expressionValues.put(skValPlaceholder2, AttributeValueConverter.toKeyAttributeValue(skValue2));
                    keyExpr.append(CONDITION_JOINER)
                        .append(skPlaceholder).append(" BETWEEN ")
                        .append(skValPlaceholder).append(CONDITION_JOINER)
                        .append(skValPlaceholder2);
                }
                case GT ->
                    keyExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" > ").append(skValPlaceholder);
                case GE ->
                    keyExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" >= ").append(skValPlaceholder);
                case LT ->
                    keyExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" < ").append(skValPlaceholder);
                case LE ->
                    keyExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" <= ").append(skValPlaceholder);
                case EQ ->
                    keyExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" = ").append(skValPlaceholder);
            }
        }
        return keyExpr.toString();
    }

    private void applyFilterExpression(
        QueryRequest.Builder requestBuilder,
        Map<String, String> keyNames,
        Map<String, AttributeValue> keyValues
    ) {
        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(filterExpression.getExpression());
            requestBuilder.expressionAttributeNames(mergeMaps(keyNames, filterExpression.getExpressionNames()));
            requestBuilder.expressionAttributeValues(mergeMaps(keyValues, filterExpression.getExpressionValues()));
        }
    }

    private void applyQueryOptions(QueryRequest.Builder requestBuilder) {
        if (limit != null) {
            requestBuilder.limit(limit);
        }
        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }
        if (consistentRead != null) {
            requestBuilder.consistentRead(consistentRead);
        }
        if (scanIndexForward != null) {
            requestBuilder.scanIndexForward(scanIndexForward);
        }
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
    }

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> base, Map<K, V> override) {
        Map<K, V> merged = new HashMap<>(base);
        merged.putAll(override);
        return merged;
    }

    // ============ Internal ============

    private String getTableName() {
        return table != null ? table.tableName() : index.tableName();
    }

    private void requirePartitionKey() {
        if (pkValue == null) {
            throw new IllegalStateException("Partition key value must be set before executing a query. "
                    + "Call partitionKey(), partitionKeyBeginsWith(), or a similar method first.");
        }
    }

    private QueryEnhancedRequest.@NonNull Builder buildBaseRequest() {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(keyCondition)
                .scanIndexForward(scanIndexForward)
                .consistentRead(consistentRead);
        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(filterExpression.toSdkExpression());
        }
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            requestBuilder.attributesToProject(
                    projectionExpression.getProjectedAttributes().toArray(new String[0]));
        }
        if (limit != null) {
            requestBuilder.limit(limit);
        }
        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        return requestBuilder;
    }

    /**
     * Builds the query request and returns the raw publisher without collecting pages.
     */
    private @NonNull SdkPublisher<Page<T>> buildPagePublisher() {
        requirePartitionKey();
        QueryEnhancedRequest request = buildBaseRequest().build();
        if (index != null) {
            return index.query(request);
        }
        return table.query(request);
    }

    /**
     * Builds the request, executes it via the async table, and collects all
     * pages into a list through a {@link PagePublisher}.
     */
    private @NonNull CompletableFuture<List<Page<T>>> executeAsPages() {
        try {
            requirePartitionKey();
            QueryEnhancedRequest request = buildBaseRequest().build();
            if (index != null) {
                return PageCollector.collectPages(index.query(request));
            }
            return PageCollector.collectPages(table.query(request));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }


}
