package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.builder.base.AbstractCrossTableBatchGetBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * Builds a cross-table batch get operation to retrieve items from multiple tables by their keys.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#batchGet()}.
 * Unlike the single-table {@link BatchGetBuilder}, this builder accepts table-scoped keys,
 * allowing retrieval from multiple tables in a single request.
 */
public class CrossTableBatchGetBuilder extends AbstractCrossTableBatchGetBuilder<CrossTableBatchGetBuilder> {

    private static final Logger LOG = Logging.getLogger(CrossTableBatchGetBuilder.class);

    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code CrossTableBatchGetBuilder}.
     *
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public CrossTableBatchGetBuilder(@NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull CrossTableBatchGetBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Entry entry) {
        return ((Table<?>) entry.table()).getRawTable().tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Entry entry) {
        return ((Table<?>) entry.table()).getRawTable().tableSchema();
    }

    /**
     * Adds a key to retrieve by partition key from the given table.
     *
     * @param table        the typed table to read from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchGetBuilder addKey(@NonNull Table<T> table, @NonNull Object partitionKey) {
        entries.add(new Entry(table, buildKey(partitionKey, null), null));
        return self();
    }

    /**
     * Adds a key to retrieve by partition and sort key from the given table.
     *
     * @param table        the typed table to read from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchGetBuilder addKey(@NonNull Table<T> table,
                                                         @NonNull Object partitionKey,
                                                         @NonNull Object sortKey) {
        entries.add(new Entry(table, buildKey(partitionKey, sortKey), null));
        return self();
    }

    /**
     * Adds multiple partition keys from the given table at once.
     *
     * @param table         the typed table to read from
     * @param partitionKeys the partition key values
     * @param <T>           the item type
     * @return this builder
     */
    public @NonNull <T> CrossTableBatchGetBuilder addKeys(@NonNull Table<T> table,
                                                          @NonNull Collection<?> partitionKeys) {
        for (Object pk : partitionKeys) {
            entries.add(new Entry(table, buildKey(pk, null), null));
        }
        return self();
    }

    /**
     * Executes the batch get operation and returns items grouped by table.
     * <p>
     * Groups entries by table name and calls the low-level DynamoDB API.
     *
     * @return a {@link CrossTableBatchGetResult} containing the retrieved items and unprocessed keys
     * @throws IllegalArgumentException if more than 100 keys are provided
     */
    public @NonNull CrossTableBatchGetResult execute() {
        long start = System.nanoTime();
        if (entries.isEmpty()) {
            return new CrossTableBatchGetResult(Map.of(), Map.of(), Map.of());
        }

        if (entries.size() > DynamoDbLimits.BATCH_GET_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.CROSS_TABLE_BATCH_GET_SIZE_FMT.formatted(DynamoDbLimits.BATCH_GET_MAX_SIZE, entries.size()));
        }

        Map<String, List<Entry>> entriesByTable = groupEntriesByTable();
        Map<String, TableSchema<?>> tableSchemas = new HashMap<>();
        Map<String, KeysAndAttributes> requestItems = buildRequestItems(entriesByTable, tableSchemas);

        return retryLoop(requestItems, tableSchemas, start);
    }

    private BatchGetItemResponse executeRequest(Map<String, KeysAndAttributes> requestItems) {
        try {
            BatchGetItemRequest.Builder requestBuilder = BatchGetItemRequest.builder().requestItems(requestItems);
            if (returnConsumedCapacity != null) {
                requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
            }
            return dynamoDbClient.batchGetItem(requestBuilder.build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), null, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), null, e);
        }
    }

    private CrossTableBatchGetResult retryLoop(
            Map<String, KeysAndAttributes> initialRequestItems,
            Map<String, TableSchema<?>> tableSchemas,
            long start) {
        Map<String, KeysAndAttributes> currentItems = initialRequestItems;
        Map<String, List<Map<String, AttributeValue>>> allResponses = new HashMap<>();
        int attempt = 0;

        while (true) {
            BatchGetItemResponse response = executeRequest(currentItems);
            accumulateItems(allResponses, response);
            Map<String, KeysAndAttributes> unprocessed = response.unprocessedKeys();

            if (unprocessed == null || unprocessed.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("CrossTable batch get returned {} items from {} tables in {}ms",
                            allResponses.values().stream().mapToInt(List::size).sum(),
                            allResponses.size(),
                            (System.nanoTime() - start) / 1_000_000);
                }
                return new CrossTableBatchGetResult(allResponses, Map.of(), tableSchemas);
            }

            if (attempt >= DynamoDbLimits.MAX_RETRIES) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("CrossTable batch get exhausted retries, {} unprocessed keys remain",
                            unprocessed.values().stream().mapToInt(ka -> ka.keys().size()).sum());
                }
                return new CrossTableBatchGetResult(allResponses, unprocessed, tableSchemas);
            }

            if (sleepWithBackoff(attempt)) {
                return new CrossTableBatchGetResult(allResponses, unprocessed, tableSchemas);
            }
            currentItems = unprocessed;
            attempt++;
        }
    }

    private boolean sleepWithBackoff(int attempt) {
        return RetryUtils.sleepWithBackoff(attempt, DynamoDbLimits.BASE_BACKOFF_MS);
    }

    private static void accumulateItems(
            Map<String, List<Map<String, AttributeValue>>> allResponses,
            BatchGetItemResponse response) {
        Map<String, List<Map<String, AttributeValue>>> responses = response.responses();
        if (responses == null) {
            return;
        }
        for (Map.Entry<String, List<Map<String, AttributeValue>>> entry : responses.entrySet()) {
            allResponses.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }
}
