package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractCrossTableBatchGetBuilder;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builds an async cross-table batch get operation to retrieve items from multiple tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#batchGet()}.
 * Uses the low-level {@link DynamoDbAsyncClient#batchGetItem(BatchGetItemRequest)}
 * which returns a {@link CompletableFuture}, avoiding the reactive publisher model.
 */
public class AsyncCrossTableBatchGetBuilder extends AbstractCrossTableBatchGetBuilder<AsyncCrossTableBatchGetBuilder> {

    private static final Logger LOG = Logging.getLogger(AsyncCrossTableBatchGetBuilder.class);

    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncCrossTableBatchGetBuilder}.
     *
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncCrossTableBatchGetBuilder(@NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncCrossTableBatchGetBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Entry entry) {
        return ((AsyncTable<?>) entry.table()).getRawTable().tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Entry entry) {
        return ((AsyncTable<?>) entry.table()).getRawTable().tableSchema();
    }

    /**
     * Adds a key to retrieve by partition key from the given async table.
     *
     * @param table        the typed async table to read from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchGetBuilder addKey(@NonNull AsyncTable<T> table, @NonNull Object partitionKey) {
        entries.add(new Entry(table, buildKey(partitionKey, null), null));
        return self();
    }

    /**
     * Adds a key to retrieve by partition and sort key from the given async table.
     *
     * @param table        the typed async table to read from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchGetBuilder addKey(@NonNull AsyncTable<T> table,
                                                     @NonNull Object partitionKey,
                                                     @NonNull Object sortKey) {
        entries.add(new Entry(table, buildKey(partitionKey, sortKey), null));
        return self();
    }

    /**
     * Adds multiple partition keys from the given async table at once.
     *
     * @param table         the typed async table to read from
     * @param partitionKeys the partition key values
     * @param <T>           the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncCrossTableBatchGetBuilder addKeys(@NonNull AsyncTable<T> table,
                                                      @NonNull Collection<?> partitionKeys) {
        for (Object pk : partitionKeys) {
            entries.add(new Entry(table, buildKey(pk, null), null));
        }
        return self();
    }

    /**
     * Executes the batch get operation asynchronously.
     * <p>
     * Groups entries by table name and calls the low-level DynamoDB API.
     *
     * @return a {@link CompletableFuture} containing the {@link CrossTableBatchGetResult}
     * @throws IllegalArgumentException if more than 100 keys are provided
     */
    @NonNull
    public CompletableFuture<CrossTableBatchGetResult> execute() {
        long start = System.nanoTime();
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new CrossTableBatchGetResult(Map.of(), Map.of(), Map.of()));
        }

        if (entries.size() > DynamoDbLimits.BATCH_GET_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.CROSS_TABLE_BATCH_GET_SIZE_FMT.formatted(DynamoDbLimits.BATCH_GET_MAX_SIZE, entries.size()));
        }

        Map<String, List<Entry>> entriesByTable = groupEntriesByTable();
        Map<String, TableSchema<?>> tableSchemas = new HashMap<>();
        Map<String, KeysAndAttributes> requestItems = buildRequestItems(entriesByTable, tableSchemas);

        BatchGetItemRequest.Builder requestBuilder = BatchGetItemRequest.builder().requestItems(requestItems);
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        return dynamoDbAsyncClient.batchGetItem(requestBuilder.build())
                .thenApply(response -> buildCrossTableBatchGetResult(response, tableSchemas, start))
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), null));
    }

    private CrossTableBatchGetResult buildCrossTableBatchGetResult(
            BatchGetItemResponse response,
            Map<String, TableSchema<?>> tableSchemas,
            long start) {
        Map<String, List<Map<String, AttributeValue>>> responses = response.responses();
        Map<String, KeysAndAttributes> unprocessed = response.unprocessedKeys();
        if (LOG.isDebugEnabled()) {
            int totalItems = (responses != null ? responses : Map.<String, List<Map<String, AttributeValue>>>of())
                    .values().stream().mapToInt(List::size).sum();
            LOG.debug("Async cross-table batch get returned {} items from {} tables in {}ms",
                    totalItems, responses != null ? responses.size() : 0,
                    (System.nanoTime() - start) / 1_000_000);
        }
        return new CrossTableBatchGetResult(
                responses != null ? responses : Map.of(),
                unprocessed != null ? unprocessed : Map.of(),
                tableSchemas);
    }
}
