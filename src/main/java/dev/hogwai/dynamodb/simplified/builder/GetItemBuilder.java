package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractGetItemBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Optional;

/**
 * A fluent builder for retrieving an item from a DynamoDB table by its key,
 * with optional projection and consistent read settings. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class GetItemBuilder<T> extends AbstractGetItemBuilder<T, GetItemBuilder<T>> {
    private static final Logger LOG = Logging.getLogger(GetItemBuilder.class);

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code GetItemBuilder} for the given table and key.
     *
     * @param table          the DynamoDB table
     * @param partitionKey   the partition key value
     * @param sortKey        the sort key value (may be {@code null} if the table has no sort key)
     * @param dynamoDbClient the low-level DynamoDB client (used for projection fallback)
     */
    public GetItemBuilder(@NonNull DynamoDbTable<T> table, @NonNull Object partitionKey,
                          @Nullable Object sortKey, @NonNull DynamoDbClient dynamoDbClient) {
        super(partitionKey, sortKey);
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected GetItemBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    /**
     * Executes the get operation and returns the item, if found.
     *
     * @return an {@link Optional} containing the item, or empty if no item
     * exists with the specified key
     */
    public @NonNull Optional<T> execute() {
        long start = System.nanoTime();
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            Optional<T> result = executeWithProjection();
            if (LOG.isDebugEnabled()) {
                LOG.debug("GetItem on table '{}' completed in {}ms (with projection)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return result;
        }
        Optional<T> result = executeSimple();
        if (LOG.isDebugEnabled()) {
            LOG.debug("GetItem on table '{}' completed in {}ms",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
        }
        return result;
    }

    private Optional<T> executeSimple() {
        var request = buildEnhancedRequest();
        try {
            return Optional.ofNullable(table.getItem(request));
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName(), e);
        }
    }

    private Optional<T> executeWithProjection() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);
        var keyMap = buildKeyMap(pkName, skName);
        var request = buildLowLevelRequest(keyMap);
        try {
            var response = dynamoDbClient.getItem(request);
            T item = response.hasItem() ? table.tableSchema().mapToItem(response.item()) : null;
            return Optional.ofNullable(item);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.GET_ITEM.getOperationName(), table.tableName(), e);
        }
    }
}
