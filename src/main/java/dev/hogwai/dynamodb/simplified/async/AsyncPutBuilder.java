package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractPutBuilder;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Async fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncPutBuilder<T> extends AbstractPutBuilder<T, AsyncPutBuilder<T>> {

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    AsyncPutBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull T item,
                    @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(item);
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncPutBuilder<T> self() {
        return this;
    }

    /**
     * Executes the put operation.
     *
     * @return a {@link CompletableFuture} that completes when the item has been put
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        applyOptimisticLocking();
        long start = System.nanoTime();
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(ignored -> {
                        incrementVersion();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncPut on table '{}' completed in {}ms (with return values)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return null;
                    });
        }
        return table.putItem(buildEnhancedRequest())
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName()))
                .thenRun(() -> {
                    incrementVersion();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncPut on table '{}' completed in {}ms",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                });
    }

    /**
     * Executes the put operation and returns the previous item if
     * {@link #returnValues(ReturnValue)} was set to {@code ALL_OLD}.
     *
     * @return a CompletableFuture containing the previous item, or empty
     */
    @NonNull
    public CompletableFuture<Optional<T>> executeReturning() {
        applyOptimisticLocking();
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(result -> {
                        incrementVersion();
                        return Optional.ofNullable(result);
                    });
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    // region Low-level put with return values

    private CompletableFuture<T> executeWithReturnValues() {
        Map<String, AttributeValue> itemMap = table.tableSchema().itemToMap(item, false);
        PutItemRequest request = buildLowLevelRequest(itemMap);
        return dynamoDbAsyncClient.putItem(request)
                .thenApply(response -> {
                    if (response.attributes() == null || response.attributes().isEmpty()) {
                        return null;
                    }
                    return table.tableSchema().mapToItem(response.attributes());
                })
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName()));
    }
}
// endregion
