package com.hogwai.dynamodb.simplified.result;

import com.hogwai.dynamodb.simplified.Table;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of a cross-table batch get operation.
 * <p>
 * Items are accessible per-table via {@link #getItems(Table)}.
 * Unprocessed keys (if any) are available via {@link #getUnprocessedKeys()}.
 * Consumed capacity is available if requested via {@link #consumedCapacity()}.
 */
public final class CrossTableBatchGetResult implements Consumed {

    private final Map<String, List<Map<String, AttributeValue>>> responses;
    private final Map<String, KeysAndAttributes> unprocessedKeys;
    private final Map<String, TableSchema<?>> tableSchemas;
    private final @Nullable ConsumedCapacity consumedCapacity;

    /**
     * Constructs a new {@code CrossTableBatchGetResult} with no consumed capacity information.
     *
     * @param responses       the raw response items per table name
     * @param unprocessedKeys the unprocessed keys per table name
     * @param tableSchemas    the table schemas per table name, for deserialization
     */
    public CrossTableBatchGetResult(
            @NonNull Map<String, List<Map<String, AttributeValue>>> responses,
            @NonNull Map<String, KeysAndAttributes> unprocessedKeys,
            @NonNull Map<String, TableSchema<?>> tableSchemas) {
        this(responses, unprocessedKeys, tableSchemas, null);
    }

    /**
     * Constructs a new {@code CrossTableBatchGetResult}.
     *
     * @param responses        the raw response items per table name
     * @param unprocessedKeys  the unprocessed keys per table name
     * @param tableSchemas     the table schemas per table name, for deserialization
     * @param consumedCapacity the consumed capacity, or {@code null}
     */
    public CrossTableBatchGetResult(
            @NonNull Map<String, List<Map<String, AttributeValue>>> responses,
            @NonNull Map<String, KeysAndAttributes> unprocessedKeys,
            @NonNull Map<String, TableSchema<?>> tableSchemas,
            @Nullable ConsumedCapacity consumedCapacity) {
        this.responses = responses;
        this.unprocessedKeys = Collections.unmodifiableMap(unprocessedKeys);
        this.tableSchemas = tableSchemas;
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
     * Returns items for the given table, deserialized from the raw response.
     *
     * @param table the typed table whose items to retrieve
     * @param <T>   the item type
     * @return a list of items for the given table (never {@code null})
     */
    @SuppressWarnings("unchecked")
    public @NonNull <T> List<T> getItems(@NonNull Table<T> table) {
        List<Map<String, AttributeValue>> items = responses.getOrDefault(
                table.getRawTable().tableName(), List.of());
        return items.stream()
                .map(m -> (T) tableSchemas.get(table.getRawTable().tableName()).mapToItem(m))
                .toList();
    }

    /**
     * Returns the unprocessed keys, keyed by table name.
     *
     * @return an unmodifiable map of unprocessed keys (never {@code null})
     */
    public @NonNull Map<String, KeysAndAttributes> getUnprocessedKeys() {
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
