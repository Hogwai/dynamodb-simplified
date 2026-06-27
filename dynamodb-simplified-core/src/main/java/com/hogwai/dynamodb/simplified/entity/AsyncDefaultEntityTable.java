package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.async.AsyncTable;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default async implementation of {@link AsyncEntityTable}.
 * <p>
 * Uses the DynamoDB Enhanced Async Client via a delegating {@link AsyncTable}
 * to perform operations. Before put/update, this implementation computes
 * composite key values from {@link KeyComponent @KeyComponent}-annotated fields
 * and sets the entity's {@link DynamoDbPartitionKey} and {@link DynamoDbSortKey}
 * fields via reflection. Query operations automatically filter by the entity's
 * discriminator value to support single-table design.
 *
 * @param <T> the entity type
 */
final class AsyncDefaultEntityTable<T> implements AsyncEntityTable<T> {

    private final AsyncTable<T> asyncTable;
    private final EntitySchema<T> schema;

    // Cached PK/SK getters and setters
    private final Method pkGetter;
    private final Method pkSetter;
    private final Method skGetter;
    private final Method skSetter;

    @SuppressWarnings("NullAway")
    AsyncDefaultEntityTable(AsyncTable<T> asyncTable, EntitySchema<T> schema) {
        this.asyncTable = asyncTable;
        this.schema = schema;
        Class<T> clazz = schema.entityClass();

        Method foundPkGetter = null;
        Method foundSkGetter = null;
        for (Method m : clazz.getMethods()) {
            if (m.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                foundPkGetter = m;
            }
            if (m.isAnnotationPresent(DynamoDbSortKey.class)) {
                foundSkGetter = m;
            }
        }
        this.pkGetter = foundPkGetter;
        this.skGetter = foundSkGetter;
        this.pkSetter = findSetter(clazz, foundPkGetter);
        this.skSetter = findSetter(clazz, foundSkGetter);

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
        } catch (NoSuchMethodException ignored) {
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
    public @NonNull CompletableFuture<Void> put(@NonNull T entity) {
        computeAndSetKeys(entity);
        return asyncTable.putItem(entity);
    }

    @Override
    public @NonNull CompletableFuture<@Nullable T> get(@NonNull Object partitionKey) {
        return asyncTable.getItem(partitionKey)
                .thenApply(opt -> opt.orElse(null));
    }

    @Override
    public @NonNull CompletableFuture<@Nullable T> get(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return asyncTable.getItem(partitionKey, sortKey)
                .thenApply(opt -> opt.orElse(null));
    }

    @Override
    public @NonNull CompletableFuture<List<T>> query(@NonNull Object partitionKey) {
        return asyncTable.query()
                .partitionKey(partitionKey)
                .filter(f -> f.eq(schema.discriminatorAttribute(), schema.discriminator()))
                .executeAll();
    }

    @Override
    public @NonNull CompletableFuture<Void> deleteEntity(@NonNull T entity) {
        computeAndSetKeys(entity);
        String pk = readPk(entity);
        if (pk == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Partition key cannot be null for deleteEntity"));
        }
        String sk = readSk(entity);
        if (sk != null) {
            return asyncTable.deleteItem(pk, sk).thenApply(ignored -> null);
        }
        return asyncTable.deleteItem(pk).thenApply(ignored -> null);
    }

    @Override
    public @NonNull CompletableFuture<Void> delete(@NonNull Object partitionKey) {
        return asyncTable.deleteItem(partitionKey).thenApply(ignored -> null);
    }

    @Override
    public @NonNull CompletableFuture<Void> delete(@NonNull Object partitionKey, @NonNull Object sortKey) {
        return asyncTable.deleteItem(partitionKey, sortKey).thenApply(ignored -> null);
    }

    @Override
    public @NonNull CompletableFuture<T> update(@NonNull T entity) {
        computeAndSetKeys(entity);
        return asyncTable.updateItem(entity);
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

    @SuppressWarnings("NullAway")
    private String readPk(T entity) {
        if (pkGetter != null) {
            try {
                Object value = pkGetter.invoke(entity);
                return value != null ? value.toString() : null;
            } catch (Exception e) {
                throw new OperationFailedException("EntityDelete", "entity", e);
            }
        }
        return null;
    }

    @SuppressWarnings("NullAway")
    private @Nullable String readSk(T entity) {
        if (skGetter != null) {
            try {
                Object value = skGetter.invoke(entity);
                return value != null ? value.toString() : null;
            } catch (Exception e) {
                throw new OperationFailedException("EntityDelete", "entity", e);
            }
        }
        return null;
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
