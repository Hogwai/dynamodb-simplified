package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link EntityTable}.
 * <p>
 * Uses the DynamoDB Enhanced Client via a delegating {@link Table} to perform operations.
 * Before put/update, this implementation computes composite key values from
 * {@link KeyComponent @KeyComponent}-annotated fields and sets the entity's
 * {@link DynamoDbPartitionKey} and {@link DynamoDbSortKey} fields via reflection.
 * Query operations automatically filter by the entity's discriminator value
 * to support single-table design.
 *
 * @param <T> the entity type
 */
final class DefaultEntityTable<T> implements EntityTable<T> {

    private final Table<T> table;
    private final EntitySchema<T> schema;

    // Cached PK/SK setters
    private final Method pkSetter;
    private final Method skSetter;

    @SuppressWarnings("NullAway")
    DefaultEntityTable(Table<T> table, EntitySchema<T> schema) {
        this.table = table;
        this.schema = schema;
        Class<T> clazz = schema.entityClass();

        Method pkGetter = null;
        Method skGetter = null;
        for (Method m : clazz.getMethods()) {
            if (m.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                pkGetter = m;
            }
            if (m.isAnnotationPresent(DynamoDbSortKey.class)) {
                skGetter = m;
            }
        }
        this.pkSetter = findSetter(clazz, pkGetter);
        this.skSetter = findSetter(clazz, skGetter);

        if (this.pkSetter == null) {
            throw new IllegalArgumentException(
                    "Entity class " + clazz.getName()
                            + " must have a @DynamoDbPartitionKey-annotated getter with a corresponding setter. "
                            + "The setter is required for automatic composite key computation.");
        }
    }

    @SuppressWarnings("NullAway")
    private static <T> Method findSetter(Class<T> clazz, Method getter) {
        if (getter == null) {
            return null;
        }
        String name = getter.getName();
        if (!name.startsWith("get")) {
            return null;
        }
        String setterName = "set" + name.substring(3);
        try {
            return clazz.getMethod(setterName, getter.getReturnType());
        } catch (NoSuchMethodException _) {
            return null;
        }
    }

    @Override
    public @NonNull String tableName() {
        return schema.tableName();
    }

    @Override
    public @NonNull EntitySchema<T> schema() {
        return schema;
    }

    @Override
    public void put(@NonNull T entity) {
        computeAndSetKeys(entity);
        table.putItem(entity);
    }

    @Override
    public @Nullable T get(@NonNull Object partitionKey) {
        Optional<T> result = table.getItem(partitionKey);
        return result.orElse(null);
    }

    @Override
    public @Nullable T get(@NonNull Object partitionKey, @NonNull Object sortKey) {
        Optional<T> result = table.getItem(partitionKey, sortKey);
        return result.orElse(null);
    }

    @Override
    public @NonNull List<T> query(@NonNull Object partitionKey) {
        return table.query()
                .partitionKey(partitionKey)
                .filter(f -> f.eq(schema.discriminatorAttribute(), schema.discriminator()))
                .executeAll();
    }

    @Override
    public void delete(@NonNull Object partitionKey) {
        table.deleteItem(partitionKey);
    }

    @Override
    public void delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        table.deleteItem(partitionKey, sortKey);
    }

    @Override
    public void update(@NonNull T entity) {
        computeAndSetKeys(entity);
        table.updateItem(entity);
    }

    private void computeAndSetKeys(T entity) {
        String pk = schema.computeKey("PK", entity);
        if (pk != null) {
            setValue(entity, pkSetter, pk);
        }
        String sk = schema.computeKey("SK", entity);
        if (sk != null && skSetter != null) {
            setValue(entity, skSetter, sk);
        }
    }

    private static <T> void setValue(T entity, Method setter, String value) {
        if (setter != null) {
            try {
                setter.invoke(entity, value);
            } catch (Exception e) {
                throw new OperationFailedException("EntityPut", "entity", e);
            }
        }
    }
}
