package dev.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of a BatchGetBuilder execution.
 * Contains the successfully retrieved items and any keys that were not processed.
 *
 * @param <T> the item type
 */
public final class BatchGetResult<T> implements Consumed {

    private final List<T> items;
    private final Map<String, KeysAndAttributes> unprocessedKeys;
    private final @Nullable ConsumedCapacity consumedCapacity;

    /**
     * Constructs a new {@code BatchGetResult} with no consumed capacity information.
     *
     * @param items           the successfully retrieved items
     * @param unprocessedKeys the keys that were not processed, keyed by table name
     */
    public BatchGetResult(@NonNull List<T> items,
                          @NonNull Map<String, KeysAndAttributes> unprocessedKeys) {
        this(items, unprocessedKeys, null);
    }

    /**
     * Constructs a new {@code BatchGetResult}.
     *
     * @param items            the successfully retrieved items
     * @param unprocessedKeys  the keys that were not processed, keyed by table name
     * @param consumedCapacity the consumed capacity, or {@code null}
     */
    public BatchGetResult(@NonNull List<T> items,
                          @NonNull Map<String, KeysAndAttributes> unprocessedKeys,
                          @Nullable ConsumedCapacity consumedCapacity) {
        this.items = Collections.unmodifiableList(items);
        this.unprocessedKeys = Collections.unmodifiableMap(unprocessedKeys);
        this.consumedCapacity = consumedCapacity;
    }

    /**
     * Returns the consumed capacity, or {@code null} if capacity was not requested
     * or the operation does not support capacity tracking.
     *
     * @return consumed capacity, or {@code null}
     */
    @Override
    @Nullable
    public ConsumedCapacity consumedCapacity() {
        return consumedCapacity;
    }

    /**
     * Returns the successfully retrieved items.
     *
     * @return an unmodifiable list of items (never {@code null})
     */
    @NonNull
    public List<T> items() {
        return items;
    }

    /**
     * Returns the unprocessed keys, keyed by table name.
     *
     * @return an unmodifiable map of unprocessed keys (never {@code null})
     */
    @NonNull
    public Map<String, KeysAndAttributes> unprocessedKeys() {
        return unprocessedKeys;
    }

    /**
     * Returns whether any keys were not processed.
     *
     * @return {@code true} if there are unprocessed keys, {@code false} otherwise
     */
    public boolean hasUnprocessed() {
        return !unprocessedKeys.isEmpty();
    }
}
