package com.hogwai.dynamodb.simplified;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.hogwai.dynamodb.simplified.builder.*;
import com.hogwai.dynamodb.simplified.expression.UpdateExpression;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.result.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import java.util.function.Consumer;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveDescription;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.util.Collection;
import java.util.Objects;
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
        return new QueryBuilder<>(dynamoDbTable, dynamoDbClient);
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
        return new ScanBuilder<>(dynamoDbTable, dynamoDbClient);
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
        return new GetItemBuilder<>(dynamoDbTable, partitionKey, null, dynamoDbClient);
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
        return new GetItemBuilder<>(dynamoDbTable, partitionKey, sortKey, dynamoDbClient);
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
                dynamoDbTable.getItem(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build())
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
                                 .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                                 .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
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
        return new PutBuilder<>(dynamoDbTable, item, dynamoDbClient);
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
     * Starts building a partial update operation with an expression consumer.
     * <p>
     * This is a convenience shorthand for {@code update(item).update(consumer)}.
     * The returned builder can be further configured before calling {@code execute()}.
     *
     * @param item               the item identifying the record to update (key fields only)
     * @param expressionConsumer a consumer to configure the update expression (SET, REMOVE, ADD, DELETE)
     * @return an update builder for further configuration and execution
     */
    @NonNull
    public UpdateBuilder<T> update(@NonNull T item, @NonNull Consumer<UpdateExpression> expressionConsumer) {
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
     */
    public void update(@NonNull Object partitionKey,
                       @Nullable Object sortKey,
                       @NonNull Consumer<UpdateExpression> expressionConsumer) {
        Objects.requireNonNull(expressionConsumer, "expressionConsumer must not be null");
        new UpdateBuilder<>(dynamoDbTable, dynamoDbClient, partitionKey, sortKey)
                .update(expressionConsumer)
                .execute();
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
        return new DeleteBuilder<>(dynamoDbTable, partitionKey, null, dynamoDbClient);
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
        return new DeleteBuilder<>(dynamoDbTable, partitionKey, sortKey, dynamoDbClient);
    }

    /**
     * Deletes an item by partition key directly.
     *
     * @param partitionKey the partition key value
     * @return the deleted item, or {@code null} if no item with the given key exists
     */
    @Nullable
    public T deleteItem(@NonNull Object partitionKey) {
        return dynamoDbTable.deleteItem(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build());
    }

    /**
     * Deletes an item by partition and sort key directly.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return the deleted item, or {@code null} if no item with the given key exists
     */
    @Nullable
    public T deleteItem(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return dynamoDbTable.deleteItem(Key.builder()
                            .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                            .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                            .build());
    }

    // ============ Secondary Index ============

    /**
     * Returns a typed wrapper for the named secondary index (GSI or LSI).
     * <p>
     * Use the returned {@link Index} to perform query and scan operations
     * scoped to that index.
     *
     * @param indexName the name of the secondary index
     * @return an {@code Index<T>} for the specified index
     */
    @NonNull
    public Index<T> index(@NonNull String indexName) {
        return new Index<>(dynamoDbTable.index(indexName), dynamoDbClient);
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
        return new BatchGetBuilder<>(enhancedClient, dynamoDbTable, dynamoDbClient);
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
        return new BatchWriteBuilder<>(dynamoDbTable, dynamoDbClient);
    }

    /**
     * Puts all items in a single batch write operation.
     * <p>
     * Equivalent to creating a {@link BatchWriteBuilder}, adding all items,
     * and executing it. At most 25 items per call are allowed by DynamoDB.
     *
     * @param items the items to put
     * @return the batch write result
     * @throws IllegalArgumentException if more than 25 items are provided
     */
    @NonNull
    public BatchWriteResult putAll(@NonNull Collection<T> items) {
        BatchWriteBuilder<T> batch = batchWrite();
        for (T item : items) {
            batch.put(item);
        }
        return batch.execute();
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

    // ============ Table Management (DDL) ============

    /**
     * Creates the DynamoDB table corresponding to this {@code Table} instance.
     * <p>
     * Uses the table schema defined during
     * {@link DynamoSimplifiedClient#table(String, Class)} to create the table.
     */
    public void create() {
        dynamoDbTable.createTable();
    }

    /**
     * Creates the DynamoDB table with additional options.
     *
     * @param request the create table request options
     */
    public void create(@NonNull CreateTableEnhancedRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        dynamoDbTable.createTable(request);
    }

    /**
     * Creates the DynamoDB table with options configured by a consumer.
     * <p>
     * Example usage:
     * <pre>{@code
     * table.create(b -> b
     *     .provisionedThroughput(p -> p
     *         .readCapacityUnits(10L)
     *         .writeCapacityUnits(10L)));
     * }</pre>
     *
     * @param configurator a consumer to configure the {@link CreateTableEnhancedRequest.Builder}
     */
    public void create(@NonNull Consumer<CreateTableEnhancedRequest.Builder> configurator) {
        Objects.requireNonNull(configurator, "configurator must not be null");
        dynamoDbTable.createTable(configurator);
    }

    /**
     * Deletes the DynamoDB table corresponding to this {@code Table} instance permanently.
     */
    public void delete() {
        dynamoDbTable.deleteTable();
    }

    /**
     * Returns information about the DynamoDB table, including its status, schema,
     * throughput settings, and indexes.
     *
     * @return the table description response
     */
    @NonNull
    public DescribeTableEnhancedResponse describe() {
        return dynamoDbTable.describeTable();
    }

    /**
     * Checks whether the DynamoDB table exists.
     * <p>
     * Returns {@code true} if the table exists, {@code false} if it does not.
     *
     * @return {@code true} if the table exists, {@code false} otherwise
     */
    public boolean exists() {
        try {
            dynamoDbTable.describeTable();
            return true;
        } catch (ResourceNotFoundException _) {
            return false;
        }
    }

    // ============ TTL Management ============

    /**
     * Enables Time To Live (TTL) on this table with the given attribute name.
     *
     * @param attributeName the attribute that stores the TTL epoch value
     * @throws OperationFailedException if the DynamoDB API call fails
     */
    public void enableTtl(@NonNull String attributeName) {
        try {
            dynamoDbClient.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(dynamoDbTable.tableName())
                    .timeToLiveSpecification(spec -> {
                        spec.attributeName(attributeName);
                        spec.enabled(true);
                    })
                    .build());
        } catch (DynamoDbException e) {
            throw new OperationFailedException("UpdateTimeToLive", dynamoDbTable.tableName(), e);
        }
    }

    /**
     * Disables TTL on this table for the given attribute name.
     *
     * @param attributeName the TTL attribute name to disable
     * @throws OperationFailedException if the DynamoDB API call fails
     */
    public void disableTtl(@NonNull String attributeName) {
        try {
            dynamoDbClient.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(dynamoDbTable.tableName())
                    .timeToLiveSpecification(spec -> {
                        spec.attributeName(attributeName);
                        spec.enabled(false);
                    })
                    .build());
        } catch (DynamoDbException e) {
            throw new OperationFailedException("UpdateTimeToLive", dynamoDbTable.tableName(), e);
        }
    }

    /**
     * Describes the current TTL configuration for this table.
     *
     * @return the TTL description
     * @throws OperationFailedException if the DynamoDB API call fails
     */
    @NonNull
    public TimeToLiveDescription describeTtl() {
        try {
            return dynamoDbClient.describeTimeToLive(DescribeTimeToLiveRequest.builder()
                    .tableName(dynamoDbTable.tableName())
                    .build())
                    .timeToLiveDescription();
        } catch (DynamoDbException e) {
            throw new OperationFailedException("DescribeTimeToLive", dynamoDbTable.tableName(), e);
        }
    }

}
