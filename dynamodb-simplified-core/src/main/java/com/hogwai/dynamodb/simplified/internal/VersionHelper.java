package com.hogwai.dynamodb.simplified.internal;

import com.hogwai.dynamodb.simplified.Versioned;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper for optimistic locking via version fields.
 */
public final class VersionHelper {

    private static final String VERSION_ATTR = "version";

    private VersionHelper() {}

    /**
     * Returns the version from a potentially-versioned item.
     *
     * @param item the item (may implement Versioned)
     * @return the version, or null if the item is not versioned or version is null
     */
    @Nullable
    public static Integer getVersion(@NonNull Object item) {
        if (item instanceof Versioned v) {
            return v.getVersion();
        }
        return null;
    }

    /**
     * Increments the version on a {@link Versioned} item.
     *
     * @param item the item to update
     */
    public static void incrementVersion(@NonNull Versioned item) {
        Integer current = item.getVersion();
        item.setVersion(current != null ? current + 1 : 1);
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
