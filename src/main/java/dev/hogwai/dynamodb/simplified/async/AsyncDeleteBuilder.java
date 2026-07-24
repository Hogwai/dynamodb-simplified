package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractDeleteBuilder;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Async fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class AsyncDeleteBuilder<T> extends AbstractDeleteBuilder<T, AsyncDeleteBuilder<T>> {

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    AsyncDeleteBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                       @Nullable Object sortKey, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(partitionKey, sortKey);
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected AsyncDeleteBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    /**
     * Executes the delete operation and returns the deleted item.
     *
     * @return a {@link CompletableFuture} containing the deleted item, or empty
     */
    @NonNull
    public CompletableFuture<Optional<T>> execute() {
        long start = System.nanoTime();
        if (returnValues != null) {
            return executeWithReturnValues()
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncDelete on table '{}' completed in {}ms (with return values)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return Optional.ofNullable(result);
                    });
        }

        return table.deleteItem(buildEnhancedRequest())
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName()))
                .thenApply(result -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncDelete on table '{}' completed in {}ms",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return Optional.ofNullable(result);
                });
    }

    // region Low-level path for return values

    private CompletableFuture<T> executeWithReturnValues() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);

        Map<String, AttributeValue> key = buildKeyMap(pkName, skName);
        DeleteItemRequest request = buildLowLevelRequest(key);

        return dynamoDbAsyncClient.deleteItem(request)
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName()))
                .thenApply(response -> {
                    if (response.attributes() == null || response.attributes().isEmpty()) {
                        return null;
                    }
                    return table.tableSchema().mapToItem(response.attributes());
                });
    }

}
// endregion
