package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import dev.hogwai.dynamodb.simplified.expression.UpdateExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.KeyUtils;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base for sync and async transactional write builders.
 * <p>
 * Manages operations (put, update, delete, condition check), consumed capacity
 * settings, and low-level request item building shared across sync and async
 * variants.
 *
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractTransactWriteBuilder<S extends AbstractTransactWriteBuilder<S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractTransactWriteBuilder.class);

    protected final List<Operation> operations = new ArrayList<>();
    protected ReturnConsumedCapacity returnConsumedCapacity;

    protected AbstractTransactWriteBuilder() {
    }

    /**
     * Stores a table reference, operation type, and optional item, key values,
     * condition expression, and update expression.
     * The table is stored as {@code Object} to accommodate both
     * {@code DynamoDbTable} and {@code DynamoDbAsyncTable} types without coupling
     * the base to either package.
     */
    public record Operation(
            Type type,
            Object table,
            @Nullable Object item,
            @Nullable Object partitionKey,
            @Nullable Object sortKey,
            @Nullable ConditionExpression conditionExpression,
            @Nullable UpdateExpression updateExpression
    ) {
        public enum Type {
            PUT, UPDATE, DELETE, CONDITION_CHECK, UPDATE_WITH_EXPRESSION
        }
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     *
     * @return this builder
     */
    protected abstract @NonNull S self();

    /**
     * Extracts the DynamoDB table name from an operation's table reference.
     *
     * @param operation the operation whose table name to extract
     * @return the DynamoDB table name
     */
    protected abstract @NonNull String getTableName(@NonNull Operation operation);

    /**
     * Extracts the table schema from an operation's table reference.
     *
     * @param operation the operation whose table schema to extract
     * @return the table schema
     */
    @SuppressWarnings("java:S1452")
    protected abstract @NonNull TableSchema<?> getTableSchema(@NonNull Operation operation);

    /**
     * Creates a {@link Key} from the given item using the operation's table
     * reference.
     *
     * @param operation the operation providing the table reference
     * @param item      the item from which to extract the key
     * @return the constructed key
     */
    protected abstract @NonNull Key keyFromItem(@NonNull Operation operation, @NonNull Object item);

    /**
     * Checks whether any operation in this transaction uses an
     * {@link UpdateExpression}.
     *
     * @return {@code true} if any operation has type
     * {@link Operation.Type#UPDATE_WITH_EXPRESSION}
     */
    protected boolean hasExpressionOperation() {
        return operations.stream()
                .anyMatch(op -> op.type() == Operation.Type.UPDATE_WITH_EXPRESSION);
    }

    // === Static helpers ===

    /**
     * Builds a {@link Key} from partition and optional sort key values.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value, or {@code null} if the table has no
     *                     sort key
     * @return the constructed key
     */
    protected static @NonNull Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        return KeyUtils.buildKey(
                AttributeValueConverter.toKeyAttributeValue(partitionKey),
                sortKey != null ? AttributeValueConverter.toKeyAttributeValue(sortKey) : null);
    }

    // === Low-level item building ===

    /**
     * Builds a {@link TransactWriteItem} for the given operation.
     *
     * @param op the operation to build from
     * @return the transact write item
     */
    protected TransactWriteItem buildTransactWriteItem(Operation op) {
        return switch (op.type()) {
            case PUT, UPDATE -> buildPutItem(op);
            case DELETE -> buildDeleteItem(op);
            case CONDITION_CHECK -> buildConditionCheckItem(op);
            case UPDATE_WITH_EXPRESSION -> buildUpdateWithExpressionItem(op);
        };
    }

    @SuppressWarnings("unchecked")
    protected TransactWriteItem buildPutItem(Operation op) {
        Objects.requireNonNull(op.item(), "item must not be null for PUT");
        var schema = (TableSchema<Object>) getTableSchema(op);
        Map<String, AttributeValue> itemMap = schema.itemToMap(op.item(), true);
        String tableName = getTableName(op);
        return TransactWriteItem.builder()
                .put(p -> p.tableName(tableName).item(itemMap))
                .build();
    }

    protected TransactWriteItem buildDeleteItem(Operation op) {
        var schema = getTableSchema(op);
        Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(schema);
        String tableName = getTableName(op);
        return TransactWriteItem.builder()
                .delete(d -> d.tableName(tableName).key(keyMap))
                .build();
    }

    protected TransactWriteItem buildConditionCheckItem(Operation op) {
        ConditionExpression condExpr = Objects.requireNonNull(op.conditionExpression(),
                "conditionExpression must not be null for CONDITION_CHECK");
        Expression expression = condExpr.toSdkExpression();
        var schema = getTableSchema(op);
        Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(schema);
        String tableName = getTableName(op);
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

    protected TransactWriteItem buildUpdateWithExpressionItem(Operation op) {
        Object item = Objects.requireNonNull(op.item(), "item must not be null for UPDATE_WITH_EXPRESSION");
        var schema = getTableSchema(op);
        Key key = keyFromItem(op, item);
        Map<String, AttributeValue> keyMap = key.primaryKeyMap(schema);
        String tableName = getTableName(op);
        UpdateExpression expr = Objects.requireNonNull(op.updateExpression(),
                "updateExpression must not be null for UPDATE_WITH_EXPRESSION");
        return TransactWriteItem.builder()
                .update(u -> u.tableName(tableName)
                        .key(keyMap)
                        .updateExpression(expr.getExpression())
                        .expressionAttributeNames(expr.getExpressionNames())
                        .expressionAttributeValues(expr.getExpressionValues()))
                .build();
    }
}
