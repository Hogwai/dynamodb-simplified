package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.entity.AsyncEntityTable;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for {@link AsyncDynamoSimplifiedClient} to enable mocking and polymorphic usage.
 */
public interface AsyncDynamoSimplifiedClientInterface extends AutoCloseable {

    /**
     * Returns a typed {@link AsyncTable} for the given table name and item class.
     *
     * @param tableName the name of the DynamoDB table
     * @param itemClass the class of items stored in the table
     * @param <T>       the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    <T> AsyncTable<T> table(@NonNull String tableName, @NonNull Class<T> itemClass);

    /**
     * Returns a typed {@link AsyncTable} for the given table name and custom schema.
     *
     * @param tableName the name of the DynamoDB table
     * @param schema    the table schema defining item mapping
     * @param <T>       the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    <T> AsyncTable<T> table(@NonNull String tableName, @NonNull TableSchema<T> schema);

    /**
     * Returns a typed {@link AsyncTable} for the given table name and item class,
     * using a {@link StaticTableSchema} configured by the provided consumer.
     *
     * @param tableName    the name of the DynamoDB table
     * @param itemClass    the class of items stored in the table
     * @param configurator a consumer to configure the {@link StaticTableSchema.Builder}
     * @param <T>          the item type
     * @return an {@code AsyncTable<T>} for the specified table
     */
    @NonNull
    <T> AsyncTable<T> table(@NonNull String tableName, @NonNull Class<T> itemClass,
                            @NonNull Consumer<StaticTableSchema.Builder<T>> configurator);

    /**
     * Returns an {@link AsyncEntityTable} for the given entity class, enabling
     * single-table design with auto-computed composite keys and discriminator filtering.
     *
     * @param entityClass the entity class annotated with {@code @Entity}
     * @param <T>         the entity type
     * @return an {@code AsyncEntityTable<T>} for single-table design
     */
    @NonNull
    <T> AsyncEntityTable<T> entityTable(@NonNull Class<T> entityClass);

    /**
     * Starts building an async transactional read operation across one or more tables.
     *
     * @return an async transact get builder
     */
    @NonNull
    AsyncTransactGetBuilder transactGet();

    /**
     * Starts building an async transactional write operation across one or more tables.
     *
     * @return an async transact write builder
     */
    @NonNull
    AsyncTransactWriteBuilder transactWrite();

    /**
     * Returns an async cross-table batch get builder for retrieving items from multiple tables.
     *
     * @return an async cross-table batch get builder
     */
    @NonNull
    AsyncCrossTableBatchGetBuilder batchGet();

    /**
     * Returns an async cross-table batch write builder for putting/deleting items across multiple tables.
     *
     * @return an async cross-table batch write builder
     */
    @NonNull
    AsyncCrossTableBatchWriteBuilder batchWrite();

    /**
     * Returns the underlying {@link DynamoDbEnhancedAsyncClient}.
     *
     * @return the enhanced async DynamoDB client
     */
    @NonNull
    DynamoDbEnhancedAsyncClient getEnhancedClient();

    /**
     * Returns the underlying {@link DynamoDbAsyncClient}.
     *
     * @return the DynamoDB async client
     */
    @NonNull
    DynamoDbAsyncClient getDynamoDbClient();

    /**
     * Lists all DynamoDB tables asynchronously.
     *
     * @return a {@link CompletableFuture} containing a list of table names
     */
    @NonNull
    CompletableFuture<List<String>> listTables();

    /**
     * Executes a PartiQL statement against DynamoDB asynchronously.
     *
     * @param request the PartiQL request to execute
     * @return a {@link CompletableFuture} containing the response
     */
    @NonNull
    CompletableFuture<ExecuteStatementResponse> executeStatement(@NonNull ExecuteStatementRequest request);

    @Override
    void close();
}
