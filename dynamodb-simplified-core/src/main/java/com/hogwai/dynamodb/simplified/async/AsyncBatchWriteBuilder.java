package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.BatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds an async batch write operation to put and delete multiple items in a single table.
 * <p>
 * Obtain via {@link AsyncTable#batchWrite()}. Executes using the async enhanced client and
 * returns a {@link CompletableFuture} that completes with the batch write result.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 *
 * @param <T> the item type
 */
public class AsyncBatchWriteBuilder<T> {

    private static final Logger LOG = Logging.getLogger(AsyncBatchWriteBuilder.class);
    private static final int MAX_BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final List<T> itemsToPut = new ArrayList<>();
    private final List<Key> keysToDelete = new ArrayList<>();
    private ReturnConsumedCapacity returnConsumedCapacity;

    /**
     * Constructs a new {@code AsyncBatchWriteBuilder}.
     *
     * @param table               the async DynamoDB table
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    AsyncBatchWriteBuilder(@NonNull DynamoDbAsyncTable<T> table,
                           @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
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
        keysToDelete.add(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build());
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
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                .build());
        return this;
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public AsyncBatchWriteBuilder<T> returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Executes the batch write operation asynchronously.
     * <p>
     * All puts and deletes added to this builder are sent in a single batch write request.
     * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
     *
     * @return a {@link CompletableFuture} containing the {@link BatchWriteResult}
     * @throws IllegalArgumentException if more than 25 items (puts + deletes combined) are provided
     */
    @NonNull
    public CompletableFuture<BatchWriteResult> execute() {
        long start = System.nanoTime();
        if (itemsToPut.isEmpty() && keysToDelete.isEmpty()) {
            return CompletableFuture.completedFuture(new BatchWriteResult(Map.of()));
        }

        int totalItems = itemsToPut.size() + keysToDelete.size();
        if (totalItems > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "BatchWrite supports a maximum of " + MAX_BATCH_SIZE + " items per request, but " + totalItems + " were provided");
        }

        if (dynamoDbAsyncClient == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "AsyncBatchWrite requires a low-level DynamoDbAsyncClient (use the 3-param constructor)"));
        }

        Map<String, List<WriteRequest>> requestItems = buildRequestItems();
        return executeWithRetry(requestItems, 0, start);
    }

    private CompletableFuture<BatchWriteResult> executeWithRetry(
            Map<String, List<WriteRequest>> requestItems, int attempt, long start) {
        BatchWriteItemRequest.Builder batchRequestBuilder = BatchWriteItemRequest.builder().requestItems(requestItems);
        if (returnConsumedCapacity != null) {
            batchRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        return dynamoDbAsyncClient.batchWriteItem(batchRequestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler("BatchWriteItem", table.tableName()))
                .thenCompose(response -> {
                    Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();
                    if (unprocessed == null || unprocessed.isEmpty()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncBatchWrite on table '{}' completed in {}ms ({} puts, {} deletes)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000,
                                    itemsToPut.size(), keysToDelete.size());
                        }
                        return CompletableFuture.completedFuture(new BatchWriteResult(Map.of()));
                    }
                    if (attempt >= MAX_RETRIES) {
                        return CompletableFuture.completedFuture(new BatchWriteResult(unprocessed));
                    }
                    long backoff = BASE_BACKOFF_MS * (1L << attempt);
                    backoff += ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return CompletableFuture.completedFuture(new BatchWriteResult(unprocessed));
                    }
                    return executeWithRetry(unprocessed, attempt + 1, start);
                });
    }

    private Map<String, List<WriteRequest>> buildRequestItems() {
        List<WriteRequest> writes = new ArrayList<>(itemsToPut.size() + keysToDelete.size());
        for (T item : itemsToPut) {
            Map<String, AttributeValue> itemMap = table.tableSchema().itemToMap(item, true);
            writes.add(WriteRequest.builder().putRequest(r -> r.item(itemMap)).build());
        }
        for (Key key : keysToDelete) {
            Map<String, AttributeValue> keyMap = key.primaryKeyMap(table.tableSchema());
            writes.add(WriteRequest.builder().deleteRequest(r -> r.key(keyMap)).build());
        }
        return Map.of(table.tableName(), writes);
    }


}
