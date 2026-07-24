package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractBatchWriteBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.BatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;

/**
 * Builds a batch write operation to put and delete multiple items in a single table.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.Table#batchWrite()}.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 *
 * @param <T> the item type
 */
public class BatchWriteBuilder<T> extends AbstractBatchWriteBuilder<T, BatchWriteBuilder<T>> {

    private static final Logger LOG = Logging.getLogger(BatchWriteBuilder.class);

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Creates a new {@code BatchWriteBuilder} with the given table and low-level client.
     *
     * @param table          the DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public BatchWriteBuilder(@NonNull DynamoDbTable<T> table,
                             @NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected BatchWriteBuilder<T> self() {
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
        if (totalItems > DynamoDbLimits.BATCH_WRITE_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.BATCH_WRITE_SIZE_FMT.formatted(DynamoDbLimits.BATCH_WRITE_MAX_SIZE, totalItems));
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
                BatchWriteItemRequest.Builder batchRequestBuilder = BatchWriteItemRequest.builder().requestItems(currentItems);
                if (returnConsumedCapacity != null) {
                    batchRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
                }
                var response = dynamoDbClient.batchWriteItem(batchRequestBuilder.build());
                unprocessed = response.unprocessedItems();
            } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
                throw new ResourceNotFoundException(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), table.tableName(), e);
            } catch (DynamoDbException e) {
                throw new OperationFailedException(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), table.tableName(), e);
            }
            if (unprocessed == null || unprocessed.isEmpty()) {
                return new BatchWriteResult(Map.of());
            }

            if (attempt >= DynamoDbLimits.MAX_RETRIES) {
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
        return RetryUtils.sleepWithBackoff(attempt, DynamoDbLimits.BASE_BACKOFF_MS);
    }

}
