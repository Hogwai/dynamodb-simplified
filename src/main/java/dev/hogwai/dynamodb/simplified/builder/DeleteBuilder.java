package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractDeleteBuilder;
import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;

/**
 * A fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class DeleteBuilder<T> extends AbstractDeleteBuilder<T, DeleteBuilder<T>> {

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code DeleteBuilder} for the given table and key.
     *
     * @param table          the DynamoDB table
     * @param partitionKey   the partition key value
     * @param sortKey        the sort key value (may be {@code null} if the table has no sort key)
     * @param dynamoDbClient the low-level DynamoDB client (required for returnValues support)
     */
    public DeleteBuilder(@NonNull DynamoDbTable<T> table, @NonNull Object partitionKey,
                         @Nullable Object sortKey, @Nullable DynamoDbClient dynamoDbClient) {
        super(partitionKey, sortKey);
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected DeleteBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    /**
     * Configures the return values for the delete operation.
     * When set (e.g., {@link ReturnValue#ALL_OLD}), the operation falls back to the
     * low-level {@link DynamoDbClient#deleteItem(DeleteItemRequest)} to include the
     * return values in the request.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    @Override
    public @NonNull DeleteBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        if (returnValues != ReturnValue.NONE && dynamoDbClient == null) {
            throw new IllegalStateException(
                    "Return values require a low-level DynamoDbClient. " +
                    "Use the 4-argument constructor or provide a non-null client.");
        }
        return super.returnValues(returnValues);
    }

    /**
     * Executes the delete operation and returns the deleted item.
     *
     * @return the deleted item, or empty if no item matched the key
     */
    @NonNull
    public Optional<T> execute() {
        long start = System.nanoTime();
        if (returnValues != null) {
            T result = executeWithReturnValues();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Delete on table '{}' completed in {}ms (with return values)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return Optional.ofNullable(result);
        }

        try {
            T result = table.deleteItem(buildEnhancedRequest());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Delete on table '{}' completed in {}ms",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return Optional.ofNullable(result);
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        }
    }

    // region Low-level path for return values

    private @Nullable T executeWithReturnValues() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);

        Map<String, AttributeValue> key = buildKeyMap(pkName, skName);
        DeleteItemRequest request = buildLowLevelRequest(key);

        try {
            DeleteItemResponse response = dynamoDbClient.deleteItem(request);
            if (response.attributes() == null || response.attributes().isEmpty()) {
                return null;
            }
            return table.tableSchema().mapToItem(response.attributes());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        }
    }

}
// endregion
