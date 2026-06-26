package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Builds an async cross-table batch get operation to retrieve items from multiple tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#batchGet()}.
 * Uses the low-level {@link DynamoDbAsyncClient#batchGetItem(BatchGetItemRequest)}
 * which returns a {@link CompletableFuture}, avoiding the reactive publisher model.
 */
public class AsyncCrossTableBatchGetBuilder {

    private static final Logger LOG = Logging.getLogger(AsyncCrossTableBatchGetBuilder.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final List<Entry<?>> entries = new ArrayList<>();

    /**
     * Constructs a new {@code AsyncCrossTableBatchGetBuilder}.
     *
     * @param dynamoDbAsyncClient  the low-level async DynamoDB client
     */
    public AsyncCrossTableBatchGetBuilder(@NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
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
        entries.add(new Entry<>(table, buildKey(partitionKey, null), null));
        return this;
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
        entries.add(new Entry<>(table, buildKey(partitionKey, sortKey), null));
        return this;
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
            entries.add(new Entry<>(table, buildKey(pk, null), null));
        }
        return this;
    }

    /**
     * Restricts the returned attributes for the most recently added entry
     * to the specified attribute names.
     *
     * @param attributes the attribute names to include
     * @return this builder
     * @throws IllegalStateException if no entries have been added yet
     */
    @NonNull
    public AsyncCrossTableBatchGetBuilder project(@NonNull String... attributes) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("No entries have been added. Call addKey() or addKeys() first.");
        }
        ProjectionExpression expression = ProjectionExpression.builder().include(attributes);
        Entry<?> lastEntry = entries.removeLast();
        entries.add(new Entry<>(lastEntry.table, lastEntry.key, expression));
        return this;
    }

    /**
     * Restricts the returned attributes for the most recently added entry
     * using a {@link ProjectionExpression} builder consumer.
     *
     * @param consumer a consumer to configure the projection expression
     * @return this builder
     * @throws IllegalStateException if no entries have been added yet
     */
    @NonNull
    public AsyncCrossTableBatchGetBuilder project(@NonNull Consumer<ProjectionExpression> consumer) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("No entries have been added. Call addKey() or addKeys() first.");
        }
        ProjectionExpression expression = ProjectionExpression.builder();
        consumer.accept(expression);
        Entry<?> lastEntry = entries.removeLast();
        entries.add(new Entry<>(lastEntry.table, lastEntry.key, expression));
        return this;
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

        if (entries.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "CrossTable batch get supports a maximum of " + MAX_BATCH_SIZE
                            + " keys per request, but " + entries.size() + " were provided");
        }

        Map<String, List<Entry<?>>> entriesByTable = groupEntriesByTable();
        Map<String, TableSchema<?>> tableSchemas = new HashMap<>();
        Map<String, KeysAndAttributes> requestItems = buildRequestItems(entriesByTable, tableSchemas);

        return dynamoDbAsyncClient.batchGetItem(
                        BatchGetItemRequest.builder().requestItems(requestItems).build())
                .thenApply(response -> buildCrossTableBatchGetResult(response, tableSchemas, start))
                .exceptionally(AsyncExceptionMapper.handler("BatchGetItem", null));
    }

    private Map<String, List<Entry<?>>> groupEntriesByTable() {
        Map<String, List<Entry<?>>> entriesByTable = new HashMap<>();
        for (Entry<?> entry : entries) {
            String tableName = entry.table.getRawTable().tableName();
            entriesByTable.computeIfAbsent(tableName, _ -> new ArrayList<>()).add(entry);
        }
        return entriesByTable;
    }

    private Map<String, KeysAndAttributes> buildRequestItems(
            Map<String, List<Entry<?>>> entriesByTable,
            Map<String, TableSchema<?>> tableSchemas) {
        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        for (Map.Entry<String, List<Entry<?>>> tableEntry : entriesByTable.entrySet()) {
            buildTableKeysAndAttributes(tableEntry, requestItems, tableSchemas);
        }
        return requestItems;
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

    private void buildTableKeysAndAttributes(
            Map.Entry<String, List<Entry<?>>> tableEntry,
            Map<String, KeysAndAttributes> requestItems,
            Map<String, TableSchema<?>> tableSchemas) {
        String tableName = tableEntry.getKey();
        List<Entry<?>> tableEntries = tableEntry.getValue();

        List<Map<String, AttributeValue>> sdkKeys = new ArrayList<>(tableEntries.size());
        ProjectionExpression projection = null;
        TableSchema<?> schema = null;
        for (Entry<?> entry : tableEntries) {
            TableSchema<?> entrySchema = entry.table.getRawTable().tableSchema();
            if (schema == null) {
                schema = entrySchema;
            }
            sdkKeys.add(entry.key.primaryKeyMap(entrySchema));
            if (entry.projectionExpression != null && !entry.projectionExpression.isEmpty()) {
                projection = entry.projectionExpression;
            }
        }
        tableSchemas.put(tableName, schema);

        KeysAndAttributes.Builder kaBuilder = KeysAndAttributes.builder().keys(sdkKeys);
        if (projection != null) {
            kaBuilder
                    .projectionExpression(projection.getExpression())
                    .expressionAttributeNames(projection.getExpressionNames());
        }
        requestItems.put(tableName, kaBuilder.build());
    }

    private static Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        Key.Builder builder = Key.builder()
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            builder.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return builder.build();
    }

    private static class Entry<T> {
        final AsyncTable<T> table;
        final Key key;
        @Nullable final ProjectionExpression projectionExpression;

        Entry(AsyncTable<T> table, Key key, @Nullable ProjectionExpression projectionExpression) {
            this.table = table;
            this.key = key;
            this.projectionExpression = projectionExpression;
        }
    }
}
