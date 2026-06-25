package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import org.jspecify.annotations.NonNull;

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
     * Executes the batch get operation and returns all matching items.
     * <p>
     * If a projection was set via {@link #project(String...)}, the operation
     * falls back to the low-level DynamoDB API to support attribute filtering.
     *
     * @return the list of retrieved items (order may not match the requested keys)
     */
    public @NonNull List<T> execute() {
        if (keys.isEmpty()) {
            return List.of();
        }

        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            return executeWithProjection();
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

        return enhancedClient.batchGetItem(request)
                .resultsForTable(table)
                .stream()
                .toList();
    }

    private List<T> executeWithProjection() {
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

        var response = dynamoDbClient.batchGetItem(request);
        List<Map<String, AttributeValue>> items = response.responses()
                .getOrDefault(tableName, List.of());
        List<T> results = new ArrayList<>(items.size());
        for (Map<String, AttributeValue> item : items) {
            results.add(table.tableSchema().mapToItem(item));
        }
        return results;
    }


}
