package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.BatchGetResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds a batch get operation to retrieve multiple items from a single table by their keys.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.Table#batchGet()}.
 *
 * @param <T> the item type
 */
public class BatchGetBuilder<T> {

    private static final Logger LOG = Logging.getLogger(BatchGetBuilder.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;
    private final List<Key> keys = new ArrayList<>();
    private boolean consistentRead;
    private ProjectionExpression projectionExpression;

    public BatchGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbTable<T> table,
                           @NonNull DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Adds a key to retrieve by partition key only.
     *
     * @param partitionKey the partition key value
     * @return this builder
     */
    public @NonNull BatchGetBuilder<T> addKey(@NonNull Object partitionKey) {
        keys.add(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build());
        return this;
    }

    /**
     * Adds a key to retrieve by partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    public @NonNull BatchGetBuilder<T> addKey(@NonNull Object partitionKey, @NonNull Object sortKey) {
        keys.add(Key.builder()
                     .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                     .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                     .build());
        return this;
    }

    /**
     * Adds multiple keys at once.
     *
     * @param keys the keys to retrieve
     * @return this builder
     */
    public @NonNull BatchGetBuilder<T> addKeys(@NonNull Collection<Key> keys) {
        this.keys.addAll(keys);
        return this;
    }

    /**
     * Enables or disables strongly consistent reads for this batch get.
     * <p>
     * If not set, the default (eventually consistent) is used by the DynamoDB API.
     *
     * @param consistentRead {@code true} for strongly consistent reads
     * @return this builder
     */
    public @NonNull BatchGetBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in each result
     * @return this builder for chaining
     */
    public @NonNull BatchGetBuilder<T> project(@NonNull String... attributes) {
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
    public @NonNull BatchGetBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    /**
     * Executes the batch get operation and returns all matching items along
     * with any unprocessed keys.
     * <p>
     * If a projection was set via {@link #project(String...)}, the operation
     * falls back to the low-level DynamoDB API to support attribute filtering.
     *
     * @return a {@link BatchGetResult} containing the retrieved items and unprocessed keys
     * @throws IllegalArgumentException if more than 100 keys are provided
     */
    public @NonNull BatchGetResult<T> execute() {
        if (keys.isEmpty()) {
            return new BatchGetResult<>(List.of(), Map.of());
        }

        if (keys.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "BatchGet supports a maximum of " + MAX_BATCH_SIZE + " keys per request, but " + keys.size() + " were provided");
        }

        long start = System.nanoTime();
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            var result = executeWithProjection();
            if (LOG.isDebugEnabled()) {
                LOG.debug("BatchGet on table '{}' returned {} items in {}ms (with projection)",
                        table.tableName(), result.items().size(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        }

        Class<T> itemClass = table.tableSchema().itemType().rawClass();
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);
        for (Key key : keys) {
            if (consistentRead) {
                batchBuilder.addGetItem(b -> b.key(key).consistentRead(true));
            } else {
                batchBuilder.addGetItem(key);
            }
        }

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build())
                .build();

        List<T> results;
        try {
            results = enhancedClient.batchGetItem(request)
                    .resultsForTable(table)
                    .stream()
                    .toList();
        } catch (DynamoDbException e) {
            throw new OperationFailedException("BatchGetItem", table.tableName(), e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("BatchGet on table '{}' returned {} items in {}ms",
                    table.tableName(), results.size(), (System.nanoTime() - start) / 1_000_000);
        }
        return new BatchGetResult<>(results, Map.of());
    }

    private BatchGetResult<T> executeWithProjection() {
        String tableName = table.tableName();
        List<Map<String, AttributeValue>> sdkKeys = new ArrayList<>(keys.size());
        for (Key key : keys) {
            sdkKeys.add(key.primaryKeyMap(table.tableSchema()));
        }

        KeysAndAttributes keysAndAttributes = KeysAndAttributes.builder()
                .keys(sdkKeys)
                .consistentRead(consistentRead)
                .projectionExpression(projectionExpression.getExpression())
                .expressionAttributeNames(projectionExpression.getExpressionNames())
                .build();

        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        requestItems.put(tableName, keysAndAttributes);

        BatchGetItemRequest request = BatchGetItemRequest.builder()
                .requestItems(requestItems)
                .build();

        List<Map<String, AttributeValue>> items;
        Map<String, KeysAndAttributes> unprocessed;
        try {
            var response = dynamoDbClient.batchGetItem(request);
            items = response.responses()
                    .getOrDefault(tableName, List.of());
            unprocessed = response.unprocessedKeys();
        } catch (DynamoDbException e) {
            throw new OperationFailedException("BatchGetItem", tableName, e);
        }
        List<T> results = new ArrayList<>(items.size());
        for (Map<String, AttributeValue> item : items) {
            results.add(table.tableSchema().mapToItem(item));
        }
        return new BatchGetResult<>(results, unprocessed != null ? unprocessed : Map.of());
    }


}
