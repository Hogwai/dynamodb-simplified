package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.Table;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builder for creating {@link EntityTable} instances for single-table design.
 * <p>
 * The entity class must be annotated with {@link Entity @Entity} and may use
 * {@link KeyComponent @KeyComponent} and {@link KeyPrefix @KeyPrefix} annotations to define the entity schema.
 * <p>
 * Usage:
 * <pre>{@code
 * EntityTable<User> userTable = new EntityTableBuilder<>(User.class)
 *         .dynamoDbClient(dynamoDbClient)
 *         .build();
 * }</pre>
 * <p>
 * Alternatively, use {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#entityTable(Class)}
 * for a simpler end-to-end flow.
 *
 * @param <T> the entity type
 */
public final class EntityTableBuilder<T> {

    private final Class<T> entityClass;
    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient enhancedClient;

    /**
     * Creates a new builder for the given entity class.
     *
     * @param entityClass the entity class annotated with {@code @Entity}
     */
    public EntityTableBuilder(@NonNull Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Sets the low-level {@link DynamoDbClient} to use.
     *
     * @param client the DynamoDB client
     * @return this builder for chaining
     */
    public EntityTableBuilder<T> dynamoDbClient(@NonNull DynamoDbClient client) {
        this.dynamoDbClient = client;
        return this;
    }

    /**
     * Sets the {@link DynamoDbEnhancedClient} to use.
     * <p>
     * If not set, one will be built from the provided {@link #dynamoDbClient(DynamoDbClient)}.
     *
     * @param client the enhanced DynamoDB client
     * @return this builder for chaining
     */
    public EntityTableBuilder<T> enhancedClient(@NonNull DynamoDbEnhancedClient client) {
        this.enhancedClient = client;
        return this;
    }

    /**
     * Builds the {@link EntityTable} instance.
     * <p>
     * Reads the entity schema from annotations, creates the underlying
     * DynamoDB table mapping, and wraps it in an {@link DefaultEntityTable}.
     *
     * @return a new {@code EntityTable<T>}
     * @throws IllegalArgumentException if the entity class is not annotated with {@code @Entity}
     * @throws NullPointerException     if no {@code DynamoDbClient} is set
     */
    @NonNull
    public EntityTable<T> build() {
        EntitySchema<T> schema = EntitySchemaReader.read(entityClass);
        DynamoDbEnhancedClient ec = enhancedClient != null
                ? enhancedClient
                : DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
        DynamoDbClient dc = dynamoDbClient;
        DynamoDbTable<T> rawTable = ec.table(schema.tableName(), TableSchema.fromBean(entityClass));
        Table<T> table = new Table<>(ec, rawTable, dc);
        return new DefaultEntityTable<>(table, schema);
    }
}
