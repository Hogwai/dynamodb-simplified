package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractBatchGetBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.*;
import dev.hogwai.dynamodb.simplified.result.BatchGetResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a batch get operation to retrieve multiple items from a single table by their keys.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.Table#batchGet()}.
 *
 * @param <T> the item type
 */
public class BatchGetBuilder<T> extends AbstractBatchGetBuilder<T, BatchGetBuilder<T>> {

    private static final Logger LOG = Logging.getLogger(BatchGetBuilder.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a batch get builder for the given table.
     * <p>
     * Typically obtained via {@link dev.hogwai.dynamodb.simplified.Table#batchGet()}.
     *
     * @param enhancedClient the enhanced DynamoDB client
     * @param table          the typed DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client (used for projection fallback)
     */
    public BatchGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbTable<T> table,
                           @NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected BatchGetBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    /**
     * Executes the batch get operation and returns all matching items along
     * with any unprocessed keys.
     * <p>
     * If a projection was set via {@link #project(String...)}, the operation
     * falls back to the low-level DynamoDB API to support attribute filtering.
     *
     * @return a {@link BatchGetResult} containing the retrieved items and unprocessed keys
     * @throws IllegalArgumentException if more than 100 keys are provided
     */
    public @NonNull BatchGetResult<T> execute() {
        if (keys.isEmpty()) {
            return new BatchGetResult<>(List.of(), Map.of());
        }

        if (keys.size() > DynamoDbLimits.BATCH_GET_MAX_SIZE) {
            throw new IllegalArgumentException(
                    Messages.BATCH_GET_SIZE_FMT.formatted(DynamoDbLimits.BATCH_GET_MAX_SIZE, keys.size()));
        }

        long start = System.nanoTime();

        BatchGetResult<T> result;
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            result = executeWithProjection();
        } else {
            result = executeEnhancedPath();
        }

        result = retryLoop(result);

        if (LOG.isDebugEnabled()) {
            LOG.debug("BatchGet on table '{}' returned {} items in {}ms",
                    table.tableName(), result.items().size(), (System.nanoTime() - start) / 1_000_000);
        }
        return result;
    }

    private BatchGetResult<T> executeEnhancedPath() {
        ReadBatch.Builder<T> batchBuilder = buildReadBatch();
        BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build());
        applyReturnConsumedCapacityIfNeeded(requestBuilder);
        BatchGetItemEnhancedRequest request = requestBuilder.build();

        List<T> allItems = new ArrayList<>();
        Map<String, KeysAndAttributes> allUnprocessed = new HashMap<>();

        try {
            for (var page : enhancedClient.batchGetItem(request)) {
                collectPageResults(page, allItems, allUnprocessed);
            }
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), table.tableName(), e);
        }

        return new BatchGetResult<>(allItems, allUnprocessed);
    }

    private ReadBatch.Builder<T> buildReadBatch() {
        Class<T> itemClass = table.tableSchema().itemType().rawClass();
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);
        if (consistentRead != null && consistentRead) {
            for (Key key : keys) {
                batchBuilder.addGetItem(b -> b.key(key).consistentRead(true));
            }
        } else {
            for (Key key : keys) {
                batchBuilder.addGetItem(key);
            }
        }
        return batchBuilder;
    }

    private void collectPageResults(BatchGetResultPage page, List<T> allItems, Map<String, KeysAndAttributes> allUnprocessed) {
        List<T> pageItems = page.resultsForTable(table);
        if (pageItems != null) {
            allItems.addAll(pageItems);
        }
        List<Key> unprocessedKeys = page.unprocessedKeysForTable(table);
        if (unprocessedKeys == null || unprocessedKeys.isEmpty()) {
            return;
        }
        List<Map<String, AttributeValue>> keyMaps = new ArrayList<>(unprocessedKeys.size());
        for (Key key : unprocessedKeys) {
            keyMaps.add(key.primaryKeyMap(table.tableSchema()));
        }
        KeysAndAttributes.Builder kaBuilder = KeysAndAttributes.builder()
                .keys(keyMaps);
        if (consistentRead != null) {
            kaBuilder.consistentRead(consistentRead);
        }
        allUnprocessed.put(table.tableName(), kaBuilder.build());
    }

    private BatchGetResult<T> retryLoop(BatchGetResult<T> initialResult) {
        List<T> allItems = new ArrayList<>(initialResult.items());
        Map<String, KeysAndAttributes> unprocessed = initialResult.unprocessedKeys();

        int attempt = 0;
        while (unprocessed != null && !unprocessed.isEmpty()) {
            if (attempt >= DynamoDbLimits.MAX_RETRIES) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("BatchGet on table '{}' exhausted retries, {} unprocessed keys remain",
                            table.tableName(), unprocessed.size());
                }
                return new BatchGetResult<>(allItems, unprocessed);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("BatchGet on table '{}' has {} unprocessed keys, retrying (attempt {}/{})",
                        table.tableName(), unprocessed.size(), attempt + 1, DynamoDbLimits.MAX_RETRIES);
            }

            if (sleepWithBackoff(attempt)) {
                return new BatchGetResult<>(allItems, unprocessed);
            }

            unprocessed = attemptRetry(allItems, unprocessed);
            attempt++;
        }

        return new BatchGetResult<>(allItems, Map.of());
    }

    private Map<String, KeysAndAttributes> attemptRetry(
            List<T> allItems, Map<String, KeysAndAttributes> unprocessed) {
        String tableName = table.tableName();
        BatchGetItemRequest.Builder retryRequestBuilder = BatchGetItemRequest.builder()
                .requestItems(unprocessed);
        if (returnConsumedCapacity != null) {
            retryRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        BatchGetItemRequest retryRequest = retryRequestBuilder.build();

        try {
            var response = dynamoDbClient.batchGetItem(retryRequest);
            List<Map<String, AttributeValue>> retryItems = response.responses()
                    .getOrDefault(tableName, List.of());
            for (Map<String, AttributeValue> item : retryItems) {
                allItems.add(table.tableSchema().mapToItem(item));
            }
            return response.unprocessedKeys();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), table.tableName(), e);
        }
    }

    private boolean sleepWithBackoff(int attempt) {
        return RetryUtils.sleepWithBackoff(attempt, DynamoDbLimits.BASE_BACKOFF_MS);
    }

    private BatchGetResult<T> executeWithProjection() {
        String tblName = table.tableName();
        KeysAndAttributes keysAndAttributes = buildKeysAndAttributes(table.tableSchema());

        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        requestItems.put(tblName, keysAndAttributes);

        BatchGetItemRequest.Builder requestBuilder = BatchGetItemRequest.builder()
                .requestItems(requestItems);
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        BatchGetItemRequest request = requestBuilder.build();

        List<Map<String, AttributeValue>> items;
        Map<String, KeysAndAttributes> unprocessed;
        try {
            var response = dynamoDbClient.batchGetItem(request);
            items = response.responses()
                    .getOrDefault(tblName, List.of());
            unprocessed = response.unprocessedKeys();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), tblName, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.BATCH_GET_ITEM.getOperationName(), tblName, e);
        }
        List<T> results = new ArrayList<>(items.size());
        for (Map<String, AttributeValue> item : items) {
            results.add(table.tableSchema().mapToItem(item));
        }
        return new BatchGetResult<>(results, unprocessed != null ? unprocessed : Map.of());
    }

    private void applyReturnConsumedCapacityIfNeeded(BatchGetItemEnhancedRequest.Builder builder) {
        if (returnConsumedCapacity != null) {
            builder.returnConsumedCapacity(returnConsumedCapacity);
        }
    }


}
