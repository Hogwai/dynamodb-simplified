package com.hogwai.dynamodb.simplified;

import org.jspecify.annotations.NonNull;

import com.hogwai.dynamodb.simplified.builder.CrossTableBatchGetBuilder;
import com.hogwai.dynamodb.simplified.builder.CrossTableBatchWriteBuilder;
import com.hogwai.dynamodb.simplified.builder.TransactGetBuilder;
import com.hogwai.dynamodb.simplified.builder.TransactWriteBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Entry point for the DynamoDB Simplified library.
 * <p>
 * Provides factory methods to create a client and access typed {@link Table} instances
 * for DynamoDB table operations. Wraps a {@link DynamoDbEnhancedClient} and a
 * {@link DynamoDbClient}.
 * </p>
 * <p>
 * Thread safety: this client is safe to use from multiple threads once created.
 * The {@code table()} factory method may be called concurrently. Individual builders
 * returned by {@code Table} methods are single-use and not thread-safe.
 * </p>
 * <p>Usage:</p>
 * <pre>{@code
 * DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
 * Table<Post> posts = client.table("posts", Post.class);
 * posts.query().partitionKey("subreddit").execute();
 * }</pre>
 *
 * @see Table
 * @see DynamoDbEnhancedClient
 * @see DynamoDbClient
 */
public class DynamoSimplifiedClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoSimplifiedClient.class);
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    DynamoSimplifiedClient(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Returns a new {@link DynamoSimplifiedClientBuilder} for building a client with
     * a custom DynamoDB client and/or enhanced client extensions.
     *
     * @return a new builder instance
     */
    @NonNull
    public static DynamoSimplifiedClientBuilder builder() {
        return new DynamoSimplifiedClientBuilder();
    }

    /**
     * Creates a {@code DynamoSimplifiedClient} with default AWS credentials and region.
     * <p>
     * Builds both the {@link DynamoDbEnhancedClient} and the underlying {@link DynamoDbClient}.
     *
     * @return a new {@code DynamoSimplifiedClient} instance
     */
    @NonNull
    public static DynamoSimplifiedClient create() {
        DynamoDbClient client = DynamoDbClient.create();
        if (LOG.isDebugEnabled()) {
            LOG.debug("DynamoSimplifiedClient created");
        }
        return new DynamoSimplifiedClient(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(),
                client);
    }

    /**
     * Creates a {@code DynamoSimplifiedClient} with a custom {@link DynamoDbClient}.
     * <p>
     * Builds the {@link DynamoDbEnhancedClient} from the provided client.
     *
     * @param client the DynamoDB client to use
     * @return a new {@code DynamoSimplifiedClient} instance
     */
    @NonNull
    public static DynamoSimplifiedClient create(@NonNull DynamoDbClient client) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("DynamoSimplifiedClient created");
        }
        return new DynamoSimplifiedClient(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(),
                client);
    }

    /**
     * Returns a typed {@link Table} for the given table name and item class.
     * <p>
     * The item class must be a valid DynamoDB bean with appropriate annotations.
     *
     * @param tableName the name of the DynamoDB table
     * @param itemClass the class of items stored in the table
     * @param <T>       the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    public <T> Table<T> table(@NonNull String tableName, @NonNull Class<T> itemClass) {
        TableSchema<T> schema = TableSchema.fromBean(itemClass);
        DynamoDbTable<T> table = enhancedClient.table(tableName, schema);
        return new Table<>(enhancedClient, table, dynamoDbClient);
    }

    /**
     * Returns a typed {@link Table} for the given table name and custom schema.
     *
     * @param tableName the name of the DynamoDB table
     * @param schema    the table schema defining item mapping
     * @param <T>       the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    public <T> Table<T> table(@NonNull String tableName, @NonNull TableSchema<T> schema) {
        DynamoDbTable<T> table = enhancedClient.table(tableName, schema);
        return new Table<>(enhancedClient, table, dynamoDbClient);
    }

    /**
     * Returns a typed {@link Table} for the given table name and item class,
     * using a {@link StaticTableSchema} configured by the provided consumer.
     * <p>
     * This is useful when you want to define the table schema programmatically
     * rather than using annotations. Example:
     * <pre>{@code
     * Table<Item> table = client.table("items", Item.class, b -> b
     *     .newItemSupplier(Item::new)
     *     .addAttribute(String.class, a -> a.name("pk")
     *         .getter(Item::getPk)
     *         .setter(Item::setPk)
     *         .tags(StaticAttributeTags.primaryPartitionKey()))
     *     .addAttribute(String.class, a -> a.name("sk")
     *         .getter(Item::getSk)
     *         .setter(Item::setSk)
     *         .tags(StaticAttributeTags.primarySortKey())));
     * }</pre>
     *
     * @param tableName    the name of the DynamoDB table
     * @param itemClass    the class of items stored in the table
     * @param configurator a consumer to configure the {@link StaticTableSchema.Builder}
     * @param <T>          the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    public <T> Table<T> table(@NonNull String tableName, @NonNull Class<T> itemClass,
                              @NonNull Consumer<StaticTableSchema.Builder<T>> configurator) {
        StaticTableSchema.Builder<T> builder = StaticTableSchema.builder(itemClass);
        configurator.accept(builder);
        TableSchema<T> schema = builder.build();
        return table(tableName, schema);
    }

    // ============ Transaction Operations ============

    /**
     * Starts building a transactional read operation across one or more tables.
     * <p>
     * All items are read atomically in a single all-or-nothing transaction.
     *
     * @return a transact get builder
     */
    @NonNull
    public TransactGetBuilder transactGet() {
        return new TransactGetBuilder(enhancedClient, dynamoDbClient);
    }

    /**
     * Starts building a transactional write operation across one or more tables.
     * <p>
     * All put, update, delete, and condition check actions are applied atomically.
     *
     * @return a transact write builder
     */
    @NonNull
    public TransactWriteBuilder transactWrite() {
        return new TransactWriteBuilder(enhancedClient, dynamoDbClient);
    }

    // ============ Cross-Table Batch Operations ============

    /**
     * Returns a cross-table batch get builder for retrieving items from multiple tables.
     *
     * @return a cross-table batch get builder
     */
    @NonNull
    public CrossTableBatchGetBuilder batchGet() {
        return new CrossTableBatchGetBuilder(dynamoDbClient);
    }

    /**
     * Returns a cross-table batch write builder for putting/deleting items across multiple tables.
     *
     * @return a cross-table batch write builder
     */
    @NonNull
    public CrossTableBatchWriteBuilder batchWrite() {
        return new CrossTableBatchWriteBuilder(dynamoDbClient);
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
     * Returns the underlying {@link DynamoDbClient} for advanced operations.
     *
     * @return the DynamoDB client
     */
    @NonNull
    public DynamoDbClient getDynamoDbClient() {
        return dynamoDbClient;
    }

    /**
     * Lists all DynamoDB tables available to this client.
     * <p>
     * Returns a list of table names accessible with the configured AWS credentials.
     *
     * @return a list of DynamoDB table names
     */
    @NonNull
    public List<String> listTables() {
        return dynamoDbClient.listTables().tableNames();
    }

    /**
     * Executes a PartiQL statement against DynamoDB.
     * <p>
     * This is a passthrough to the underlying low-level
     * {@link DynamoDbClient#executeStatement(ExecuteStatementRequest)}. No fluent
     * builder is provided because PartiQL is schemaless and the SDK request is
     * already simple.
     *
     * @param request the PartiQL request to execute
     * @return the response from DynamoDB
     */
    @NonNull
    public ExecuteStatementResponse executeStatement(@NonNull ExecuteStatementRequest request) {
        return dynamoDbClient.executeStatement(request);
    }

    /**
     * Closes the underlying DynamoDB client, releasing any network resources.
     * <p>
     * After calling this method, the client instance is no longer usable.
     */
    @Override
    public void close() {
        dynamoDbClient.close();
    }
}
