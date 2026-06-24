package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds a transactional write operation that groups up to 100 put, update, delete,
 * and condition check actions atomically across one or more tables.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactWrite()}.
 * <p>
 * All actions succeed or none are applied. If any condition fails or a conflict occurs,
 * the entire transaction is rejected.
 */
public class TransactWriteBuilder {

    private final DynamoDbEnhancedClient enhancedClient;
    private final TransactWriteItemsEnhancedRequest.Builder requestBuilder =
            TransactWriteItemsEnhancedRequest.builder();

    public TransactWriteBuilder(@NonNull DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
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
        requestBuilder.addPutItem(table.getRawTable(), Objects.requireNonNull(item));
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
        requestBuilder.addUpdateItem(table.getRawTable(), Objects.requireNonNull(item));
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
        Key key = Key.builder().partitionValue(toAttributeValue(partitionKey)).build();
        requestBuilder.addDeleteItem(table.getRawTable(), key);
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
        Key key = Key.builder()
                     .partitionValue(toAttributeValue(partitionKey))
                     .sortValue(toAttributeValue(sortKey))
                     .build();
        requestBuilder.addDeleteItem(table.getRawTable(), key);
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
                                                            @NonNull Consumer<FilterExpression> condition) {
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
                                                            @NonNull Consumer<FilterExpression> condition) {
        FilterExpression filter = FilterExpression.builder();
        condition.accept(filter);

        Expression expression = Expression.builder()
                .expression(filter.getExpression())
                .expressionNames(filter.getExpressionNames())
                .expressionValues(filter.getExpressionValues())
                .build();

        requestBuilder.addConditionCheck(
                table.getRawTable(),
                cb -> cb.key(k -> {
                    k.partitionValue(toAttributeValue(partitionKey));
                    if (sortKey != null) {
                        k.sortValue(toAttributeValue(sortKey));
                    }
                }).conditionExpression(expression));
        return this;
    }

    /**
     * Executes the transactional write operation.
     * <p>
     * All actions are applied atomically. If any action fails,
     * a {@link software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException}
     * is thrown.
     */
    public void execute() {
        enhancedClient.transactWriteItems(requestBuilder.build());
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
