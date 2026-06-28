package dev.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async typed interface for single-table design operations on a DynamoDB table.
 * <p>
 * Mirrors {@link EntityTable} but returns {@link CompletableFuture} from all
 * mutating and query methods to support asynchronous execution.
 * <p>
 * Provides high-level methods that automatically compute composite keys
 * from {@link KeyComponent @KeyComponent}-annotated fields and filter by
 * discriminator on query operations.
 *
 * @param <T> the entity type, annotated with {@link Entity @Entity}
 */
public interface AsyncEntityTable<T> {

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
     * Puts an item asynchronously, auto-computing composite keys and setting discriminator.
     *
     * @param entity the entity to put
     * @return a future that completes when the item has been put
     */
    @NonNull CompletableFuture<Void> put(@NonNull T entity);

    /**
     * Gets an item by its composite partition key value.
     *
     * @param partitionKey the computed partition key value
     * @return a future containing the matching entity, or {@code null} if not found
     */
    @NonNull CompletableFuture<@Nullable T> get(@NonNull Object partitionKey);

    /**
     * Gets an item by its composite partition and sort keys.
     *
     * @param partitionKey the computed partition key value
     * @param sortKey      the computed sort key value
     * @return a future containing the matching entity, or {@code null} if not found
     */
    @NonNull CompletableFuture<@Nullable T> get(@NonNull Object partitionKey, @NonNull Object sortKey);

    /**
     * Queries items sharing this partition key asynchronously, auto-filtering by discriminator.
     *
     * @param partitionKey the computed partition key value
     * @return a future containing matching entities (never null)
     */
    @NonNull CompletableFuture<List<T>> query(@NonNull Object partitionKey);

    /**
     * Deletes an item asynchronously by extracting its computed partition and sort keys
     * from the given entity.
     *
     * @param entity the entity whose keys identify the item to delete
     * @return a future that completes when the item has been deleted
     */
    @NonNull CompletableFuture<Void> deleteEntity(@NonNull T entity);

    /**
     * Deletes an item asynchronously by its computed partition key.
     *
     * @param partitionKey the computed partition key value
     * @return a future that completes when the item has been deleted
     */
    @NonNull CompletableFuture<Void> delete(@NonNull Object partitionKey);

    /**
     * Deletes an item asynchronously by its computed partition and sort keys.
     *
     * @param partitionKey the computed partition key value
     * @param sortKey      the computed sort key value
     * @return a future that completes when the item has been deleted
     */
    @NonNull CompletableFuture<Void> delete(@NonNull Object partitionKey, @NonNull Object sortKey);

    /**
     * Updates an item asynchronously, auto-computing composite keys.
     *
     * @param entity the entity with updated values
     * @return a future containing the updated item as returned by DynamoDB
     */
    @NonNull CompletableFuture<T> update(@NonNull T entity);
}
