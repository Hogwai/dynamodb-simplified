package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractBatchWriteBuilder;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.BatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;
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
public class AsyncBatchWriteBuilder<T> extends AbstractBatchWriteBuilder<T, AsyncBatchWriteBuilder<T>> {

    private static final Logger LOG = Logging.getLogger(AsyncBatchWriteBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncBatchWriteBuilder}.
     *
     * @param table               the async DynamoDB table
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    AsyncBatchWriteBuilder(@NonNull DynamoDbAsyncTable<T> table,
                           @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected AsyncBatchWriteBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull TableSchema<T> tableSchema() {
        return table.tableSchema();
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
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
        if (totalItems > DynamoDbLimits.BATCH_WRITE_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.BATCH_WRITE_SIZE_FMT.formatted(DynamoDbLimits.BATCH_WRITE_MAX_SIZE, totalItems));
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
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), table.tableName()))
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
                    if (attempt >= DynamoDbLimits.MAX_RETRIES) {
                        return CompletableFuture.completedFuture(new BatchWriteResult(unprocessed));
                    }
                    long backoff = DynamoDbLimits.BASE_BACKOFF_MS * (1L << attempt);
                    backoff += ThreadLocalRandom.current().nextLong(DynamoDbLimits.BASE_BACKOFF_MS);
                    return AsyncRetryUtils.delay(backoff)
                            .thenCompose(v -> executeWithRetry(unprocessed, attempt + 1, start));
                });
    }

}
