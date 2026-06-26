package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

public final class EntitySchema<T> {

    private final Class<T> entityClass;
    private final String discriminator;
    private final String discriminatorAttribute;
    private final String tableName;
    private final Map<String, List<KeyComponentInfo>> keyComponents;
    private final Map<String, String> keyPrefixes;

    EntitySchema(Class<T> entityClass, String discriminator, String discriminatorAttribute,
                 String tableName, Map<String, List<KeyComponentInfo>> keyComponents,
                 Map<String, String> keyPrefixes) {
        this.entityClass = entityClass;
        this.discriminator = discriminator;
        this.discriminatorAttribute = discriminatorAttribute;
        this.tableName = tableName;
        this.keyComponents = Collections.unmodifiableMap(keyComponents);
        this.keyPrefixes = Collections.unmodifiableMap(keyPrefixes);
    }

    public @NonNull Class<T> entityClass() {
        return entityClass;
    }

    public @NonNull String discriminator() {
        return discriminator;
    }

    public @NonNull String discriminatorAttribute() {
        return discriminatorAttribute;
    }

    public @NonNull String tableName() {
        return tableName;
    }

    /**
     * Computes a composite key value from the entity's annotated components.
     */
    public @Nullable String computeKey(@NonNull String component, @NonNull T entity) {
        List<KeyComponentInfo> components = keyComponents.get(component);
        if (components == null || components.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String prefix = keyPrefixes.get(component);
        if (prefix != null) {
            sb.append(prefix).append('#');
        }
        boolean first = true;
        for (KeyComponentInfo info : components) {
            if (!first) {
                sb.append('#');
            }
            Object val = info.extractor.apply(entity);
            if (val != null) {
                sb.append(val);
            }
            first = false;
        }
        return sb.toString();
    }

    public @NonNull Set<String> keyComponents() {
        return keyComponents.keySet();
    }

    public record KeyComponentInfo(
            String component,
            int position,
            String attributeName,
            UnaryOperator<Object> extractor
    ) {
    }

    /**
     * Returns the key component info for a given component name, or {@code null}.
     */
    public @Nullable List<KeyComponentInfo> getKeyComponentInfo(@NonNull String component) {
        return keyComponents.get(component);
    }
}
