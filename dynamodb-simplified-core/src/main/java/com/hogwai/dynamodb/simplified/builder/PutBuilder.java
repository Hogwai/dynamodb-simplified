package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.internal.Logging;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fluent builder for putting (inserting or replacing) an item in a DynamoDB table,
 * with an optional condition expression. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class PutBuilder<T> {
    private static final Logger LOG = Logging.getLogger(PutBuilder.class);

    private final DynamoDbTable<T> table;
    private final T item;
    private final DynamoDbClient dynamoDbClient;
    private ConditionExpression conditionExpression;
    private ReturnValue returnValues;

    /**
     * Constructs a new {@code PutBuilder} for the given table and item.
     *
     * @param table           the DynamoDB table
     * @param item            the item to put
     * @param dynamoDbClient  the low-level DynamoDB client (required for return values)
     */
    public PutBuilder(@NonNull DynamoDbTable<T> table, @NonNull T item,
                      @NonNull DynamoDbClient dynamoDbClient) {
        this.table = table;
        this.item = item;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param configurator a consumer to build the condition expression
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> condition(@NonNull Consumer<ConditionExpression.Builder> configurator) {
        var builder = ConditionExpression.builder();
        configurator.accept(builder);
        this.conditionExpression = builder.build();
        return this;
    }

    /**
     * Configures a condition expression that gates the put operation.
     * DynamoDB evaluates this condition <b>before</b> writing the item
     * (unlike a filter expression which applies after reading).
     *
     * @param condition the condition expression
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> condition(@Nullable ConditionExpression condition) {
        this.conditionExpression = condition;
        return this;
    }

    /**
     * Adds a condition that the specified attribute must not already exist
     * on an item with the same key for the put to succeed.
     *
     * @param attribute the attribute name to check for absence
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> onlyIfNotExists(@NonNull String attribute) {
        this.conditionExpression = ConditionExpression.builder().notExists(attribute).build();
        return this;
    }

    /**
     * Configures which item attributes to return after the put.
     * <p>
     * When set, uses the low-level {@code PutItemRequest} with the specified
     * {@code ReturnValues}. Common values: {@link ReturnValue#ALL_OLD}
     * (returns the previous item if one existed), {@link ReturnValue#NONE}.
     * <p>
     * To retrieve the previous item when using {@code ReturnValue#ALL_OLD},
     * use {@link #executeReturning()} instead of {@link #execute()}.
     *
     * @param returnValues the return value setting, or {@code null} to use the default
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> returnValues(@Nullable ReturnValue returnValues) {
        this.returnValues = returnValues;
        return this;
    }

    /**
     * Executes the put operation.
     */
    public void execute() {
        long start = System.nanoTime();
        if (returnValues != null) {
            executeWithReturnValues(); // ignore return value for void execute()
            if (LOG.isDebugEnabled()) {
                LOG.debug("Put on table '{}' completed in {}ms (with return values)",
                        table.tableName(), (System.nanoTime() - start) / 1_000_000);
            }
            return;
        }
        @SuppressWarnings("unchecked")
        PutItemEnhancedRequest.Builder<T> requestBuilder = PutItemEnhancedRequest
                .builder((Class<T>) item.getClass())
                .item(item);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder.conditionExpression(
                    conditionExpression.toSdkExpression()
            );
        }

        try {
            table.putItem(requestBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("PutItem", table.tableName(), e);
        }
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
     *         the item didn't previously exist or no return value was configured
     */
    @NonNull
    public Optional<T> executeReturning() {
        if (returnValues != null) {
            return Optional.ofNullable(executeWithReturnValues());
        }
        return Optional.empty();
    }

    // ---- Low-level put with return values ----

    private @Nullable T executeWithReturnValues() {
        Map<String, AttributeValue> itemMap = table.tableSchema().itemToMap(item, false);

        PutItemRequest.Builder requestBuilder = PutItemRequest.builder()
                .tableName(table.tableName())
                .item(itemMap)
                .returnValues(returnValues);

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            requestBuilder
                    .conditionExpression(conditionExpression.getExpression())
                    .expressionAttributeNames(conditionExpression.getExpressionNames())
                    .expressionAttributeValues(conditionExpression.getExpressionValues());
        }

        try {
            PutItemResponse response = dynamoDbClient.putItem(requestBuilder.build());
            if (response.attributes() == null || response.attributes().isEmpty()) {
                return null;
            }
            return table.tableSchema().mapToItem(response.attributes());
        } catch (ConditionalCheckFailedException e) {
            throw ConditionFailedException.fromSdk(e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("PutItem", table.tableName(), e);
        }
    }
}
