package com.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of a cross-table batch write operation.
 * <p>
 * Contains any unprocessed items that were not written.
 */
public record CrossTableBatchWriteResult(Map<String, List<WriteRequest>> unprocessedItems) {

    /**
     * Constructs a new {@code CrossTableBatchWriteResult}.
     *
     * @param unprocessedItems the items that were not processed, keyed by table name
     */
    public CrossTableBatchWriteResult(@NonNull Map<String, List<WriteRequest>> unprocessedItems) {
        this.unprocessedItems = Collections.unmodifiableMap(unprocessedItems);
    }

    /**
     * Returns the unprocessed items, keyed by table name.
     *
     * @return an unmodifiable map of unprocessed items (never {@code null})
     */
    @Override
    public @NonNull Map<String, List<WriteRequest>> unprocessedItems() {
        return unprocessedItems;
    }

    /**
     * Returns whether any items were not processed.
     *
     * @return {@code true} if there are unprocessed items, {@code false} otherwise
     */
    public boolean hasUnprocessed() {
        return !unprocessedItems.isEmpty();
    }
}
