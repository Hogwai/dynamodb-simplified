package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import org.jspecify.annotations.NonNull;

import java.beans.Introspector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * Reads {@link Entity @Entity} and {@link KeyComponent @KeyComponent}
 * annotations from a class and produces an {@link EntitySchema}.
 * <p>
 * Scans both methods and fields for {@code @KeyComponent} annotations
 * and collects {@link KeyPrefix @KeyPrefix} values.
 */
public final class EntitySchemaReader {

    private EntitySchemaReader() {
    }

    /**
     * Reads the entity schema from an {@code @Entity}-annotated class.
     *
     * @param <T>   the entity type
     * @param clazz the entity class to scan
     * @return the parsed entity schema
     * @throws IllegalArgumentException if the class is not annotated with {@code @Entity}
     */
    public static @NonNull <T> EntitySchema<T> read(@NonNull Class<T> clazz) {
        Entity entityAnn = clazz.getAnnotation(Entity.class);
        if (entityAnn == null) {
            throw new IllegalArgumentException(
                    "Class " + clazz.getName() + " is not annotated with @Entity");
        }

        Map<String, List<EntitySchema.KeyComponentInfo>> components = new HashMap<>();
        validateDeclaredKeyComponentMethods(clazz);
        scanMethodsForKeyComponents(clazz, components);
        scanFieldsForKeyComponents(clazz, components);
        sortComponents(components);

        return buildSchema(clazz, entityAnn, components);
    }

    private static <T> void scanMethodsForKeyComponents(Class<T> clazz,
                                                        Map<String, List<EntitySchema.KeyComponentInfo>> components) {
        for (Method method : clazz.getMethods()) {
            KeyComponent kc = method.getAnnotation(KeyComponent.class);
            if (kc == null) {
                continue;
            }

            validateKeyComponentMethod(method, clazz);

            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                throw new IllegalArgumentException("Key component method '" + method.getName()
                        + "' on " + clazz.getName() + " must be a no-argument getter");
            }

            String attributeName = method.getName().startsWith("get")
                    ? Introspector.decapitalize(method.getName().substring(3))
                    : method.getName();

            // Use MethodHandle via unreflect for faster invocation
            MethodHandle handle;
            try {
                handle = MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot extract key component method '"
                        + method.getName() + "' on " + clazz.getName(), e);
            }
            UnaryOperator<Object> extractor = entity -> {
                try {
                    return handle.invoke(entity);
                } catch (Throwable e) {
                    throw new DynamoSimplifiedException("Failed to extract key component '"
                            + attributeName + "' from " + entity, e);
                }
            };

            components.computeIfAbsent(kc.component(), ignored -> new ArrayList<>())
                    .add(new EntitySchema.KeyComponentInfo(
                            kc.component(), kc.position(), attributeName, extractor));
        }
    }

    private static void sortComponents(Map<String, List<EntitySchema.KeyComponentInfo>> components) {
        for (List<EntitySchema.KeyComponentInfo> list : components.values()) {
            list.sort(Comparator.comparingInt(EntitySchema.KeyComponentInfo::position));
        }
    }

    private static <T> EntitySchema<T> buildSchema(Class<T> clazz, Entity entityAnn,
                                                   Map<String, List<EntitySchema.KeyComponentInfo>> components) {
        return new EntitySchema<>(clazz,
                entityAnn.discriminator(),
                entityAnn.discriminatorAttribute(),
                entityAnn.table(),
                components,
                readKeyPrefixes(clazz));
    }

    private static void validateDeclaredKeyComponentMethods(Class<?> clazz) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getAnnotation(KeyComponent.class) != null) {
                    validateKeyComponentMethod(method, clazz);
                }
            }
        }
    }

    private static void validateKeyComponentMethod(Method method, Class<?> clazz) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Methods annotated with @KeyComponent must not be static: "
                    + method.toGenericString());
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Methods annotated with @KeyComponent must be public on "
                    + clazz.getName() + ": " + method.toGenericString());
        }
    }

    private static Map<String, String> readKeyPrefixes(Class<?> clazz) {
        Map<String, String> prefixes = new HashMap<>();
        KeyPrefix prefixAnn = clazz.getAnnotation(KeyPrefix.class);
        if (prefixAnn != null) {
            prefixes.put(prefixAnn.component(), prefixAnn.value());
        }
        KeyPrefix.Container container = clazz.getAnnotation(KeyPrefix.Container.class);
        if (container != null) {
            for (KeyPrefix kp : container.value()) {
                prefixes.put(kp.component(), kp.value());
            }
        }
        return prefixes;
    }

    private static <T> void scanFieldsForKeyComponents(Class<T> clazz,
            Map<String, List<EntitySchema.KeyComponentInfo>> components) {
        for (Field field : clazz.getDeclaredFields()) {
            KeyComponent kc = field.getAnnotation(KeyComponent.class);
            if (kc == null) {
                continue;
            }

            String attributeName = field.getName();

            if (Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException("Cannot extract key component field '"
                        + field.getName() + "' on " + clazz.getName() + ": static fields are not supported");
            }

            MethodHandle handle;
            try {
                handle = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup())
                        .unreflectGetter(field);
            } catch (IllegalAccessException | SecurityException e) {
                throw new IllegalArgumentException("Cannot extract key component field '"
                        + field.getName() + "' on " + clazz.getName()
                        + ": the field is not accessible", e);
            }
            UnaryOperator<Object> extractor = entity -> {
                try {
                    return handle.invoke(entity);
                } catch (Throwable e) {
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
