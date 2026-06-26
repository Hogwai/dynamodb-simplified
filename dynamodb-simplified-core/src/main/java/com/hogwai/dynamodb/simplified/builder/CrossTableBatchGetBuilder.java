package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.internal.RetryUtils;
import com.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds a cross-table batch get operation to retrieve items from multiple tables by their keys.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#batchGet()}.
 * Unlike the single-table {@link BatchGetBuilder}, this builder accepts table-scoped keys,
 * allowing retrieval from multiple tables in a single request.
 */
public class CrossTableBatchGetBuilder {

    private static final Logger LOG = Logging.getLogger(CrossTableBatchGetBuilder.class);
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final DynamoDbClient dynamoDbClient;
    private final List<Entry<?>> entries = new ArrayList<>();
    private ReturnConsumedCapacity returnConsumedCapacity;
    private Boolean consistentRead;

    /**
     * Constructs a new {@code CrossTableBatchGetBuilder}.
     *
     * @param dynamoDbClient the low-level DynamoDB client
     */
    public CrossTableBatchGetBuilder(@NonNull DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
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
        entries.add(new Entry<>(table, buildKey(partitionKey, null), null));
        return this;
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
        entries.add(new Entry<>(table, buildKey(partitionKey, sortKey), null));
        return this;
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
            entries.add(new Entry<>(table, buildKey(pk, null), null));
        }
        return this;
    }

    /**
     * Restricts the returned attributes for the most recently added entry
     * to the specified attribute names.
     * <p>
     * Projection is applied server-side, reducing data transfer and consumed capacity.
     *
     * @param attributes the attribute names to include
     * @return this builder
     * @throws IllegalStateException if no entries have been added yet
     */
    public @NonNull CrossTableBatchGetBuilder project(@NonNull String... attributes) {
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
    public @NonNull CrossTableBatchGetBuilder project(@NonNull Consumer<ProjectionExpression> consumer) {
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
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public CrossTableBatchGetBuilder returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Configures whether to use strongly consistent reads for this batch get operation.
     * <p>
     * If not set, DynamoDB defaults to eventually consistent reads.
     *
     * @param consistentRead {@code true} for strongly consistent reads
     * @return this builder for chaining
     */
    public @NonNull CrossTableBatchGetBuilder consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
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

        if (entries.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "CrossTable batch get supports a maximum of " + MAX_BATCH_SIZE
                            + " keys per request, but " + entries.size() + " were provided");
        }

        Map<String, List<Entry<?>>> entriesByTable = groupEntriesByTable();
        Map<String, TableSchema<?>> tableSchemas = new HashMap<>();
        Map<String, KeysAndAttributes> requestItems = buildRequestItems(entriesByTable, tableSchemas);

        return retryLoop(requestItems, tableSchemas, start);
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
            if (consistentRead != null) {
                kaBuilder.consistentRead(consistentRead);
            }
            requestItems.put(tableName, kaBuilder.build());
        }
        return requestItems;
    }

    private BatchGetItemResponse executeRequest(Map<String, KeysAndAttributes> requestItems) {
        try {
            BatchGetItemRequest.Builder requestBuilder = BatchGetItemRequest.builder().requestItems(requestItems);
            if (returnConsumedCapacity != null) {
                requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
            }
            return dynamoDbClient.batchGetItem(requestBuilder.build());
        } catch (DynamoDbException e) {
            throw new OperationFailedException("BatchGetItem", null, e);
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

            if (attempt >= MAX_RETRIES) {
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
        return RetryUtils.sleepWithBackoff(attempt, BASE_BACKOFF_MS);
    }

    private static void accumulateItems(
            Map<String, List<Map<String, AttributeValue>>> allResponses,
            BatchGetItemResponse response) {
        Map<String, List<Map<String, AttributeValue>>> responses = response.responses();
        if (responses == null) {
            return;
        }
        for (Map.Entry<String, List<Map<String, AttributeValue>>> entry : responses.entrySet()) {
            allResponses.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }

    private static Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        Key.Builder builder = Key.builder()
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            builder.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return builder.build();
    }

    private record Entry<T>(Table<T> table, Key key, @Nullable ProjectionExpression projectionExpression) {
    }
}
