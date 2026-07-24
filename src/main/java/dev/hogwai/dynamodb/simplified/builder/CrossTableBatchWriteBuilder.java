package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.builder.base.AbstractCrossTableBatchWriteBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;

/**
 * Builds a cross-table batch write operation to put and delete items across multiple tables.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#batchWrite()}.
 * Unlike the single-table {@link BatchWriteBuilder}, this builder accepts table-scoped
 * puts and deletes, allowing writes to multiple tables in a single request.
 * <p>
 * A single batch write can contain up to 25 put and delete operations combined.
 * Unprocessed items are automatically retried with exponential backoff (up to 3 attempts).
 */
public class CrossTableBatchWriteBuilder extends AbstractCrossTableBatchWriteBuilder<CrossTableBatchWriteBuilder> {

    private static final Logger LOG = Logging.getLogger(CrossTableBatchWriteBuilder.class);

    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code CrossTableBatchWriteBuilder}.
     *
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public CrossTableBatchWriteBuilder(@NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull CrossTableBatchWriteBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Operation operation) {
        return ((Table<?>) operation.table()).getRawTable().tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Operation operation) {
        return ((Table<?>) operation.table()).getRawTable().tableSchema();
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

        if (operations.size() > DynamoDbLimits.BATCH_WRITE_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.CROSS_TABLE_BATCH_WRITE_SIZE_FMT.formatted(DynamoDbLimits.BATCH_WRITE_MAX_SIZE, operations.size()));
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
            } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
                throw new ResourceNotFoundException(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), null, e);
            } catch (DynamoDbException e) {
                throw new OperationFailedException(DynamoDbOperations.BATCH_WRITE_ITEM.getOperationName(), null, e);
            }
            if (unprocessed == null || unprocessed.isEmpty()) {
                return new CrossTableBatchWriteResult(Map.of());
            }

            if (attempt >= DynamoDbLimits.MAX_RETRIES) {
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
        return RetryUtils.sleepWithBackoff(attempt, DynamoDbLimits.BASE_BACKOFF_MS);
    }
}
