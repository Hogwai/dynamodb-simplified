package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.ExpressionConstants;
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

    private static final String FALLBACK_TABLE = "entity";

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
        this.pkSetter = findSetterFromGetter(clazz, pkGetter);
        this.skSetter = findSetterFromGetter(clazz, skGetter);
    }

    @SuppressWarnings("NullAway")
    private static Method findSetterFromGetter(Class<?> clazz, Method getter) {
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
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findSetter(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name, String.class);
        } catch (NoSuchMethodException e) {
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
        for (String component : schema.keyComponents()) {
            String value = schema.computeKey(component, entity);
            if (value == null) {
                continue;
            }
            Method setter;
            if (ExpressionConstants.PK_COMPONENT.equals(component)) {
                setter = pkSetter;
            } else if (ExpressionConstants.SK_COMPONENT.equals(component)) {
                setter = skSetter;
            } else {
                setter = findSetter(entity.getClass(), "set" + component);
            }
            if (setter != null) {
                setValue(entity, setter, value);
            }
        }
    }

    private static <T> void setValue(T entity, Method setter, String value) {
        if (setter != null) {
            try {
                setter.invoke(entity, value);
            } catch (Exception e) {
                throw new OperationFailedException(DynamoDbOperations.ENTITY_PUT.getOperationName(), FALLBACK_TABLE, e);
            }
        }
    }
}
