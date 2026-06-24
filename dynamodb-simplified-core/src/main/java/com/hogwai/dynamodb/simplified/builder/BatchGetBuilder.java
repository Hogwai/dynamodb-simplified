package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private final List<Key> keys = new ArrayList<>();

    public BatchGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbTable<T> table) {
        this.enhancedClient = enhancedClient;
        this.table = table;
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
     * Executes the batch get operation and returns all matching items.
     *
     * @return the list of retrieved items (order may not match the requested keys)
     */
    public @NonNull List<T> execute() {
        if (keys.isEmpty()) {
            return List.of();
        }

        Class<T> itemClass = table.tableSchema().itemType().rawClass();
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);
        for (Key key : keys) {
            batchBuilder.addGetItem(key);
        }

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchGetItem(request)
                .resultsForTable(table)
                .stream()
                .toList();
    }


}
