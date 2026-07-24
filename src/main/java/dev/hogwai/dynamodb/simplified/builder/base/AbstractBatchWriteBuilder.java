package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.KeyUtils;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base for sync and async batch write builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractBatchWriteBuilder<T, S extends AbstractBatchWriteBuilder<T, S>> {

    /**
     * Logger for this builder class.
     */
    protected static final Logger LOG = Logging.getLogger(AbstractBatchWriteBuilder.class);

    /** The list of items to put in the batch write. */
    protected final List<T> itemsToPut = new ArrayList<>();
    /** The list of keys to delete in the batch write. */
    protected final List<Key> keysToDelete = new ArrayList<>();
    /** Optional consumed capacity reporting level. */
    protected ReturnConsumedCapacity returnConsumedCapacity;

    /**
     * Constructs a new {@code AbstractBatchWriteBuilder}.
     */
    protected AbstractBatchWriteBuilder() {
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     *
     * @return this builder
     */
    protected abstract S self();

    /**
     * Returns the table schema for converting items to attribute maps.
     *
     * @return the table schema
     */
    protected abstract @NonNull TableSchema<T> tableSchema();

    /**
     * Returns the DynamoDB table name.
     *
     * @return the DynamoDB table name
     */
    protected abstract @NonNull String tableName();

    /**
     * Adds an item to be put (inserted or replaced) in the batch write.
     *
     * @param item the item to put
     * @return this builder
     */
    public @NonNull S put(@NonNull T item) {
        itemsToPut.add(Objects.requireNonNull(item, "item must not be null"));
        return self();
    }

    /**
     * Adds a delete operation for the given partition key.
     *
     * @param partitionKey the partition key of the item to delete
     * @return this builder
     */
    public @NonNull S delete(@NonNull Object partitionKey) {
        keysToDelete.add(KeyUtils.buildKey(AttributeValueConverter.toKeyAttributeValue(partitionKey), null));
        return self();
    }

    /**
     * Adds a delete operation for the given partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    public @NonNull S delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        keysToDelete.add(KeyUtils.buildKey(
                AttributeValueConverter.toKeyAttributeValue(partitionKey),
                AttributeValueConverter.toKeyAttributeValue(sortKey)));
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
     * Builds the request items map from the current puts and deletes.
     *
     * @return the request items keyed by table name
     */
    protected @NonNull Map<String, List<WriteRequest>> buildRequestItems() {
        List<WriteRequest> writes = new ArrayList<>(itemsToPut.size() + keysToDelete.size());
        for (T item : itemsToPut) {
            Map<String, AttributeValue> itemMap = tableSchema().itemToMap(item, true);
            writes.add(WriteRequest.builder().putRequest(r -> r.item(itemMap)).build());
        }
        for (Key key : keysToDelete) {
            Map<String, AttributeValue> keyMap = key.primaryKeyMap(tableSchema());
            writes.add(WriteRequest.builder().deleteRequest(r -> r.key(keyMap)).build());
        }
        return Map.of(tableName(), writes);
    }
}
