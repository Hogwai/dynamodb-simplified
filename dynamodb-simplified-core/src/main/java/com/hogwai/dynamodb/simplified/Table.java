package com.hogwai.dynamodb.simplified;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.hogwai.dynamodb.simplified.builder.*;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Optional;

/**
 * Typed entry point for DynamoDB operations on a single table.
 * <p>
 * Obtained via {@link DynamoSimplifiedClient#table(String, Class)}.
 * Provides fluent builder access (query, scan, put, get, update, delete)
 * and convenience direct methods (putItem, getItem, updateItem, deleteItem).
 *
 * @param <T> the item type mapped to this table
 */
public class Table<T> {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<T> dynamoDbTable;
    private final DynamoDbClient dynamoDbClient;

    Table(DynamoDbEnhancedClient enhancedClient, DynamoDbTable<T> dynamoDbTable, DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbTable = dynamoDbTable;
        this.dynamoDbClient = dynamoDbClient;
    }

    // ============ Query ============

    /**
     * Starts building a query operation.
     * <p>
     * Use the returned builder to set the partition key, sort key conditions,
     * filters, and other options, then call {@code execute()}.
     *
     * @return a query builder for chaining
     */
    @NonNull
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(dynamoDbTable);
    }

    // ============ Scan ============

    /**
     * Starts building a scan operation.
     * <p>
     * Use the returned builder to set filters and other options,
     * then call {@code execute()}.
     *
     * @return a scan builder for chaining
     */
    @NonNull
    public ScanBuilder<T> scan() {
        return new ScanBuilder<>(dynamoDbTable);
    }

    // ============ Get Item ============

    /**
     * Starts building a get operation by partition key.
     * <p>
     * Use the returned builder to set projection and consistent read options,
     * then call {@code execute()}.
     *
     * @param partitionKey the partition key value
     * @return a get builder for chaining
     */
    @NonNull
    public GetItemBuilder<T> get(@NonNull Object partitionKey) {
        return new GetItemBuilder<>(dynamoDbTable, partitionKey, null);
    }

    /**
     * Starts building a get operation by partition and sort key.
     * <p>
     * Use the returned builder to set projection and consistent read options,
     * then call {@code execute()}.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return a get builder for chaining
     */
    @NonNull
    public GetItemBuilder<T> get(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return new GetItemBuilder<>(dynamoDbTable, partitionKey, sortKey);
    }

    /**
     * Fetches an item by partition key directly.
     *
     * @param partitionKey the partition key value
     * @return an {@link Optional} containing the item, or empty if not found
     */
    @NonNull
    public Optional<T> getItem(@NonNull Object partitionKey) {
        return Optional.ofNullable(
                dynamoDbTable.getItem(Key.builder().partitionValue(toAttributeValue(partitionKey)).build())
        );
    }

    /**
     * Fetches an item by partition and sort key directly.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return an {@link Optional} containing the item, or empty if not found
     */
    @NonNull
    public Optional<T> getItem(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return Optional.ofNullable(
                dynamoDbTable.getItem(Key.builder()
                                 .partitionValue(toAttributeValue(partitionKey))
                                 .sortValue(toAttributeValue(sortKey))
                                 .build())
        );
    }

    // ============ Put Item ============

    /**
     * Starts building a put operation with optional conditions.
     * <p>
     * Use the returned builder to set condition expressions and other options,
     * then call {@code execute()}.
     *
     * @param item the item to put
     * @return a put builder for chaining
     */
    @NonNull
    public PutBuilder<T> put(@NonNull T item) {
        return new PutBuilder<>(dynamoDbTable, item);
    }

    /**
     * Puts an item directly without conditions.
     *
     * @param item the item to put
     */
    public void putItem(@NonNull T item) {
        dynamoDbTable.putItem(item);
    }

    // ============ Update Item ============

    /**
     * Starts building an update operation with optional conditions and partial expressions.
     * <p>
     * Use the returned builder to set condition expressions, update expressions,
     * and other options, then call {@code execute()}.
     *
     * @param item the item with updated values
     * @return an update builder for chaining
     */
    @NonNull
    public UpdateBuilder<T> update(@NonNull T item) {
        return new UpdateBuilder<>(dynamoDbTable, item, dynamoDbClient);
    }

    /**
     * Updates an item directly without conditions or partial expressions.
     *
     * @param item the item with updated values
     * @return the updated item as returned by DynamoDB
     */
    @Nullable
    public T updateItem(@NonNull T item) {
        return dynamoDbTable.updateItem(item);
    }

    // ============ Delete Item ============

    /**
     * Starts building a delete operation by partition key with optional conditions.
     * <p>
     * Use the returned builder to set condition expressions and other options,
     * then call {@code execute()}.
     *
     * @param partitionKey the partition key value
     * @return a delete builder for chaining
     */
    @NonNull
    public DeleteBuilder<T> delete(@NonNull Object partitionKey) {
        return new DeleteBuilder<>(dynamoDbTable, partitionKey, null);
    }

    /**
     * Starts building a delete operation by partition and sort key with optional conditions.
     * <p>
     * Use the returned builder to set condition expressions and other options,
     * then call {@code execute()}.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return a delete builder for chaining
     */
    @NonNull
    public DeleteBuilder<T> delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return new DeleteBuilder<>(dynamoDbTable, partitionKey, sortKey);
    }

    /**
     * Deletes an item by partition key directly.
     *
     * @param partitionKey the partition key value
     */
    public void deleteItem(@NonNull Object partitionKey) {
        dynamoDbTable.deleteItem(Key.builder().partitionValue(toAttributeValue(partitionKey)).build());
    }

    /**
     * Deletes an item by partition and sort key directly.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     */
    public void deleteItem(@NonNull Object partitionKey, @NonNull Object sortKey) {
        dynamoDbTable.deleteItem(Key.builder()
                            .partitionValue(toAttributeValue(partitionKey))
                            .sortValue(toAttributeValue(sortKey))
                            .build());
    }

    // ============ Batch Operations ============

    /**
     * Starts building a batch get operation to retrieve multiple items by key.
     * <p>
     * Use the returned builder to add keys and set options,
     * then call {@code execute()} to retrieve the items.
     *
     * @return a batch get builder for chaining
     */
    @NonNull
    public BatchGetBuilder<T> batchGet() {
        return new BatchGetBuilder<>(enhancedClient, dynamoDbTable);
    }

    /**
     * Starts building a batch write operation to put and delete multiple items.
     * <p>
     * Use the returned builder to add put and delete operations,
     * then call {@code execute()} to submit them.
     *
     * @return a batch write builder for chaining
     */
    @NonNull
    public BatchWriteBuilder<T> batchWrite() {
        return new BatchWriteBuilder<>(enhancedClient, dynamoDbTable);
    }

    // ============ Raw Access ============

    /**
     * Returns the underlying {@link DynamoDbTable} for advanced operations.
     *
     * @return the raw DynamoDB table instance
     */
    @NonNull
    public DynamoDbTable<T> getRawTable() {
        return dynamoDbTable;
    }

    /**
     * Returns the underlying {@link DynamoDbEnhancedClient} for advanced operations.
     *
     * @return the enhanced DynamoDB client
     */
    @NonNull
    public DynamoDbEnhancedClient getEnhancedClient() {
        return enhancedClient;
    }

    /**
     * Returns the underlying low-level {@link DynamoDbClient}.
     *
     * @return the low-level DynamoDB client
     */
    @NonNull
    public DynamoDbClient getDynamoDbClient() {
        return dynamoDbClient;
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}
