package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.async.AsyncTable;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Builder for creating {@link AsyncEntityTable} instances for single-table design.
 * <p>
 * The entity class must be annotated with {@link Entity @Entity} and may use
 * {@link KeyComponent @KeyComponent} and {@link KeyPrefix @KeyPrefix} annotations to define the entity schema.
 * <p>
 * Usage:
 * <pre>{@code
 * AsyncEntityTable<User> userTable = new AsyncEntityTableBuilder<>(User.class)
 *         .dynamoDbAsyncClient(asyncClient)
 *         .build();
 * }</pre>
 * <p>
 * Alternatively, use
 * {@link dev.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient#entityTable(Class)}
 * for a simpler end-to-end flow.
 *
 * @param <T> the entity type
 */
public final class AsyncEntityTableBuilder<T> {

    private final Class<T> entityClass;
    private DynamoDbAsyncClient dynamoDbAsyncClient;
    private DynamoDbEnhancedAsyncClient enhancedAsyncClient;

    /**
     * Creates a new builder for the given entity class.
     *
     * @param entityClass the entity class annotated with {@code @Entity}
     */
    public AsyncEntityTableBuilder(@NonNull Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Sets the low-level {@link DynamoDbAsyncClient} to use.
     *
     * @param client the DynamoDB async client
     * @return this builder for chaining
     */
    public AsyncEntityTableBuilder<T> dynamoDbAsyncClient(@NonNull DynamoDbAsyncClient client) {
        this.dynamoDbAsyncClient = client;
        return this;
    }

    /**
     * Sets the {@link DynamoDbEnhancedAsyncClient} to use.
     * <p>
     * If not set, one will be built from the provided
     * {@link #dynamoDbAsyncClient(DynamoDbAsyncClient)}.
     *
     * @param client the enhanced async DynamoDB client
     * @return this builder for chaining
     */
    public AsyncEntityTableBuilder<T> enhancedAsyncClient(@NonNull DynamoDbEnhancedAsyncClient client) {
        this.enhancedAsyncClient = client;
        return this;
    }

    /**
     * Builds the {@link AsyncEntityTable} instance.
     * <p>
     * Reads the entity schema from annotations, creates the underlying
     * DynamoDB table mapping, and wraps it in an {@link AsyncDefaultEntityTable}.
     *
     * @return a new {@code AsyncEntityTable<T>}
     * @throws IllegalArgumentException if the entity class is not annotated with {@code @Entity}
     * @throws NullPointerException     if no {@code DynamoDbAsyncClient} is set
     */
    @NonNull
    public AsyncEntityTable<T> build() {
        EntitySchema<T> schema = EntitySchemaReader.read(entityClass);
        DynamoDbEnhancedAsyncClient ec = enhancedAsyncClient != null
                ? enhancedAsyncClient
                : DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dynamoDbAsyncClient).build();
        DynamoDbAsyncClient dc = dynamoDbAsyncClient;
        DynamoDbAsyncTable<T> rawTable = ec.table(schema.tableName(), TableSchema.fromBean(entityClass));
        AsyncTable<T> asyncTable = new AsyncTable<>(ec, rawTable, dc);
        return new AsyncDefaultEntityTable<>(asyncTable, schema);
    }
}
