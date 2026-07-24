package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.KeyUtils;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.*;

/**
 * Abstract base for sync and async cross-table batch write builders.
 * <p>
 * Manages operations (put and delete), consumed capacity settings,
 * and request item building shared across sync and async variants.
 *
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractCrossTableBatchWriteBuilder<S extends AbstractCrossTableBatchWriteBuilder<S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractCrossTableBatchWriteBuilder.class);

    protected final List<Operation> operations = new ArrayList<>();
    protected ReturnConsumedCapacity returnConsumedCapacity;

    protected AbstractCrossTableBatchWriteBuilder() {
    }

    /**
     * Stores a table reference, operation type, and optional item or key values.
     * The table is stored as {@code Object} to accommodate both {@code Table}
     * and {@code AsyncTable} types without coupling the base to either package.
     */
    public record Operation(
            Type type,
            Object table,
            @Nullable Object item,
            @Nullable Object partitionKey,
            @Nullable Object sortKey
    ) {
        public enum Type {
            PUT, DELETE
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
     * Adds a put action to insert or replace an item in the given table.
     *
     * @param table the table to write to
     * @param item  the item to put
     * @param <T>   the item type
     * @return this builder
     */
    public @NonNull <T> S put(@NonNull Object table, @NonNull T item) {
        operations.add(new Operation(Operation.Type.PUT, table, Objects.requireNonNull(item), null, null));
        return self();
    }

    /**
     * Adds a delete action for the given partition key in the given table.
     *
     * @param table        the table to delete from
     * @param partitionKey the partition key value
     * @return this builder
     */
    public @NonNull S delete(@NonNull Object table, @NonNull Object partitionKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, null));
        return self();
    }

    /**
     * Adds a delete action for the given partition and sort key in the given table.
     *
     * @param table        the table to delete from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    public @NonNull S delete(@NonNull Object table, @NonNull Object partitionKey, @NonNull Object sortKey) {
        operations.add(new Operation(Operation.Type.DELETE, table, null, partitionKey, sortKey));
        return self();
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public S returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return self();
    }

    /**
     * Builds the request items map from the current operations.
     *
     * @return the request items keyed by table name
     */
    @SuppressWarnings({"unchecked"})
    protected @NonNull Map<String, List<WriteRequest>> buildRequestItems() {
        Map<String, List<WriteRequest>> requestMap = new HashMap<>();
        for (Operation op : operations) {
            String tableName = getTableName(op);
            List<WriteRequest> writes = requestMap.computeIfAbsent(tableName, ignored -> new ArrayList<>());
            TableSchema<?> schema = getTableSchema(op);

            switch (op.type()) {
                case PUT -> {
                    Objects.requireNonNull(op.item(), "item must not be null for PUT");
                    Map<String, AttributeValue> itemMap = ((TableSchema<Object>) schema).itemToMap(op.item(), true);
                    writes.add(WriteRequest.builder().putRequest(r -> r.item(itemMap)).build());
                }
                case DELETE -> {
                    Key key = buildKey(Objects.requireNonNull(op.partitionKey()), op.sortKey());
                    Map<String, AttributeValue> keyMap = key.primaryKeyMap(schema);
                    writes.add(WriteRequest.builder().deleteRequest(r -> r.key(keyMap)).build());
                }
            }
        }
        return requestMap;
    }

    /**
     * Builds a {@link Key} from partition and optional sort key values.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value, or {@code null} if the table has no sort key
     * @return the constructed key
     */
    protected static @NonNull Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        return KeyUtils.buildKey(
                AttributeValueConverter.toKeyAttributeValue(partitionKey),
                sortKey != null ? AttributeValueConverter.toKeyAttributeValue(sortKey) : null);
    }
}
