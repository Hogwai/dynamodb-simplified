package com.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of a cross-table batch write operation.
 * <p>
 * Contains any unprocessed items that were not written,
 * and optionally the consumed capacity if requested.
 */
public final class CrossTableBatchWriteResult implements Consumed {

    private final Map<String, List<WriteRequest>> unprocessedItems;
    private final @Nullable ConsumedCapacity consumedCapacity;

    /**
     * Constructs a new {@code CrossTableBatchWriteResult} with no consumed capacity information.
     *
     * @param unprocessedItems the items that were not processed, keyed by table name
     */
    public CrossTableBatchWriteResult(@NonNull Map<String, List<WriteRequest>> unprocessedItems) {
        this(unprocessedItems, null);
    }

    /**
     * Constructs a new {@code CrossTableBatchWriteResult}.
     *
     * @param unprocessedItems the items that were not processed, keyed by table name
     * @param consumedCapacity the consumed capacity, or {@code null}
     */
    public CrossTableBatchWriteResult(@NonNull Map<String, List<WriteRequest>> unprocessedItems,
                                      @Nullable ConsumedCapacity consumedCapacity) {
        this.unprocessedItems = Collections.unmodifiableMap(unprocessedItems);
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
     * Returns the unprocessed items, keyed by table name.
     *
     * @return an unmodifiable map of unprocessed items (never {@code null})
     */
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
