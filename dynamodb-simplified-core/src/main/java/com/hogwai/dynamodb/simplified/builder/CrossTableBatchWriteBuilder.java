package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.internal.RetryUtils;
import com.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * Builds a cross-table batch write operation to put and delete items across multiple tables.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#batchWrite()}.
 * Unlike the single-table {@link BatchWriteBuilder}, this builder accepts table-scoped
 * puts and deletes, allowing writes to multiple tables in a single request.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 */
public class CrossTableBatchWriteBuilder {

    private static final Logger LOG = Logging.getLogger(CrossTableBatchWriteBuilder.class);
    private static final int MAX_BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final DynamoDbClient dynamoDbClient;
    private final List<Operation> operations = new ArrayList<>();
    private ReturnConsumedCapacity returnConsumedCapacity;

    private record Operation(
            Type type,
            Table<?> table,
            @Nullable Object item,
            @Nullable Object partitionKey,
            @Nullable Object sortKey
    ) {
        enum Type {
            PUT, DELETE
        }
    }

    /**
     * Constructs a new {@code CrossTableBatchWriteBuilder}.
     *
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public CrossTableBatchWriteBuilder(@NonNull DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Adds a put action to insert or replace an item in the given table.
     *
     * @param table the typed table to write to
     * @param item  the item to put
     * @param <T>   the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchWriteBuilder put(@NonNull Table<T> table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.PUT, table, Objects.requireNonNull(item), null, null));
        return this;
    }

    /**
     * Adds a delete action for the given partition key in the given table.
     *
     * @param table        the typed table to delete from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchWriteBuilder delete(@NonNull Table<T> table, @NonNull Object partitionKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, null));
        return this;
    }

    /**
     * Adds a delete action for the given partition and sort key in the given table.
     *
     * @param table        the typed table to delete from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchWriteBuilder delete(@NonNull Table<T> table,
                                                           @NonNull Object partitionKey,
                                                           @NonNull Object sortKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, sortKey));
        return this;
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public CrossTableBatchWriteBuilder returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Executes the batch write operation.
     * <p>
     * All puts and deletes added to this builder are sent in a single batch write request.
     * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
     *
     * @return a {@link CrossTableBatchWriteResult} containing any unprocessed items
     * @throws IllegalArgumentException if more than 25 items (puts + deletes combined) are provided
     */
    public @NonNull CrossTableBatchWriteResult execute() {
        long start = System.nanoTime();
        if (operations.isEmpty()) {
            return new CrossTableBatchWriteResult(Map.of());
        }

        if (operations.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "CrossTable batch write supports a maximum of " + MAX_BATCH_SIZE
                            + " items per request, but " + operations.size() + " were provided");
        }

        Map<String, List<WriteRequest>> requestItems = buildRequestItems();
        CrossTableBatchWriteResult result = retryLoop(requestItems);
        if (LOG.isDebugEnabled()) {
            LOG.debug("CrossTable batch write completed in {}ms ({} operations)",
                    (System.nanoTime() - start) / 1_000_000, operations.size());
        }
        return result;
    }

    private CrossTableBatchWriteResult retryLoop(Map<String, List<WriteRequest>> initialRequestItems) {
        Map<String, List<WriteRequest>> currentItems = initialRequestItems;
        int attempt = 0;
        while (true) {
            Map<String, List<WriteRequest>> unprocessed;
            try {
                BatchWriteItemRequest.Builder batchRequestBuilder = BatchWriteItemRequest.builder().requestItems(currentItems);
                if (returnConsumedCapacity != null) {
                    batchRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
                }
                var response = dynamoDbClient.batchWriteItem(batchRequestBuilder.build());
                unprocessed = response.unprocessedItems();
            } catch (DynamoDbException e) {
                throw new OperationFailedException("BatchWriteItem", null, e);
            }
            if (unprocessed == null || unprocessed.isEmpty()) {
                return new CrossTableBatchWriteResult(Map.of());
            }

            if (attempt >= MAX_RETRIES) {
                return new CrossTableBatchWriteResult(unprocessed);
            }

            if (sleepWithBackoff(attempt)) {
                return new CrossTableBatchWriteResult(unprocessed);
            }
            currentItems = unprocessed;
            attempt++;
        }
    }

    private boolean sleepWithBackoff(int attempt) {
        return RetryUtils.sleepWithBackoff(attempt, BASE_BACKOFF_MS);
    }

    @SuppressWarnings({"unchecked"})
    private Map<String, List<WriteRequest>> buildRequestItems() {
        Map<String, List<WriteRequest>> requestMap = new HashMap<>();
        for (Operation op : operations) {
            String tableName = op.table().getRawTable().tableName();
            List<WriteRequest> writes = requestMap.computeIfAbsent(tableName, ignored -> new ArrayList<>());

            switch (op.type()) {
                case PUT -> {
                    Objects.requireNonNull(op.item(), "item must not be null for PUT");
                    var rawTable = (DynamoDbTable<Object>) op.table().getRawTable();
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
