package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractUpdateBuilder;
import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A fluent builder for updating an item in a DynamoDB table.
 * Supports both full-item replacement and partial updates via an
 * {@link dev.hogwai.dynamodb.simplified.expression.UpdateExpression}.
 * Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class UpdateBuilder<T> extends AbstractUpdateBuilder<T, UpdateBuilder<T>> {

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code UpdateBuilder} for the given table and item.
     *
     * @param table          the DynamoDB table
     * @param item           the item to update (full replacement data, or
     *                       a template object when used with {@link #update(java.util.function.Consumer)})
     * @param dynamoDbClient the low-level DynamoDB client (required for partial updates)
     */
    public UpdateBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item, @NonNull DynamoDbClient dynamoDbClient) {
        super(item);
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Key-only constructor for use from {@link Table} convenience methods.
     * Skips the item parameter and builds the key map directly from partition
     * (and optionally sort) key values.
     *
     * @param table          the DynamoDB table
     * @param dynamoDbClient the low-level DynamoDB client (required for partial updates)
     * @param partitionKey   the partition key value
     * @param sortKey        the sort key value, or {@code null} if the table has no sort key
     */
    public UpdateBuilder(@NonNull DynamoDbTable<T> table, @NonNull DynamoDbClient dynamoDbClient,
                         @NonNull Object partitionKey, @Nullable Object sortKey) {
        super();
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
        buildKeyMapFromValues(partitionKey, sortKey);
    }

    @Override
    protected @NonNull UpdateBuilder<T> self() {
        return this;
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    @Override
    protected @NonNull Key getKeyFromItem() {
        return table.keyFrom(item);
    }

    @Override
    protected @NonNull TableSchema<T> getTableSchema() {
        return table.tableSchema();
    }

    /**
     * Executes the update operation. If a partial update expression was configured
     * via {@link #update(java.util.function.Consumer)}, a targeted partial update is
     * performed via the low-level DynamoDB client. Otherwise, the full item is replaced.
     *
     * @return the updated item, or empty if not found
     */
    @NonNull
    public Optional<T> execute() {
        if (!ignoreNulls && updateExpression != null && !updateExpression.isEmpty()) {
            throw new IllegalStateException(
                    "ignoreNulls(false) has no effect when using partial updates via update(Consumer). "
                            + "Remove the ignoreNulls() call or use a full-item update instead.");
        }
        boolean hasExpression = hasUpdateExpression();
        applyOptimisticLocking();
        long start = System.nanoTime();
        if (hasExpression) {
            T result = executeWithExpression();
            incrementVersion();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Update on table '{}' completed in {}ms (expression)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return Optional.ofNullable(result);
        }
        T result = executeWithFullItem();
        incrementVersion();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update on table '{}' completed in {}ms (full item)",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
        }
        return Optional.ofNullable(result);
    }

    private boolean hasUpdateExpression() {
        boolean hasExpression = updateExpression != null && !updateExpression.isEmpty();
        if (returnValues != null && !hasExpression) {
            throw new IllegalStateException(
                    "ReturnValues is not supported for full-item replacement. "
                            + "Use partial updates with update(expr -> ...) to configure ReturnValues.");
        }
        if (item == null && !hasExpression) {
            throw new IllegalStateException(
                    "Key-only update requires a partial update expression. "
                            + "Use update(expr -> ...) to configure an update expression.");
        }
        return hasExpression;
    }

    // region Full item replacement (existing behavior)

    @SuppressWarnings("unchecked")
    private T executeWithFullItem() {
        UpdateItemEnhancedRequest.Builder<T> requestBuilder =
                UpdateItemEnhancedRequest.builder((Class<T>) item.getClass())
                        .item(item)
                        .ignoreNullsMode(ignoreNulls ? IgnoreNullsMode.SCALAR_ONLY : IgnoreNullsMode.DEFAULT);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        try {
            return table.updateItem(requestBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.UPDATE_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.UPDATE_ITEM.getOperationName(), table.tableName(), e);
        }
    }

    // endregion

    // region Partial update via low-level client

    private T executeWithExpression() {
        Map<String, AttributeValue> expressionKeyMap = buildKeyMap();

        Map<String, String> allNames;
        Map<String, AttributeValue> allValues;

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            allNames = new HashMap<>(updateExpression.getExpressionNames());
            allNames.putAll(conditionExpression.getExpressionNames());
            allValues = new HashMap<>(updateExpression.getExpressionValues());
            allValues.putAll(conditionExpression.getExpressionValues());
        } else {
            allNames = updateExpression.getExpressionNames();
            allValues = updateExpression.getExpressionValues();
        }

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName(table.tableName())
                .key(expressionKeyMap)
                .updateExpression(updateExpression.getExpression())
                .expressionAttributeNames(allNames)
                .expressionAttributeValues(allValues)
                .returnValues(returnValues != null ? returnValues : ReturnValue.ALL_NEW);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(conditionExpression.getExpression());
        }

        Map<String, AttributeValue> result;
        try {
            result = dynamoDbClient.updateItem(requestBuilder.build()).attributes();
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.UPDATE_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.UPDATE_ITEM.getOperationName(), table.tableName(), e);
        }

        if (result == null || result.isEmpty()) {
            return null;
        }
        return table.tableSchema().mapToItem(result);
    }

}
// endregion
