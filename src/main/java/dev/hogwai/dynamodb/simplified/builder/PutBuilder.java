package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractPutBuilder;
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
 * A fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class PutBuilder<T> extends AbstractPutBuilder<T, PutBuilder<T>> {

    private final DynamoDbTable<T> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code PutBuilder} for the given table and item.
     *
     * @param table          the DynamoDB table
     * @param item           the item to put
     * @param dynamoDbClient the low-level DynamoDB client (required for return values)
     */
    public PutBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item,
                      @NonNull DynamoDbClient dynamoDbClient) {
        super(item);
        this.table = table;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull PutBuilder<T> self() {
        return this;
    }

    /**
     * Executes the put operation.
     */
    public void execute() {
        long start = System.nanoTime();
        applyOptimisticLocking();
        if (returnValues != null) {
            executeWithReturnValues(); // ignore return value for void execute()
            if (LOG.isDebugEnabled()) {
                LOG.debug("Put on table '{}' completed in {}ms (with return values)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            incrementVersion();
            return;
        }
        try {
            table.putItem(buildEnhancedRequest());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName(), e);
        }
        incrementVersion();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Put on table '{}' completed in {}ms",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    /**
     * Executes the put operation and returns the previous item if
     * {@link #returnValues(ReturnValue)} was set to {@code ALL_OLD}.
     * <p>
     * If no return value was configured, returns {@link Optional#empty()}.
     *
     * @return the previous item (if ReturnValue.ALL_OLD was set), or empty if
     * the item didn't previously exist or no return value was configured
     */
    @NonNull
    public Optional<T> executeReturning() {
        applyOptimisticLocking();
        if (returnValues != null) {
            T result = executeWithReturnValues();
            incrementVersion();
            return Optional.ofNullable(result);
        }
        return Optional.empty();
    }

    @Override
    protected @NonNull String tableName() {
        return table.tableName();
    }

    // region Low-level put with return values

    private @Nullable T executeWithReturnValues() {
        Map<String, AttributeValue> itemMap = table.tableSchema().itemToMap(item, false);
        PutItemRequest request = buildLowLevelRequest(itemMap);
        try {
            PutItemResponse response = dynamoDbClient.putItem(request);
            if (response.attributes() == null || response.attributes().isEmpty()) {
                return null;
            }
            return table.tableSchema().mapToItem(response.attributes());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName(), e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.PUT_ITEM.getOperationName(), table.tableName(), e);
        }
    }
}
// endregion
