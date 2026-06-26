package com.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.NonNull;
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
public class BatchGetResult<T> {

    private final List<T> items;
    private final Map<String, KeysAndAttributes> unprocessedKeys;

    /**
     * Constructs a new {@code BatchGetResult}.
     *
     * @param items          the successfully retrieved items
     * @param unprocessedKeys the keys that were not processed, keyed by table name
     */
    public BatchGetResult(@NonNull List<T> items,
                          @NonNull Map<String, KeysAndAttributes> unprocessedKeys) {
        this.items = Collections.unmodifiableList(items);
        this.unprocessedKeys = Collections.unmodifiableMap(unprocessedKeys);
    }

    /**
     * Returns the successfully retrieved items.
     *
     * @return an unmodifiable list of items (never {@code null})
     */
    @NonNull
    public List<T> getItems() {
        return items;
    }

    /**
     * Returns the unprocessed keys, keyed by table name.
     *
     * @return an unmodifiable map of unprocessed keys (never {@code null})
     */
    @NonNull
    public Map<String, KeysAndAttributes> getUnprocessedKeys() {
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
