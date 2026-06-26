package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed interface for single-table design operations on a DynamoDB table.
 * <p>
 * Provides high-level methods that automatically compute composite keys
 * from {@link KeyComponent @KeyComponent}-annotated fields and filter by
 * discriminator on query operations.
 *
 * @param <T> the entity type, annotated with {@link Entity @Entity}
 */
public interface EntityTable<T> {

    /**
     * Returns the DynamoDB table name associated with this entity.
     *
     * @return the table name
     */
    @NonNull String tableName();

    /**
     * Returns the {@link EntitySchema} for this entity type.
     *
     * @return the entity schema
     */
    @NonNull EntitySchema<T> schema();

    /**
     * Puts an item, auto-computing composite keys and setting discriminator.
     *
     * @param entity the entity to put
     */
    void put(@NonNull T entity);

    /**
     * Gets an item by its composite partition key value.
     *
     * @param partitionKey the computed partition key value
     * @return the matching entity, or {@code null} if not found
     */
    @Nullable T get(@NonNull Object partitionKey);

    /**
     * Gets an item by its composite partition and sort keys.
     *
     * @param partitionKey the computed partition key value
     * @param sortKey      the computed sort key value
     * @return the matching entity, or {@code null} if not found
     */
    @Nullable T get(@NonNull Object partitionKey, @NonNull Object sortKey);

    /**
     * Queries items sharing this partition key, auto-filtering by discriminator.
     *
     * @param partitionKey the computed partition key value
     * @return matching entities (never null)
     */
    @NonNull List<T> query(@NonNull Object partitionKey);

    /**
     * Deletes an item by its computed partition key.
     *
     * @param partitionKey the computed partition key value
     */
    void delete(@NonNull Object partitionKey);

    /**
     * Deletes an item by its computed partition and sort keys.
     *
     * @param partitionKey the computed partition key value
     * @param sortKey      the computed sort key value
     */
    void delete(@NonNull Object partitionKey, @NonNull Object sortKey);

    /**
     * Updates an item, auto-computing composite keys.
     *
     * @param entity the entity with updated values
     */
    void update(@NonNull T entity);
}
