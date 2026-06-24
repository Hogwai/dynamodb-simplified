package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Builds an async batch write operation to put and delete multiple items in a single table.
 * <p>
 * Obtain via {@link AsyncTable#batchWrite()}. Executes using the async enhanced client and
 * returns a {@link CompletableFuture} that completes when the batch write has been submitted.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * If the batch size exceeds DynamoDB limits, unprocessed items may remain.
 * Automatic retry of unprocessed items is not currently implemented.
 *
 * @param <T> the item type
 */
public class AsyncBatchWriteBuilder<T> {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<T> table;
    private final List<T> itemsToPut = new ArrayList<>();
    private final List<Key> keysToDelete = new ArrayList<>();

    /**
     * Constructs a new {@code AsyncBatchWriteBuilder}.
     *
     * @param enhancedClient the enhanced async DynamoDB client
     * @param table          the async DynamoDB table
     */
    AsyncBatchWriteBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient, @NonNull DynamoDbAsyncTable<T> table) {
        this.enhancedClient = enhancedClient;
        this.table = table;
    }

    /**
     * Adds an item to be put (inserted or replaced) in the batch write.
     *
     * @param item the item to put
     * @return this builder
     */
    @NonNull
    public AsyncBatchWriteBuilder<T> put(@NonNull T item) {
        itemsToPut.add(Objects.requireNonNull(item, "item must not be null"));
        return this;
    }

    /**
     * Adds a delete operation for the given partition key.
     *
     * @param partitionKey the partition key of the item to delete
     * @return this builder
     */
    @NonNull
    public AsyncBatchWriteBuilder<T> delete(@NonNull Object partitionKey) {
        keysToDelete.add(Key.builder().partitionValue(toAttributeValue(partitionKey)).build());
        return this;
    }

    /**
     * Adds a delete operation for the given partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    @NonNull
    public AsyncBatchWriteBuilder<T> delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        keysToDelete.add(Key.builder()
                .partitionValue(toAttributeValue(partitionKey))
                .sortValue(toAttributeValue(sortKey))
                .build());
        return this;
    }

    /**
     * Executes the batch write operation asynchronously.
     * <p>
     * All puts and deletes added to this builder are sent in a single batch write request.
     * Unprocessed items are not automatically retried.
     *
     * @return a {@link CompletableFuture} that completes when the batch write has been submitted
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        if (itemsToPut.isEmpty() && keysToDelete.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Class<T> itemClass = table.tableSchema().itemType().rawClass();
        WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(itemClass)
                .mappedTableResource(table);
        for (T item : itemsToPut) {
            batchBuilder.addPutItem(item);
        }
        for (Key key : keysToDelete) {
            batchBuilder.addDeleteItem(key);
        }

        BatchWriteItemEnhancedRequest request = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchWriteItem(request)
                .thenApply(ignored -> null);
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
