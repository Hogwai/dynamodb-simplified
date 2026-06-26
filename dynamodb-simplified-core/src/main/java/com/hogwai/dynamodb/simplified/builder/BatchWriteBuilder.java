package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds a batch write operation to put and delete multiple items in a single table.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.Table#batchWrite()}.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 *
 * @param <T> the item type
 */
public class BatchWriteBuilder<T> {

    private static final Logger LOG = Logging.getLogger(BatchWriteBuilder.class);
    private static final int MAX_BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;
    private final List<T> itemsToPut = new ArrayList<>();
    private final List<Key> keysToDelete = new ArrayList<>();

    /**
     * Creates a new {@code BatchWriteBuilder} with the given table and low-level client.
     *
     * @param table          the DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public BatchWriteBuilder(@NonNull DynamoDbTable<T> table,
                             @NonNull DynamoDbClient dynamoDbClient) {
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Adds an item to be put (inserted or replaced) in the batch write.
     *
     * @param item the item to put
     * @return this builder
     */
    public @NonNull BatchWriteBuilder<T> put(@NonNull T item) {
        itemsToPut.add(Objects.requireNonNull(item, "item must not be null"));
        return this;
    }

    /**
     * Adds a delete operation for the given partition key.
     *
     * @param partitionKey the partition key of the item to delete
     * @return this builder
     */
    public @NonNull BatchWriteBuilder<T> delete(@NonNull Object partitionKey) {
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
    public @NonNull BatchWriteBuilder<T> delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        keysToDelete.add(Key.builder()
                             .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                             .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                             .build());
        return this;
    }

    /**
     * Executes the batch write operation.
     * <p>
     * All puts and deletes added to this builder are sent in a single batch write request.
     * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
     * Uses the low-level client so that unprocessed items can be retried.
     *
     * @return a {@link BatchWriteResult} containing any unprocessed items
     * @throws IllegalArgumentException if more than 25 items (puts + deletes combined) are provided
     */
    public @NonNull BatchWriteResult execute() {
        long start = System.nanoTime();
        if (itemsToPut.isEmpty() && keysToDelete.isEmpty()) {
            return new BatchWriteResult(Map.of());
        }

        int totalItems = itemsToPut.size() + keysToDelete.size();
        if (totalItems > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "BatchWrite supports a maximum of " + MAX_BATCH_SIZE + " items per request, but " + totalItems + " were provided");
        }

        Map<String, List<WriteRequest>> requestItems = buildRequestItems();
        BatchWriteResult result = retryLoop(requestItems);
        if (LOG.isDebugEnabled()) {
            LOG.debug("BatchWrite on table '{}' completed in {}ms ({} puts, {} deletes)",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000,
                    itemsToPut.size(), keysToDelete.size());
        }
        return result;
    }

    private BatchWriteResult retryLoop(Map<String, List<WriteRequest>> initialRequestItems) {
        Map<String, List<WriteRequest>> currentItems = initialRequestItems;
        int attempt = 0;
        while (true) {
            Map<String, List<WriteRequest>> unprocessed;
            try {
                var response = dynamoDbClient.batchWriteItem(
                        BatchWriteItemRequest.builder().requestItems(currentItems).build());
                unprocessed = response.unprocessedItems();
            } catch (DynamoDbException e) {
                throw new OperationFailedException("BatchWriteItem", table.tableName(), e);
            }
            if (unprocessed == null || unprocessed.isEmpty()) {
                return new BatchWriteResult(Map.of());
            }

            if (attempt >= MAX_RETRIES) {
                return new BatchWriteResult(unprocessed);
            }

            if (sleepWithBackoff(attempt)) {
                return new BatchWriteResult(unprocessed);
            }
            currentItems = unprocessed;
            attempt++;
        }
    }

    private boolean sleepWithBackoff(int attempt) {
        long backoff = BASE_BACKOFF_MS * (1L << attempt);
        backoff += ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
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
