package dev.hogwai.dynamodb.simplified.internal;

import dev.hogwai.dynamodb.simplified.entity.Version;
import dev.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

/**
 * Helper for optimistic locking via version fields.
 */
public final class VersionHelper {

    private static final String VERSION_ATTR = "version";

    private VersionHelper() {
    }

    /**
     * Returns the version from an item with a {@link Version} field.
     *
     * @param item the item to check
     * @return the version, or null if the item has no version field or version is null
     */
    @Nullable
    public static Integer getVersion(@NonNull Object item) {
        return getAnnotatedVersion(item).orElse(null);
    }

    /**
     * Increments the version on an item with a {@link Version} field.
     *
     * @param item the item to update
     */

    @SuppressWarnings("java:S3011")
    public static void incrementVersion(@NonNull Object item) {
        Optional<Field> versionField = getAnnotatedVersionField(item.getClass());
        if (versionField.isPresent()) {
            Field f = versionField.get();
            try {
                f.setAccessible(true);
                Integer current = (Integer) f.get(item);
                f.set(item, current != null ? current + 1 : 1);
            } catch (Exception e) {
                throw new DynamoSimplifiedException("Failed to increment @Version field on " + item.getClass().getName(), e);
            }
        }
    }

    /**
     * Checks whether the given item has a field annotated with {@link Version}.
     *
     * @param item the item to check
     * @param <T>  the item type
     * @return true if the item has a @Version field
     */
    public static <T> boolean isVersioned(@NonNull T item) {
        return hasVersionAnnotation(item.getClass());
    }

    private static boolean hasVersionAnnotation(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(Version.class));
    }

    @SuppressWarnings("java:S3011")
    private static Optional<Integer> getAnnotatedVersion(@NonNull Object item) {
        return getAnnotatedVersionField(item.getClass())
                .map(f -> {
                    try {
                        f.setAccessible(true);
                        return (Integer) f.get(item);
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    private static Optional<Field> getAnnotatedVersionField(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Version.class))
                .findFirst();
    }

    /**
     * Builds a condition expression that verifies the version hasn't changed.
     * <p>
     * For a new item (version == null or 0): attribute_not_exists(version)
     * For an existing item (version &gt; 0): version = :expectedVersion
     *
     * @param item the item being written
     * @return a condition expression, or null if version is not applicable
     */
    @Nullable
    public static ConditionExpression buildCondition(@NonNull Object item) {
        Integer version = getVersion(item);
        if (version == null) {
            return null;
        }
        if (version <= 0) {
            return ConditionExpression.builder()
                    .notExists(VERSION_ATTR)
                    .build();
        }
        return ConditionExpression.builder()
                .eq(VERSION_ATTR, version)
                .build();
    }

}
