package dev.hogwai.dynamodb.simplified;

import dev.hogwai.dynamodb.simplified.builder.CrossTableBatchGetBuilder;
import dev.hogwai.dynamodb.simplified.builder.CrossTableBatchWriteBuilder;
import dev.hogwai.dynamodb.simplified.builder.TransactGetBuilder;
import dev.hogwai.dynamodb.simplified.builder.TransactWriteBuilder;
import dev.hogwai.dynamodb.simplified.entity.EntityTable;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for {@link DynamoSimplifiedClient} to enable mocking and polymorphic usage.
 */
public interface DynamoSimplifiedClientInterface extends AutoCloseable {

    /**
     * Returns a typed {@link Table} for the given table name and item class.
     *
     * @param tableName the name of the DynamoDB table
     * @param itemClass the class of items stored in the table
     * @param <T>       the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    <T> Table<T> table(@NonNull String tableName, @NonNull Class<T> itemClass);

    /**
     * Returns a typed {@link Table} for the given table name and custom schema.
     *
     * @param tableName the name of the DynamoDB table
     * @param schema    the table schema defining item mapping
     * @param <T>       the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    <T> Table<T> table(@NonNull String tableName, @NonNull TableSchema<T> schema);

    /**
     * Returns a typed {@link Table} for the given table name and item class,
     * using a {@link StaticTableSchema} configured by the provided consumer.
     *
     * @param tableName    the name of the DynamoDB table
     * @param itemClass    the class of items stored in the table
     * @param configurator a consumer to configure the {@link StaticTableSchema.Builder}
     * @param <T>          the item type
     * @return a {@code Table<T>} for the specified table
     */
    @NonNull
    <T> Table<T> table(@NonNull String tableName, @NonNull Class<T> itemClass,
                       @NonNull Consumer<StaticTableSchema.Builder<T>> configurator);

    /**
     * Returns an {@link EntityTable} for the given entity class, enabling
     * single-table design with auto-computed composite keys and discriminator filtering.
     *
     * @param entityClass the entity class annotated with {@code @Entity}
     * @param <T>         the entity type
     * @return an {@code EntityTable<T>} for single-table design
     */
    @NonNull
    <T> EntityTable<T> entityTable(@NonNull Class<T> entityClass);

    /**
     * Starts building a transactional read operation across one or more tables.
     *
     * @return a transact get builder
     */
    @NonNull
    TransactGetBuilder transactGet();

    /**
     * Starts building a transactional write operation across one or more tables.
     *
     * @return a transact write builder
     */
    @NonNull
    TransactWriteBuilder transactWrite();

    /**
     * Returns a cross-table batch get builder for retrieving items from multiple tables.
     *
     * @return a cross-table batch get builder
     */
    @NonNull
    CrossTableBatchGetBuilder batchGet();

    /**
     * Returns a cross-table batch write builder for putting/deleting items across multiple tables.
     *
     * @return a cross-table batch write builder
     */
    @NonNull
    CrossTableBatchWriteBuilder batchWrite();

    /**
     * Returns the underlying {@link DynamoDbEnhancedClient} for advanced operations.
     *
     * @return the enhanced DynamoDB client
     */
    @NonNull
    DynamoDbEnhancedClient getEnhancedClient();

    /**
     * Returns the underlying {@link DynamoDbClient} for advanced operations.
     *
     * @return the DynamoDB client
     */
    @NonNull
    DynamoDbClient getDynamoDbClient();

    /**
     * Lists all DynamoDB tables available to this client.
     *
     * @return a list of DynamoDB table names
     */
    @NonNull
    List<String> listTables();

    /**
     * Executes a PartiQL statement against DynamoDB.
     *
     * @param request the PartiQL request to execute
     * @return the response from DynamoDB
     */
    @NonNull
    ExecuteStatementResponse executeStatement(@NonNull ExecuteStatementRequest request);

    @Override
    void close();
}
