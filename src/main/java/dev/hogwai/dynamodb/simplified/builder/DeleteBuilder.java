package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fluent builder for deleting an item from a DynamoDB table by its key,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class DeleteBuilder<T> {
    private static final Logger LOG = Logging.getLogger(DeleteBuilder.class);

    private final DynamoDbTable<T> table;
    private final Object partitionKey;
    private final Object sortKey;
    private final DynamoDbClient dynamoDbClient;
    private ConditionExpression conditionExpression;
    private ReturnValue returnValues;

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
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    public @NonNull DeleteBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return this;
    }

    /**
     * Configures a condition expression that gates the delete operation.
     * DynamoDB evaluates this condition <b>before</b> deleting the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    public @NonNull DeleteBuilder<T> condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return this;
    }

    /**
     * Adds a condition that the specified attribute must exist on the item
     * for the deletion to succeed.
     *
     * @param attribute the attribute name to check for existence
     * @return this builder for chaining
     */
    public @NonNull DeleteBuilder<T> onlyIfExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().exists(attribute).build();
        return this;
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
    public @NonNull DeleteBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        if (returnValues != ReturnValue.NONE && dynamoDbClient == null) {
            throw new IllegalStateException(
                    "Return values require a low-level DynamoDbClient. " +
                    "Use the 4-argument constructor or provide a non-null client.");
        }
        this.returnValues = returnValues;
        return this;
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

        DeleteItemEnhancedRequest.Builder requestBuilder =
                DeleteItemEnhancedRequest.builder().key(k -> {
                    k.partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
                    }
                });
        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        try {
            T result = table.deleteItem(requestBuilder.build());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Delete on table '{}' completed in {}ms",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return Optional.ofNullable(result);
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        }
    }

    // ---- Low-level path for return values ----

    private @Nullable T executeWithReturnValues() {
        String pkName = table.tableSchema().tableMetadata().primaryPartitionKey();
        String skName = table.tableSchema().tableMetadata().primarySortKey().orElse(null);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put(pkName, AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (skName != null && sortKey != null) {
            key.put(skName, AttributeValueConverter.toKeyAttributeValue(sortKey));
        }

        DeleteItemRequest.Builder requestBuilder = DeleteItemRequest.builder()
                .tableName(table.tableName())
                .key(key)
                .returnValues(returnValues);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(conditionExpression.getExpressionNames())
                    .expressionAttributeValues(conditionExpression.getExpressionValues());
        }

        try {
            DeleteItemResponse response = dynamoDbClient.deleteItem(requestBuilder.build());
            if (response.attributes() == null || response.attributes().isEmpty()) {
                return null;
            }
            return table.tableSchema().mapToItem(response.attributes());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.DELETE_ITEM.getOperationName(), table.tableName(), e);
        }
    }


}
