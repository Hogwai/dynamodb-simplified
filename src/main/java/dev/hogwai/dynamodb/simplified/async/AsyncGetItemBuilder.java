package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async fluent builder for getting an item by key from a DynamoDB table or index.
 * Supports optional projection expressions and consistent read settings.
 *
 * @param <T> the item type
 */
public class AsyncGetItemBuilder<T> {
    private static final Logger LOG = Logging.getLogger(AsyncGetItemBuilder.class);

    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final Object partitionKey;
    @Nullable
    private final Object sortKey;
    private ProjectionExpression projectionExpression;
    private boolean consistentRead = false;

    AsyncGetItemBuilder(@NonNull DynamoDbAsyncTable<T> table, @NonNull Object partitionKey,
                        @Nullable Object sortKey, @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    @NonNull
    public AsyncGetItemBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncGetItemBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    @NonNull
    public AsyncGetItemBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
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
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(k -> {
                    k.partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
                    }
                })
                .consistentRead(consistentRead)
                .build();
        return table.getItem(request)
                .thenApply(Optional::ofNullable)
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName()));
    }

    private CompletableFuture<Optional<T>> executeWithProjection() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);

        Map<String, AttributeValue> keyMap = new HashMap<>();
        keyMap.put(pkName, AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (skName != null && sortKey != null) {
            keyMap.put(skName, AttributeValueConverter.toKeyAttributeValue(sortKey));
        }

        GetItemRequest request = GetItemRequest.builder()
                .tableName(table.tableName())
                .key(keyMap)
                .projectionExpression(projectionExpression.getExpression())
                .expressionAttributeNames(projectionExpression.getExpressionNames())
                .consistentRead(consistentRead)
                .build();

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
