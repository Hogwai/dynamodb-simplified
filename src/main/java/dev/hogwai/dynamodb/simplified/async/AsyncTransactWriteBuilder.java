package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractTransactWriteBuilder;
import dev.hogwai.dynamodb.simplified.exception.TransactionFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.expression.UpdateExpression;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Builds an async transactional write operation that groups up to 100 put, update, delete,
 * and condition check actions atomically across one or more tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#transactWrite()}. All actions succeed
 * or none are applied. If any condition fails or a conflict occurs, the entire transaction
 * is rejected.
 */
public class AsyncTransactWriteBuilder extends AbstractTransactWriteBuilder<AsyncTransactWriteBuilder> {

    private static final Logger LOG = Logging.getLogger(AsyncTransactWriteBuilder.class);

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncTransactWriteBuilder}.
     *
     * @param enhancedClient      the enhanced async DynamoDB client
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncTransactWriteBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient,
                                     @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncTransactWriteBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Operation operation) {
        return ((DynamoDbAsyncTable<?>) operation.table()).tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Operation operation) {
        return ((DynamoDbAsyncTable<?>) operation.table()).tableSchema();
    }

    @Override
    protected @NonNull Key keyFromItem(@NonNull Operation operation, @NonNull Object item) {
        return ((DynamoDbAsyncTable<Object>) operation.table()).keyFrom(item);
    }

    /**
     * Adds a put action to insert or replace an item.
     *
     * @param table the typed async table to write to
     * @param item  the item to put
     * @param <T>   the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder put(@NonNull AsyncTable<T> table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.PUT, table.getRawTable(),
                Objects.requireNonNull(item), null, null, null, null));
        return this;
    }

    /**
     * Adds an update action to modify an existing item.
     *
     * @param table the typed async table to update
     * @param item  the item with updated values
     * @param <T>   the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder update(@NonNull AsyncTable<T> table, @NonNull T item) {
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
     * @param table                  the typed async table to update
     * @param item                   the item providing the key identifying the item to update
     * @param expressionConfigurator a consumer to build the update expression
     * @param <T>                    the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder update(@NonNull AsyncTable<T> table, @NonNull T item,
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
     * @param table        the typed async table to delete from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder delete(@NonNull AsyncTable<T> table, @NonNull Object partitionKey) {
        operations.add(new Operation(Operation.Type.DELETE, table.getRawTable(),
                null, partitionKey, null, null, null));
        return this;
    }

    /**
     * Adds a delete action by partition and sort key.
     *
     * @param table        the typed async table to delete from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder delete(@NonNull AsyncTable<T> table, @NonNull Object partitionKey, @NonNull Object sortKey) {
        operations.add(new Operation(Operation.Type.DELETE, table.getRawTable(),
                null, partitionKey, sortKey, null, null));
        return this;
    }

    /**
     * Adds a condition check action that verifies a condition on an item without modifying it.
     * <p>
     * If the condition is not satisfied, the entire transaction is rejected.
     *
     * @param table        the typed async table to check
     * @param partitionKey the partition key value
     * @param condition    a consumer to build the condition expression (e.g. {@code expr -> expr.eq("status", "active")})
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder conditionCheck(@NonNull AsyncTable<T> table, @NonNull Object partitionKey,
                                                        @NonNull Consumer<ConditionExpression.Builder> condition) {
        return conditionCheck(table, partitionKey, null, condition);
    }

    /**
     * Adds a condition check action with sort key.
     *
     * @param table        the typed async table to check
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param condition    a consumer to build the condition expression
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactWriteBuilder conditionCheck(@NonNull AsyncTable<T> table, @NonNull Object partitionKey,
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
    public AsyncTransactWriteBuilder returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Executes the transactional write operation asynchronously.
     * <p>
     * All actions are applied atomically. If any action fails,
     * a {@link software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException}
     * is thrown.
     * <p>
     * If any operation in the transaction uses an {@link UpdateExpression},
     * the entire transaction is sent via the low-level DynamoDB API.
     * Otherwise, the enhanced DynamoDB client is used.
     *
     * @return a {@link CompletableFuture} that completes when the transaction has been applied
     */
    @NonNull
    public CompletableFuture<Void> execute() {
        long start = System.nanoTime();
        if (hasExpressionOperation()) {
            return executeLowLevel().thenRun(() -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("AsyncTransactWrite (low-level) completed in {}ms ({} operations)",
                            (System.nanoTime() - start) / 1_000_000, operations.size());
                }
            });
        } else {
            return executeEnhanced().thenRun(() -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("AsyncTransactWrite (enhanced) completed in {}ms ({} operations)",
                            (System.nanoTime() - start) / 1_000_000, operations.size());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private CompletableFuture<Void> executeEnhanced() {
        var requestBuilder = TransactWriteItemsEnhancedRequest.builder();
        for (Operation op : operations) {
            switch (op.type()) {
                case PUT -> {
                    Objects.requireNonNull(op.item(), "item must not be null for PUT");
                    requestBuilder.addPutItem(
                            (DynamoDbAsyncTable<Object>) op.table(), op.item());
                }
                case UPDATE -> {
                    Objects.requireNonNull(op.item(), "item must not be null for UPDATE");
                    requestBuilder.addUpdateItem(
                            (DynamoDbAsyncTable<Object>) op.table(), op.item());
                }
                case DELETE -> {
                    Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
                    requestBuilder.addDeleteItem((DynamoDbAsyncTable<?>) op.table(), key);
                }
                case CONDITION_CHECK -> {
                    ConditionExpression condExpr = Objects.requireNonNull(op.conditionExpression(),
                            "conditionExpression must not be null for CONDITION_CHECK");
                    Expression expression = condExpr.toSdkExpression();
                    requestBuilder.addConditionCheck(
                            (DynamoDbAsyncTable<Object>) op.table(),
                            cb -> cb.key(k -> {
                                k.partitionValue(AttributeValueConverter.toKeyAttributeValue(op.partitionKey()));
                                if (op.sortKey() != null) {
                                    k.sortValue(AttributeValueConverter.toKeyAttributeValue(op.sortKey()));
                                }
                            }).conditionExpression(expression));
                }
                default -> throw new IllegalStateException(
                        "Unexpected operation type: " + op.type());
            }
        }
        return enhancedClient.transactWriteItems(requestBuilder.build())
                .exceptionally(e -> {
                    Throwable cause = e instanceof CompletionException ce ? ce.getCause() : e;
                    if (cause instanceof TransactionCanceledException tce) {
                        throw new TransactionFailedException(tce);
                    }
                    throw AsyncExceptionMapper.mapException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
                });
    }

    @NonNull
    private CompletableFuture<Void> executeLowLevel() {
        var items = new ArrayList<TransactWriteItem>(operations.size());
        for (Operation op : operations) {
            items.add(buildTransactWriteItem(op));
        }
        var requestBuilder = TransactWriteItemsRequest.builder()
                .transactItems(items);
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        return dynamoDbAsyncClient.transactWriteItems(requestBuilder.build())
                .exceptionally(e -> {
                    Throwable cause = e instanceof CompletionException ce ? ce.getCause() : e;
                    if (cause instanceof TransactionCanceledException tce) {
                        throw new TransactionFailedException(tce);
                    }
                    throw AsyncExceptionMapper.mapException(DynamoDbOperations.TRANSACT_WRITE.getOperationName(), null, e);
                }).thenApply(ignored -> null);
    }
}
