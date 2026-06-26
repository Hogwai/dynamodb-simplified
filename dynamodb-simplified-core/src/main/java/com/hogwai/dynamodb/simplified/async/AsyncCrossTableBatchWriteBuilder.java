package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds an async cross-table batch write operation to put and delete items
 * across multiple tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#batchWrite()}.
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 */
public class AsyncCrossTableBatchWriteBuilder {

    private static final Logger LOG = Logging.getLogger(AsyncCrossTableBatchWriteBuilder.class);
    private static final int MAX_BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final List<Operation> operations = new ArrayList<>();

    private record Operation(
            Type type,
            AsyncTable<?> table,
            @Nullable Object item,
            @Nullable Object partitionKey,
            @Nullable Object sortKey
    ) {
        enum Type {
            PUT, DELETE
        }
    }

    /**
     * Constructs a new {@code AsyncCrossTableBatchWriteBuilder}.
     *
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncCrossTableBatchWriteBuilder(@NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Adds a put action to insert or replace an item in the given async table.
     *
     * @param table the typed async table to write to
     * @param item  the item to put
     * @param <T>   the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchWriteBuilder put(@NonNull AsyncTable<T> table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.PUT, table, Objects.requireNonNull(item), null, null));
        return this;
    }

    /**
     * Adds a delete action for the given partition key in the given async table.
     *
     * @param table        the typed async table to delete from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchWriteBuilder delete(@NonNull AsyncTable<T> table, @NonNull Object partitionKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, null));
        return this;
    }

    /**
     * Adds a delete action for the given partition and sort key in the given async table.
     *
     * @param table        the typed async table to delete from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchWriteBuilder delete(@NonNull AsyncTable<T> table,
                                                        @NonNull Object partitionKey,
                                                        @NonNull Object sortKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, sortKey));
        return this;
    }

    /**
     * Executes the batch write operation asynchronously.
     * <p>
     * All puts and deletes are sent in a single batch write request.
     * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
     *
     * @return a {@link CompletableFuture} containing the {@link CrossTableBatchWriteResult}
     * @throws IllegalArgumentException if more than 25 items (puts + deletes combined) are provided
     */
    @NonNull
    public CompletableFuture<CrossTableBatchWriteResult> execute() {
        long start = System.nanoTime();
        if (operations.isEmpty()) {
            return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(Map.of()));
        }

        if (operations.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "CrossTable batch write supports a maximum of " + MAX_BATCH_SIZE
                            + " items per request, but " + operations.size() + " were provided");
        }

        Map<String, List<WriteRequest>> requestItems = buildRequestItems();
        return executeWithRetry(requestItems, 0, start);
    }

    private CompletableFuture<CrossTableBatchWriteResult> executeWithRetry(
            Map<String, List<WriteRequest>> requestItems, int attempt, long start) {
        return dynamoDbAsyncClient.batchWriteItem(
                        BatchWriteItemRequest.builder().requestItems(requestItems).build())
                .exceptionally(e -> {
                    if (e instanceof DynamoDbException dde) {
                        throw new OperationFailedException("BatchWriteItem", null, dde);
                    }
                    throw new DynamoSimplifiedException("BatchWriteItem failed", e);
                })
                .thenCompose(response -> {
                    Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();
                    if (unprocessed == null || unprocessed.isEmpty()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Async cross-table batch write completed in {}ms ({} operations)",
                                    (System.nanoTime() - start) / 1_000_000, operations.size());
                        }
                        return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(Map.of()));
                    }
                    if (attempt >= MAX_RETRIES) {
                        return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(unprocessed));
                    }
                    long backoff = BASE_BACKOFF_MS * (1L << attempt);
                    backoff += ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(unprocessed));
                    }
                    return executeWithRetry(unprocessed, attempt + 1, start);
                });
    }

    @SuppressWarnings({"unchecked"})
    private Map<String, List<WriteRequest>> buildRequestItems() {
        Map<String, List<WriteRequest>> requestMap = new HashMap<>();
        for (Operation op : operations) {
            String tableName = op.table().getRawTable().tableName();
            List<WriteRequest> writes = requestMap.computeIfAbsent(tableName, _ -> new ArrayList<>());

            switch (op.type()) {
                case PUT -> {
                    Objects.requireNonNull(op.item(), "item must not be null for PUT");
                    var rawTable = (DynamoDbAsyncTable<Object>) op.table().getRawTable();
                    Map<String, AttributeValue> itemMap = rawTable.tableSchema().itemToMap(op.item(), true);
                    writes.add(WriteRequest.builder().putRequest(r -> r.item(itemMap)).build());
                }
                case DELETE -> {
                    Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
                    Map<String, AttributeValue> keyMap = key.primaryKeyMap(
                            op.table().getRawTable().tableSchema());
                    writes.add(WriteRequest.builder().deleteRequest(r -> r.key(keyMap)).build());
                }
            }
        }
        return requestMap;
    }

    private static Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        Key.Builder builder = Key.builder()
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            builder.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return builder.build();
    }
}
