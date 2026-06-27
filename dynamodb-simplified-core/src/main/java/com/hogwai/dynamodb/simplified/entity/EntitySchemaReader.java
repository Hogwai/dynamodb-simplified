package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import org.jspecify.annotations.NonNull;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.UnaryOperator;

public final class EntitySchemaReader {

    private EntitySchemaReader() {
    }

    public static @NonNull <T> EntitySchema<T> read(@NonNull Class<T> clazz) {
        Entity entityAnn = clazz.getAnnotation(Entity.class);
        if (entityAnn == null) {
            throw new IllegalArgumentException(
                    "Class " + clazz.getName() + " is not annotated with @Entity");
        }

        Map<String, List<EntitySchema.KeyComponentInfo>> components = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            KeyComponent kc = method.getAnnotation(KeyComponent.class);
            if (kc == null) {
                continue;
            }

            String attributeName = method.getName().startsWith("get")
                    ? Introspector.decapitalize(method.getName().substring(3))
                    : method.getName();

            // Use method reference via lambda rather than reflection per-invocation
            UnaryOperator<Object> extractor = entity -> {
                try {
                    return method.invoke(entity);
                } catch (Exception e) {
                    throw new DynamoSimplifiedException("Failed to extract key component '"
                            + attributeName + "' from " + entity, e);
                }
            };

            components.computeIfAbsent(kc.component(), ignored -> new ArrayList<>())
                    .add(new EntitySchema.KeyComponentInfo(
                            kc.component(), kc.position(), attributeName, extractor));
        }

        // Also scan fields for @KeyComponent (annotation targets FIELD too)
        scanFieldsForKeyComponents(clazz, components);

        // Sort each component list by position
        for (List<EntitySchema.KeyComponentInfo> list : components.values()) {
            list.sort(Comparator.comparingInt(EntitySchema.KeyComponentInfo::position));
        }

        // Read @KeyPrefix
        Map<String, String> prefixes = new HashMap<>();
        KeyPrefix prefixAnn = clazz.getAnnotation(KeyPrefix.class);
        if (prefixAnn != null) {
            prefixes.put(prefixAnn.component(), prefixAnn.value());
        }
        // Support multiple @KeyPrefix via container annotation
        KeyPrefix.Container container = clazz.getAnnotation(KeyPrefix.Container.class);
        if (container != null) {
            for (KeyPrefix kp : container.value()) {
                prefixes.put(kp.component(), kp.value());
            }
        }

        return new EntitySchema<>(clazz,
                entityAnn.discriminator(),
                entityAnn.discriminatorAttribute(),
                entityAnn.table(),
                components,
                prefixes);
    }

    private static <T> void scanFieldsForKeyComponents(Class<T> clazz,
            Map<String, List<EntitySchema.KeyComponentInfo>> components) {
        for (Field field : clazz.getDeclaredFields()) {
            KeyComponent kc = field.getAnnotation(KeyComponent.class);
            if (kc == null) {
                continue;
            }

            String attributeName = field.getName();

            String getterName = "get" + Character.toUpperCase(field.getName().charAt(0))
                    + field.getName().substring(1);
            Method getter;
            try {
                getter = clazz.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                throw new DynamoSimplifiedException(
                        "No getter method '" + getterName + "' found for key component field '"
                                + field.getName() + "' in " + clazz.getName(), e);
            }

            UnaryOperator<Object> extractor = entity -> {
                try {
                    return getter.invoke(entity);
                } catch (Exception e) {
                    throw new DynamoSimplifiedException("Failed to extract key component '"
                            + attributeName + "' from " + entity, e);
                }
            };

            components.computeIfAbsent(kc.component(), ignored -> new ArrayList<>())
                    .add(new EntitySchema.KeyComponentInfo(
                            kc.component(), kc.position(), attributeName, extractor));
        }
    }
}
