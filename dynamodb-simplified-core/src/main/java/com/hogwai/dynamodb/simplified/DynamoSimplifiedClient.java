package com.hogwai.dynamodb.simplified;

import org.jspecify.annotations.NonNull;

import com.hogwai.dynamodb.simplified.builder.TransactGetBuilder;
import com.hogwai.dynamodb.simplified.builder.TransactWriteBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Entry point for the DynamoDB Simplified library.
 * <p>
 * Provides factory methods to create a client and access typed {@link Table} instances
 * for DynamoDB table operations. Wraps a {@link DynamoDbEnhancedClient} and a
 * {@link DynamoDbClient}.
 * </p>
 * <p>Usage:</p>
 * <pre>{@code
 * DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
 * Table<Post> posts = client.table("posts", Post.class);
 * posts.query().partitionKey("subreddit").execute();
 * }</pre>
 * <p>
 * This class is <b>not</b> thread-safe.
 * </p>
 *
 * @see Table
 * @see DynamoDbEnhancedClient
 * @see DynamoDbClient
 */
public class DynamoSimplifiedClient {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    private DynamoSimplifiedClient(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
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
        return new TransactGetBuilder(enhancedClient);
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
        return new TransactWriteBuilder(enhancedClient);
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
}
