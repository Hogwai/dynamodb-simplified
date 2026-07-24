package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractCrossTableBatchWriteBuilder;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;
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
public class AsyncCrossTableBatchWriteBuilder extends AbstractCrossTableBatchWriteBuilder<AsyncCrossTableBatchWriteBuilder> {

    private static final Logger LOG = Logging.getLogger(AsyncCrossTableBatchWriteBuilder.class);

    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncCrossTableBatchWriteBuilder}.
     *
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncCrossTableBatchWriteBuilder(@NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncCrossTableBatchWriteBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Operation operation) {
        return ((AsyncTable<?>) operation.table()).getRawTable().tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Operation operation) {
        return ((AsyncTable<?>) operation.table()).getRawTable().tableSchema();
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

        if (operations.size() > DynamoDbLimits.BATCH_WRITE_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.CROSS_TABLE_BATCH_WRITE_SIZE_FMT.formatted(DynamoDbLimits.BATCH_WRITE_MAX_SIZE, operations.size()));
        }

        Map<String, List<WriteRequest>> requestItems = buildRequestItems();
        return executeWithRetry(requestItems, 0, start);
    }

    private CompletableFuture<CrossTableBatchWriteResult> executeWithRetry(
            Map<String, List<WriteRequest>> requestItems, int attempt, long start) {
        BatchWriteItemRequest.Builder batchRequestBuilder = BatchWriteItemRequest.builder().requestItems(requestItems);
        if (returnConsumedCapacity != null) {
            batchRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        return dynamoDbAsyncClient.batchWriteItem(batchRequestBuilder.build())
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), null))
                .thenCompose(response -> {
                    Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();
                    if (unprocessed == null || unprocessed.isEmpty()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Async cross-table batch write completed in {}ms ({} operations)",
                                    (System.nanoTime() - start) / 1_000_000, operations.size());
                        }
                        return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(Map.of()));
                    }
                    if (attempt >= DynamoDbLimits.MAX_RETRIES) {
                        return CompletableFuture.completedFuture(new CrossTableBatchWriteResult(unprocessed));
                    }
                    long backoff = DynamoDbLimits.BASE_BACKOFF_MS * (1L << attempt);
                    backoff += ThreadLocalRandom.current().nextLong(DynamoDbLimits.BASE_BACKOFF_MS);
                    return AsyncRetryUtils.delay(backoff)
                            .thenCompose(v -> executeWithRetry(unprocessed, attempt + 1, start));
                });
    }
}
