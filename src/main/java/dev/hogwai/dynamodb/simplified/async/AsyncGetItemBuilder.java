package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractGetItemBuilder;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Async fluent builder for getting an item by key from a DynamoDB table or index.
 * Supports optional projection expressions and consistent read settings.
 *
 * @param <T> the item type
 */
public class AsyncGetItemBuilder<T> extends AbstractGetItemBuilder<T, AsyncGetItemBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(AsyncGetItemBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    AsyncGetItemBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                        @Nullable Object sortKey, @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(partitionKey, sortKey);
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected AsyncGetItemBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    /**
     * Executes the get operation and returns the item, if found.
     *
     * @return a {@link CompletableFuture} containing an {@link Optional} with the item,
     * or empty if no item exists with the specified key
     */
    @NonNull
    public CompletableFuture<Optional<T>> execute() {
        long start = System.nanoTime();
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            return executeWithProjection()
                    .thenApply(result -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("AsyncGetItem on table '{}' completed in {}ms (with projection)",
                                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
                        }
                        return result;
                    });
        }
        return executeSimple()
                .thenApply(result -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncGetItem on table '{}' completed in {}ms",
                                table.tableName(), (System.nanoTime() - start) / 1_000_000);
                    }
                    return result;
                });
    }

    private CompletableFuture<Optional<T>> executeSimple() {
        var request = buildEnhancedRequest();
        return table.getItem(request)
                .thenApply(Optional::ofNullable)
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName()));
    }

    private CompletableFuture<Optional<T>> executeWithProjection() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);
        var keyMap = buildKeyMap(pkName, skName);
        var request = buildLowLevelRequest(keyMap);
        return dynamoDbAsyncClient.getItem(request)
                .thenApply(response -> {
                    if (!response.hasItem()) {
                        return Optional.<T>empty();
                    }
                    return Optional.of(table.tableSchema().mapToItem(response.item()));
                })
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName()));
    }
}
