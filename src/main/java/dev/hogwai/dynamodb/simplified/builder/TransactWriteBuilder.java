package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.builder.base.AbstractTransactWriteBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.exception.TransactionFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.expression.UpdateExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.Messages;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds a transactional write operation that groups up to 100 put, update, delete,
 * and condition check actions atomically across one or more tables.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactWrite()}.
 * <p>
 * All actions succeed or none are applied. If any condition fails or a conflict occurs,
 * the entire transaction is rejected.
 */
public class TransactWriteBuilder extends AbstractTransactWriteBuilder<TransactWriteBuilder> {

    private static final Logger LOG = Logging.getLogger(TransactWriteBuilder.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a transactional write builder.
     * <p>
     * Typically obtained via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactWrite()}.
     *
     * @param enhancedClient the enhanced DynamoDB client
     * @param dynamoDbClient the low-level DynamoDB client (used for expression-based update fallback)
     */
    public TransactWriteBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull TransactWriteBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Operation operation) {
        return ((DynamoDbTable<?>) operation.table()).tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Operation operation) {
        return ((DynamoDbTable<?>) operation.table()).tableSchema();
    }

    @Override
    protected @NonNull Key keyFromItem(@NonNull Operation operation, @NonNull Object item) {
        return ((DynamoDbTable<Object>) operation.table()).keyFrom(item);
    }

    /**
     * Adds a put action to insert or replace an item.
     *
     * @param table the typed table to write to
     * @param item  the item to put
     * @param <T>   the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder put(@NonNull Table<T> table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.PUT, table.getRawTable(),
                Objects.requireNonNull(item), null, null, null, null));
        return this;
    }

    /**
     * Adds an update action to modify an existing item.
     *
     * @param table the typed table to update
     * @param item  the item with updated values
     * @param <T>   the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder update(@NonNull Table<T> table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.UPDATE, table.getRawTable(),
                Objects.requireNonNull(item), null, null, null, null));
        return this;
    }

    /**
     * Adds an update action with a partial update expression for fine-grained
     * attribute modifications.
     * <p>
     * Use the {@code expressionConfigurator} to specify SET, REMOVE, ADD, and/or
     * DELETE clauses. When an expression-based update is included in the transaction,
     * the entire transaction is sent via the low-level DynamoDB API rather than the
     * enhanced client.
     *
     * @param table                  the typed table to update
     * @param item                   the item providing the key identifying the item to update
     * @param expressionConfigurator a consumer to build the update expression
     * @param <T>                    the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder update(@NonNull Table<T> table, @NonNull T item,
                                                    @NonNull Consumer<UpdateExpression> expressionConfigurator) {
        Objects.requireNonNull(expressionConfigurator);
        UpdateExpression expression = UpdateExpression.builder();
        expressionConfigurator.accept(expression);
        operations.add(new Operation(
                Operation.Type.UPDATE_WITH_EXPRESSION, table.getRawTable(),
                Objects.requireNonNull(item), null, null, null, expression));
        return this;
    }

    /**
     * Adds a delete action by partition key.
     *
     * @param table        the typed table to delete from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder delete(@NonNull Table<T> table, @NonNull Object partitionKey) {
        operations.add(new Operation(Operation.Type.DELETE, table.getRawTable(),
                null, partitionKey, null, null, null));
        return this;
    }

    /**
     * Adds a delete action by partition and sort key.
     *
     * @param table        the typed table to delete from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder delete(@NonNull Table<T> table, @NonNull Object partitionKey, @NonNull Object sortKey) {
        operations.add(new Operation(Operation.Type.DELETE, table.getRawTable(),
                null, partitionKey, sortKey, null, null));
        return this;
    }

    /**
     * Adds a condition check action that verifies a condition on an item without modifying it.
     * <p>
     * If the condition is not satisfied, the entire transaction is rejected.
     *
     * @param table        the typed table to check
     * @param partitionKey the partition key value
     * @param condition    a consumer to build the condition expression (e.g. {@code expr -> expr.eq("status", "active")})
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder conditionCheck(@NonNull Table<T> table, @NonNull Object partitionKey,
                                                            @NonNull Consumer<ConditionExpression.Builder> condition) {
        return conditionCheck(table, partitionKey, null, condition);
    }

    /**
     * Adds a condition check action with sort key.
     *
     * @param table        the typed table to check
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param condition    a consumer to build the condition expression
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactWriteBuilder conditionCheck(@NonNull Table<T> table, @NonNull Object partitionKey,
                                                            @Nullable Object sortKey,
                                                            @NonNull Consumer<ConditionExpression.Builder> condition) {
        var builder = ConditionExpression.builder();
        condition.accept(builder);
        ConditionExpression conditionExpression = builder.build();
        operations.add(new Operation(Operation.Type.CONDITION_CHECK, table.getRawTable(),
                null, partitionKey, sortKey, conditionExpression, null));
        return this;
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public TransactWriteBuilder returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Executes the transactional write operation.
     * <p>
     * All actions are applied atomically. If any action fails,
     * a {@link software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException}
     * is thrown.
     * <p>
     * If any operation in the transaction uses an {@link UpdateExpression},
     * the entire transaction is sent via the low-level DynamoDB API.
     * Otherwise, the enhanced DynamoDB client is used.
     */
    public void execute() {
        long start = System.nanoTime();
        if (hasExpressionOperation()) {
            executeLowLevel();
            if (LOG.isDebugEnabled()) {
                LOG.debug("TransactWrite (low-level) completed in {}ms ({} operations)",
                        (System.nanoTime() - start) / 1_000_000, operations.size());
            }
        } else {
            executeEnhanced();
            if (LOG.isDebugEnabled()) {
                LOG.debug("TransactWrite (enhanced) completed in {}ms ({} operations)",
                        (System.nanoTime() - start) / 1_000_000, operations.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeEnhanced() {
        var requestBuilder = TransactWriteItemsEnhancedRequest.builder();
        for (Operation op : operations) {
            switch (op.type()) {
                case PUT -> {
                    Objects.requireNonNull(op.item(), "item must not be null for PUT");
                    requestBuilder.addPutItem(
                            (DynamoDbTable<Object>) op.table(), op.item());
                }
                case UPDATE -> {
                    Objects.requireNonNull(op.item(), "item must not be null for UPDATE");
                    requestBuilder.addUpdateItem(
                            (DynamoDbTable<Object>) op.table(), op.item());
                }
                case DELETE -> {
                    Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
                    requestBuilder.addDeleteItem((DynamoDbTable<?>) op.table(), key);
                }
                case CONDITION_CHECK -> {
                    ConditionExpression condExpr = Objects.requireNonNull(op.conditionExpression(),
                            "conditionExpression must not be null for CONDITION_CHECK");
                    Expression expression = condExpr.toSdkExpression();
                    requestBuilder.addConditionCheck(
                            (DynamoDbTable<Object>) op.table(),
                            cb -> cb.key(k -> {
                                k.partitionValue(AttributeValueConverter.toKeyAttributeValue(op.partitionKey()));
                                if (op.sortKey() != null) {
                                    k.sortValue(AttributeValueConverter.toKeyAttributeValue(op.sortKey()));
                                }
                            }).conditionExpression(expression));
                }
                default -> throw new IllegalStateException(Messages.UNEXPECTED_OPERATION_TYPE_FMT.formatted(op.type()));
            }
        }
        try {
            enhancedClient.transactWriteItems(requestBuilder.build());
        } catch (TransactionCanceledException e) {
            throw new TransactionFailedException(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
        }
    }

    private void executeLowLevel() {
        var items = new ArrayList<TransactWriteItem>(operations.size());
        for (Operation op : operations) {
            items.add(buildTransactWriteItem(op));
        }
        var requestBuilder = TransactWriteItemsRequest.builder()
                .transactItems(items);
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        try {
            dynamoDbClient.transactWriteItems(requestBuilder.build());
        } catch (TransactionCanceledException e) {
            throw new TransactionFailedException(e);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
        }
    }
}
