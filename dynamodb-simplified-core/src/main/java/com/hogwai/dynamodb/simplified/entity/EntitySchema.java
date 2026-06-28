package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Captures the schema metadata for an entity type used in single-table designs.
 * <p>
 * Read from an entity class via {@link EntitySchemaReader#read(Class)}.
 * Provides access to the entity's discriminator, key components, and prefixes.
 *
 * @param <T> the entity type
 */
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

    /**
     * Returns the entity class.
     *
     * @return the entity class
     */
    public @NonNull Class<T> entityClass() {
        return entityClass;
    }

    /**
     * Returns the discriminator value for this entity type.
     *
     * @return the discriminator value
     */
    public @NonNull String discriminator() {
        return discriminator;
    }

    /**
     * Returns the attribute name used to store the discriminator value.
     *
     * @return the discriminator attribute name
     */
    public @NonNull String discriminatorAttribute() {
        return discriminatorAttribute;
    }

    /**
     * Returns the DynamoDB table name.
     *
     * @return the table name
     */
    public @NonNull String tableName() {
        return tableName;
    }

    /**
     * Computes a composite key value from the entity's annotated components.
     *
     * @param component the key component name (e.g. {@code "PK"} or {@code "SK"})
     * @param entity    the entity instance to extract values from
     * @return the computed key string, or {@code null} if no components match
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

    /**
     * Returns the set of key component names defined for this entity.
     *
     * @return the set of key component names
     */
    public @NonNull Set<String> keyComponents() {
        return keyComponents.keySet();
    }

    /**
     * Describes a single key component part with its attribute name, position,
     * and a strategy for extracting the value from an entity instance.
     *
     * @param component     the key component name
     * @param position      the ordering position within the composite key
     * @param attributeName the entity attribute name
     * @param extractor     the strategy for extracting the value from an entity instance
     */
    public record KeyComponentInfo(
            String component,
            int position,
            String attributeName,
            UnaryOperator<Object> extractor
    ) {
    }

    /**
     * Returns the key component info for a given component name, or {@code null}.
     *
     * @param component the key component name
     * @return the list of component info entries, or {@code null} if not found
     */
    public @Nullable List<KeyComponentInfo> getKeyComponentInfo(@NonNull String component) {
        return keyComponents.get(component);
    }
}
