package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Versioned;
import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.VersionHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

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
    private boolean optimisticLocking;

    /**
     * Constructs a new {@code PutBuilder} for the given table and item.
     *
     * @param table          the DynamoDB table
     * @param item           the item to put
     * @param dynamoDbClient the low-level DynamoDB client (required for return values)
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
     * Enables optimistic locking for this put operation.
     * <p>
     * When enabled, the library adds a condition expression checking that the
     * version attribute hasn't changed, and increments the version on success.
     * The item must implement {@link dev.hogwai.dynamodb.simplified.Versioned}.
     *
     * @return this builder for chaining
     */
    public @NonNull PutBuilder<T> withOptimisticLocking() {
        this.optimisticLocking = true;
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

    // ---- Optimistic locking ----

    private void applyOptimisticLocking() {
        if (!optimisticLocking) {
            return;
        }
        ConditionExpression versionCondition = VersionHelper.buildCondition(item);
        if (versionCondition == null) {
            return;
        }

        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            conditionExpression = ConditionExpression.builder()
                    .group(conditionExpression)
                    .and()
                    .group(versionCondition)
                    .build();
        } else {
            conditionExpression = versionCondition;
        }
    }

    private void incrementVersion() {
        if (optimisticLocking && item instanceof Versioned v) {
            VersionHelper.incrementVersion(v);
        }
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
