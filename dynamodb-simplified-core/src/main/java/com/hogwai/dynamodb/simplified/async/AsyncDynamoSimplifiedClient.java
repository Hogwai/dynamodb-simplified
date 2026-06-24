package com.hogwai.dynamodb.simplified.async;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async entry point for the DynamoDB Simplified library.
 * <p>
 * Provides factory methods to create an async client and access typed {@link AsyncTable}
 * instances for DynamoDB table operations. Wraps a {@link DynamoDbEnhancedAsyncClient}
 * and a {@link DynamoDbAsyncClient}.
 * </p>
 *
 * <p>This class is <b>not</b> thread-safe.</p>
 *
 * @see AsyncTable
 * @see DynamoDbEnhancedAsyncClient
 * @see DynamoDbAsyncClient
 */
public class AsyncDynamoSimplifiedClient {
    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /** Package-private constructor, used by create() and builder() methods. */
    AsyncDynamoSimplifiedClient(
            @NonNull DynamoDbEnhancedAsyncClient enhancedAsyncClient,
            @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.enhancedAsyncClient = enhancedAsyncClient;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Creates an {@code AsyncDynamoSimplifiedClient} with default AWS credentials and region.
     *
     * @return a new instance
     */
    @NonNull
    public static AsyncDynamoSimplifiedClient create() {
        DynamoDbAsyncClient client = DynamoDbAsyncClient.create();
        return new AsyncDynamoSimplifiedClient(
                DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client).build(),
                client);
    }

    /**
     * Creates an {@code AsyncDynamoSimplifiedClient} with a custom {@link DynamoDbAsyncClient}.
     *
     * @param client the async DynamoDB client to use
     * @return a new instance
     */
    @NonNull
    public static AsyncDynamoSimplifiedClient create(@NonNull DynamoDbAsyncClient client) {
        return new AsyncDynamoSimplifiedClient(
                DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client).build(),
                client);
    }

    /**
     * Returns a builder for creating an {@link AsyncDynamoSimplifiedClient} with
     * extensions and a custom DynamoDB async client.
     *
     * @return a new builder instance
     */
    @NonNull
    public static AsyncDynamoSimplifiedClientBuilder builder() {
        return new AsyncDynamoSimplifiedClientBuilder();
    }

    /**
     * Returns a typed {@link AsyncTable} for the given table name and item class.
     *
     * @param tableName the name of the DynamoDB table
     * @param itemClass the class of items stored in the table
     * @param <T>       the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    public <T> AsyncTable<T> table(@NonNull String tableName, @NonNull Class<T> itemClass) {
        TableSchema<T> schema = TableSchema.fromBean(itemClass);
        return new AsyncTable<>(enhancedAsyncClient, enhancedAsyncClient.table(tableName, schema), dynamoDbAsyncClient);
    }

    /**
     * Returns a typed {@link AsyncTable} for the given table name and custom schema.
     *
     * @param tableName the name of the DynamoDB table
     * @param schema    the table schema defining item mapping
     * @param <T>       the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    public <T> AsyncTable<T> table(@NonNull String tableName, @NonNull TableSchema<T> schema) {
        return new AsyncTable<>(enhancedAsyncClient, enhancedAsyncClient.table(tableName, schema), dynamoDbAsyncClient);
    }

    /**
     * Returns a typed {@link AsyncTable} for the given table name and item class,
     * using a {@link StaticTableSchema} configured by the provided consumer.
     * <p>
     * This is useful when you want to define the table schema programmatically
     * rather than using annotations.
     *
     * @param tableName    the name of the DynamoDB table
     * @param itemClass    the class of items stored in the table
     * @param configurator a consumer to configure the {@link StaticTableSchema.Builder}
     * @param <T>          the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    public <T> AsyncTable<T> table(@NonNull String tableName, @NonNull Class<T> itemClass,
                                   @NonNull Consumer<StaticTableSchema.Builder<T>> configurator) {
        StaticTableSchema.Builder<T> builder = StaticTableSchema.builder(itemClass);
        configurator.accept(builder);
        TableSchema<T> schema = builder.build();
        return table(tableName, schema);
    }

    // ============ Transaction Operations ============

    /**
     * Starts building an async transactional read operation across one or more tables.
     * <p>
     * All items are read atomically in a single all-or-nothing transaction.
     *
     * @return an {@link AsyncTransactGetBuilder} for configuring and executing the transaction
     */
    @NonNull
    public AsyncTransactGetBuilder transactGet() {
        return new AsyncTransactGetBuilder(enhancedAsyncClient);
    }

    /**
     * Starts building an async transactional write operation across one or more tables.
     * <p>
     * All put, update, delete, and condition check actions are applied atomically.
     *
     * @return an {@link AsyncTransactWriteBuilder} for configuring and executing the transaction
     */
    @NonNull
    public AsyncTransactWriteBuilder transactWrite() {
        return new AsyncTransactWriteBuilder(enhancedAsyncClient, dynamoDbAsyncClient);
    }

    /**
     * Returns the underlying {@link DynamoDbEnhancedAsyncClient}.
     *
     * @return the enhanced async DynamoDB client
     */
    @NonNull
    public DynamoDbEnhancedAsyncClient getEnhancedClient() {
        return enhancedAsyncClient;
    }

    /**
     * Returns the underlying {@link DynamoDbAsyncClient}.
     *
     * @return the DynamoDB async client
     */
    @NonNull
    public DynamoDbAsyncClient getDynamoDbClient() {
        return dynamoDbAsyncClient;
    }

    /**
     * Lists all DynamoDB tables asynchronously.
     *
     * @return a {@link CompletableFuture} containing a list of table names
     */
    @NonNull
    public CompletableFuture<List<String>> listTables() {
        return dynamoDbAsyncClient.listTables()
                .thenApply(ListTablesResponse::tableNames);
    }

    /**
     * Executes a PartiQL statement against DynamoDB asynchronously.
     * <p>
     * This is a passthrough to the underlying low-level
     * {@link DynamoDbAsyncClient#executeStatement(ExecuteStatementRequest)}.
     *
     * @param request the PartiQL request to execute
     * @return a {@link CompletableFuture} containing the response
     */
    @NonNull
    public CompletableFuture<ExecuteStatementResponse> executeStatement(@NonNull ExecuteStatementRequest request) {
        return dynamoDbAsyncClient.executeStatement(request);
    }
}
