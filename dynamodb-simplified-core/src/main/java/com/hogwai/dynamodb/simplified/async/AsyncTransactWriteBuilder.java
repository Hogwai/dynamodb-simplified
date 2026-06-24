package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Builds an async transactional write operation that groups up to 100 put, update, delete,
 * and condition check actions atomically across one or more tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#transactWrite()}. All actions succeed
 * or none are applied. If any condition fails or a conflict occurs, the entire transaction
 * is rejected.
 */
public class AsyncTransactWriteBuilder {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final List<Operation> operations = new ArrayList<>();

    private record Operation(
            Type type,
            AsyncTable<?> table,
            @Nullable Object item,
            @Nullable Object partitionKey,
            @Nullable Object sortKey,
            @Nullable ConditionExpression conditionExpression,
            @Nullable UpdateExpression updateExpression
    ) {
        enum Type {
            PUT, UPDATE, DELETE, CONDITION_CHECK, UPDATE_WITH_EXPRESSION
        }
    }

    /**
     * Constructs a new {@code AsyncTransactWriteBuilder}.
     *
     * @param enhancedClient      the enhanced async DynamoDB client
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncTransactWriteBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient,
                                     @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
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
        operations.add(new Operation(Operation.Type.PUT, table, Objects.requireNonNull(item), null, null, null, null));
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
        operations.add(new Operation(Operation.Type.UPDATE, table, Objects.requireNonNull(item), null, null, null, null));
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
                Operation.Type.UPDATE_WITH_EXPRESSION, table, Objects.requireNonNull(item),
                null, null, null, expression));
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
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, null, null, null));
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
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, sortKey, null, null));
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
        operations.add(new Operation(Operation.Type.CONDITION_CHECK, table, null, partitionKey, sortKey, conditionExpression, null));
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
        boolean hasExpressionOperation = operations.stream()
                .anyMatch(op -> op.type() == Operation.Type.UPDATE_WITH_EXPRESSION);

        if (hasExpressionOperation) {
            return executeLowLevel();
        } else {
            return executeEnhanced();
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
                            (DynamoDbAsyncTable<Object>) op.table().getRawTable(), op.item());
                }
                case UPDATE -> {
                    Objects.requireNonNull(op.item(), "item must not be null for UPDATE");
                    requestBuilder.addUpdateItem(
                            (DynamoDbAsyncTable<Object>) op.table().getRawTable(), op.item());
                }
                case DELETE -> {
                    Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
                    requestBuilder.addDeleteItem(op.table().getRawTable(), key);
                }
                case CONDITION_CHECK -> {
                    ConditionExpression condExpr = Objects.requireNonNull(op.conditionExpression(),
                            "conditionExpression must not be null for CONDITION_CHECK");
                    Expression expression = condExpr.toSdkExpression();
                    requestBuilder.addConditionCheck(
                            op.table().getRawTable(),
                            cb -> cb.key(k -> {
                                k.partitionValue(AttributeValueConverter.toKeyAttributeValue(op.partitionKey()));
                                if (op.sortKey() != null) {
                                    k.sortValue(AttributeValueConverter.toKeyAttributeValue(op.sortKey()));
                                }
                            }).conditionExpression(expression));
                }
                default -> throw new IllegalStateException("Unexpected operation type in enhanced path: " + op.type());
            }
        }
        return enhancedClient.transactWriteItems(requestBuilder.build());
    }

    @NonNull
    private CompletableFuture<Void> executeLowLevel() {
        var items = new ArrayList<TransactWriteItem>(operations.size());
        for (Operation op : operations) {
            items.add(buildTransactWriteItem(op));
        }
        return dynamoDbAsyncClient.transactWriteItems(
                        TransactWriteItemsRequest.builder().transactItems(items).build())
                .thenApply(ignored -> null);
    }

    private TransactWriteItem buildTransactWriteItem(Operation op) {
        return switch (op.type()) {
            case PUT, UPDATE -> buildPutItem(op);
            case DELETE -> buildDeleteItem(op);
            case CONDITION_CHECK -> buildConditionCheckItem(op);
            case UPDATE_WITH_EXPRESSION -> buildUpdateWithExpressionItem(op);
        };
    }

    @SuppressWarnings("unchecked")
    private TransactWriteItem buildPutItem(Operation op) {
        Objects.requireNonNull(op.item(), "item must not be null for PUT");
        var rawTable = (DynamoDbAsyncTable<Object>) op.table().getRawTable();
        Map<String, AttributeValue> itemMap = rawTable.tableSchema().itemToMap(op.item(), true);
        String tableName = rawTable.tableName();
        return TransactWriteItem.builder()
                .put(p -> p.tableName(tableName).item(itemMap))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TransactWriteItem buildDeleteItem(Operation op) {
        var rawTable = (DynamoDbAsyncTable<Object>) op.table().getRawTable();
        Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(rawTable.tableSchema());
        String tableName = rawTable.tableName();
        return TransactWriteItem.builder()
                .delete(d -> d.tableName(tableName).key(keyMap))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TransactWriteItem buildConditionCheckItem(Operation op) {
        ConditionExpression condExpr = Objects.requireNonNull(op.conditionExpression(),
                "conditionExpression must not be null for CONDITION_CHECK");
        Expression expression = condExpr.toSdkExpression();
        var rawTable = (DynamoDbAsyncTable<Object>) op.table().getRawTable();
        Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(rawTable.tableSchema());
        String tableName = rawTable.tableName();
        return TransactWriteItem.builder()
                .conditionCheck(c -> c.tableName(tableName)
                        .key(keyMap)
                        .conditionExpression(Objects.requireNonNull(expression.expression()))
                        .expressionAttributeNames(
                                Objects.requireNonNullElse(expression.expressionNames(), Map.of()))
                        .expressionAttributeValues(
                                Objects.requireNonNullElse(expression.expressionValues(), Map.of())))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TransactWriteItem buildUpdateWithExpressionItem(Operation op) {
        Objects.requireNonNull(op.item(), "item must not be null for UPDATE_WITH_EXPRESSION");
        var rawTable = (DynamoDbAsyncTable<Object>) op.table().getRawTable();
        Key key = rawTable.keyFrom(op.item());
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(rawTable.tableSchema());
        String tableName = rawTable.tableName();
        UpdateExpression expr = op.updateExpression();
        return TransactWriteItem.builder()
                .update(u -> u.tableName(tableName)
                        .key(keyMap)
                        .updateExpression(Objects.requireNonNull(expr).getExpression())
                        .expressionAttributeNames(expr.getExpressionNames())
                        .expressionAttributeValues(expr.getExpressionValues()))
                .build();
    }

    private static Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        var keyBuilder = Key.builder()
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            keyBuilder.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return keyBuilder.build();
    }
}
