package com.hogwai.dynamodb.simplified.async;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.function.Consumer;
import java.util.Objects;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Typed async entry point for DynamoDB operations on a single table.
 * <p>
 * Obtained via {@link AsyncDynamoSimplifiedClient#table(String, Class)}.
 * Provides convenience direct methods (getItem, putItem, updateItem, deleteItem)
 * returning {@link CompletableFuture}, as well as DDL operations (create, delete, describe).
 * Builder-based query, scan, and CRUD operations will be added in a later phase.
 *
 * @param <T> the item type mapped to this table
 */
public class AsyncTable<T> {
    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final DynamoDbAsyncTable<T> dynamoDbAsyncTable;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /** Package-private constructor. */
    AsyncTable(
            @NonNull DynamoDbEnhancedAsyncClient enhancedAsyncClient,
            @NonNull DynamoDbAsyncTable<T> dynamoDbAsyncTable,
            @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.enhancedAsyncClient = enhancedAsyncClient;
        this.dynamoDbAsyncTable = dynamoDbAsyncTable;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    // ============ Query ============

    /**
     * Starts building an async query operation against this table.
     *
     * @return an {@link AsyncQueryBuilder} for configuring and executing the query
     */
    @NonNull
    public AsyncQueryBuilder<T> query() {
        return new AsyncQueryBuilder<>(dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    // ============ Scan ============

    /**
     * Starts building an async scan operation against this table.
     *
     * @return an {@link AsyncScanBuilder} for configuring and executing the scan
     */
    @NonNull
    public AsyncScanBuilder<T> scan() {
        return new AsyncScanBuilder<>(dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    // ============ Get Item ============

    /**
     * Starts building a get operation by partition key.
     *
     * @param partitionKey the partition key value
     * @return an {@link AsyncGetItemBuilder} for configuring and executing the get
     */
    @NonNull
    public AsyncGetItemBuilder<T> get(@NonNull Object partitionKey) {
        return new AsyncGetItemBuilder<>(dynamoDbAsyncTable, partitionKey, null, dynamoDbAsyncClient);
    }

    /**
     * Starts building a get operation by partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return an {@link AsyncGetItemBuilder} for configuring and executing the get
     */
    @NonNull
    public AsyncGetItemBuilder<T> get(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return new AsyncGetItemBuilder<>(dynamoDbAsyncTable, partitionKey, sortKey, dynamoDbAsyncClient);
    }

    /**
     * Fetches an item by partition key directly.
     *
     * @param partitionKey the partition key value
     * @return a {@link CompletableFuture} containing an {@link Optional} with the item, or empty if not found
     */
    @NonNull
    public CompletableFuture<Optional<T>> getItem(@NonNull Object partitionKey) {
        return dynamoDbAsyncTable.getItem(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build())
                .thenApply(Optional::ofNullable);
    }

    /**
     * Fetches an item by partition and sort key directly.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return a {@link CompletableFuture} containing an {@link Optional} with the item, or empty if not found
     */
    @NonNull
    public CompletableFuture<Optional<T>> getItem(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return dynamoDbAsyncTable.getItem(Key.builder()
                        .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                        .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                        .build())
                .thenApply(Optional::ofNullable);
    }

    // ============ Put Item ============

    /**
     * Starts building a put operation with optional conditions.
     *
     * @param item the item to put
     * @return an {@link AsyncPutBuilder} for configuring and executing the put
     */
    @NonNull
    public AsyncPutBuilder<T> put(@NonNull T item) {
        return new AsyncPutBuilder<>(dynamoDbAsyncTable, item, dynamoDbAsyncClient);
    }

    /**
     * Puts an item directly without conditions.
     *
     * @param item the item to put
     * @return a {@link CompletableFuture} that completes when the item has been put
     */
    @NonNull
    public CompletableFuture<Void> putItem(@NonNull T item) {
        return dynamoDbAsyncTable.putItem(item);
    }

    // ============ Update Item ============

    /**
     * Starts building an update operation with optional conditions and partial expressions.
     *
     * @param item the item with updated values
     * @return an {@link AsyncUpdateBuilder} for configuring and executing the update
     */
    @NonNull
    public AsyncUpdateBuilder<T> update(@NonNull T item) {
        return new AsyncUpdateBuilder<>(dynamoDbAsyncTable, item, dynamoDbAsyncClient);
    }

    /**
     * Starts building a partial update operation with an expression consumer.
     * <p>
     * This is a convenience shorthand for {@code update(item).update(consumer)}.
     * The returned builder can be further configured before calling {@code execute()}.
     *
     * @param item               the item identifying the record to update (key fields only)
     * @param expressionConsumer a consumer to configure the update expression (SET, REMOVE, ADD, DELETE)
     * @return an async update builder for further configuration and execution
     */
    @NonNull
    public AsyncUpdateBuilder<T> update(@NonNull T item, @NonNull Consumer<UpdateExpression> expressionConsumer) {
        Objects.requireNonNull(expressionConsumer, "expressionConsumer must not be null");
        return update(item).update(expressionConsumer);
    }

    /**
     * Updates an item identified by partition and sort key using a partial update expression.
     * <p>
     * This avoids creating a dummy item object when the key is already known.
     * Pass {@code null} for the sort key when the table has no sort key.
     *
     * @param partitionKey        the partition key value
     * @param sortKey             the sort key value, or {@code null} if the table has no sort key
     * @param expressionConsumer a consumer to build the update expression
     * @return a {@link CompletableFuture} that completes when the update has been executed
     */
    @NonNull
    public CompletableFuture<Void> update(@NonNull Object partitionKey,
                                          @Nullable Object sortKey,
                                          @NonNull Consumer<UpdateExpression> expressionConsumer) {
        Objects.requireNonNull(expressionConsumer, "expressionConsumer must not be null");
        return new AsyncUpdateBuilder<>(dynamoDbAsyncTable, dynamoDbAsyncClient, partitionKey, sortKey)
                .update(expressionConsumer)
                .execute()
                .thenApply(_ -> null);
    }

    /**
     * Updates an item directly without conditions or partial expressions.
     *
     * @param item the item with updated values
     * @return a {@link CompletableFuture} containing the updated item as returned by DynamoDB
     */
    @NonNull
    public CompletableFuture<T> updateItem(@NonNull T item) {
        return dynamoDbAsyncTable.updateItem(item);
    }

    // ============ Delete Item ============

    /**
     * Starts building a delete operation by partition key with optional conditions.
     *
     * @param partitionKey the partition key value
     * @return an {@link AsyncDeleteBuilder} for configuring and executing the delete
     */
    @NonNull
    public AsyncDeleteBuilder<T> delete(@NonNull Object partitionKey) {
        return new AsyncDeleteBuilder<>(dynamoDbAsyncTable, partitionKey, null, dynamoDbAsyncClient);
    }

    /**
     * Starts building a delete operation by partition and sort key with optional conditions.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return an {@link AsyncDeleteBuilder} for configuring and executing the delete
     */
    @NonNull
    public AsyncDeleteBuilder<T> delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return new AsyncDeleteBuilder<>(dynamoDbAsyncTable, partitionKey, sortKey, dynamoDbAsyncClient);
    }

    /**
     * Deletes an item by partition key directly.
     *
     * @param partitionKey the partition key value
     * @return a {@link CompletableFuture} that completes with the deleted item,
     *         or {@code null} if no item with the given key exists
     */
    @NonNull
    public CompletableFuture<T> deleteItem(@NonNull Object partitionKey) {
        return dynamoDbAsyncTable.deleteItem(
                Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build());
    }

    /**
     * Deletes an item by partition and sort key directly.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return a {@link CompletableFuture} that completes with the deleted item,
     *         or {@code null} if no item with the given key exists
     */
    @NonNull
    public CompletableFuture<T> deleteItem(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return dynamoDbAsyncTable.deleteItem(Key.builder()
                        .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                        .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                        .build());
    }

    // ============ Secondary Index ============

    /**
     * Returns a typed wrapper for the named secondary index (GSI or LSI).
     * <p>
     * Use the returned {@link AsyncIndex} to perform query and scan operations
     * scoped to that index.
     *
     * @param indexName the name of the secondary index
     * @return an {@code AsyncIndex<T>} for the specified index
     */
    @NonNull
    public AsyncIndex<T> index(@NonNull String indexName) {
        return new AsyncIndex<>(dynamoDbAsyncTable.index(indexName), dynamoDbAsyncClient);
    }

    // ============ Batch Operations ============

    /**
     * Starts building an async batch get operation to retrieve multiple items by their keys.
     *
     * @return an {@link AsyncBatchGetBuilder} for configuring and executing the batch get
     */
    @NonNull
    public AsyncBatchGetBuilder<T> batchGet() {
        return new AsyncBatchGetBuilder<>(enhancedAsyncClient, dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    /**
     * Starts building an async batch write operation to put and delete multiple items.
     *
     * @return an {@link AsyncBatchWriteBuilder} for configuring and executing the batch write
     */
    @NonNull
    public AsyncBatchWriteBuilder<T> batchWrite() {
        return new AsyncBatchWriteBuilder<>(dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    // ============ Raw Access ============

    /**
     * Returns the underlying {@link DynamoDbAsyncTable} for advanced operations.
     *
     * @return the raw async DynamoDB table instance
     */
    @NonNull
    public DynamoDbAsyncTable<T> getRawTable() {
        return dynamoDbAsyncTable;
    }

    /**
     * Returns the underlying {@link DynamoDbEnhancedAsyncClient} for advanced operations.
     *
     * @return the enhanced async DynamoDB client
     */
    @NonNull
    public DynamoDbEnhancedAsyncClient getEnhancedClient() {
        return enhancedAsyncClient;
    }

    /**
     * Returns the underlying low-level {@link DynamoDbAsyncClient}.
     *
     * @return the low-level async DynamoDB client
     */
    @NonNull
    public DynamoDbAsyncClient getDynamoDbClient() {
        return dynamoDbAsyncClient;
    }

    // ============ Table Management (DDL) ============

    /**
     * Creates the DynamoDB table corresponding to this {@code AsyncTable} instance.
     * <p>
     * Uses the table schema defined during
     * {@link AsyncDynamoSimplifiedClient#table(String, Class)} to create the table.
     *
     * @return a {@link CompletableFuture} that completes when the table has been created
     */
    @NonNull
    public CompletableFuture<Void> create() {
        return dynamoDbAsyncTable.createTable();
    }

    /**
     * Creates the DynamoDB table with the given request options.
     *
     * @param request the create table request options
     * @return a {@link CompletableFuture} that completes when the table has been created
     */
    @NonNull
    public CompletableFuture<Void> create(@NonNull CreateTableEnhancedRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return dynamoDbAsyncTable.createTable(request);
    }

    /**
     * Creates the DynamoDB table with options configured by a consumer.
     * <p>
     * Example usage:
     * <pre>{@code
     * asyncTable.create(b -> b
     *     .provisionedThroughput(p -> p
     *         .readCapacityUnits(10L)
     *         .writeCapacityUnits(10L)));
     * }</pre>
     *
     * @param configurator a consumer to configure the {@link CreateTableEnhancedRequest.Builder}
     * @return a {@link CompletableFuture} that completes when the table has been created
     */
    @NonNull
    public CompletableFuture<Void> create(@NonNull Consumer<CreateTableEnhancedRequest.Builder> configurator) {
        Objects.requireNonNull(configurator, "configurator must not be null");
        return dynamoDbAsyncTable.createTable(configurator);
    }

    /**
     * Deletes the DynamoDB table corresponding to this {@code AsyncTable} instance permanently.
     *
     * @return a {@link CompletableFuture} that completes when the table has been deleted
     */
    @NonNull
    public CompletableFuture<Void> delete() {
        return dynamoDbAsyncTable.deleteTable();
    }

    /**
     * Returns information about the DynamoDB table, including its status, schema,
     * throughput settings, and indexes.
     *
     * @return a {@link CompletableFuture} containing the table description response
     */
    @NonNull
    public CompletableFuture<DescribeTableEnhancedResponse> describe() {
        return dynamoDbAsyncTable.describeTable();
    }

    /**
     * Checks whether the DynamoDB table exists.
     * <p>
     * Returns a {@link CompletableFuture} that completes with {@code true}
     * if the table exists, or {@code false} if it does not.
     *
     * @return a future that completes with {@code true} if the table exists, {@code false} otherwise
     */
    @NonNull
    public CompletableFuture<Boolean> exists() {
        return dynamoDbAsyncTable.describeTable()
                .handle((_, error) -> {
                    // Unwrap CompletionException to find the actual cause
                    Throwable actualError = error;
                    while (actualError instanceof CompletionException ce) {
                        actualError = ce.getCause();
                    }
                    return switch (actualError) {
                        case null -> true;
                        case ResourceNotFoundException _ -> false;

                        // AWS SDK exceptions are RuntimeExceptions, re-throw as-is
                        case RuntimeException re -> throw re;
                        default -> throw new DynamoSimplifiedException(String.valueOf(actualError), actualError);
                    };
                });
    }

    // ============ Internal Helpers ============


}
