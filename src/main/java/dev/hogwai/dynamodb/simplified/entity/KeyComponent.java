package dev.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.*;

/**
 * Marks a field or getter method as contributing to a composite key component.
 * <p>
 * The {@link #component()} value identifies the key component name (e.g.
 * {@code "PK"} or {@code "SK"}). The {@link #position()} defines the
 * ordering of parts within a composite key. Multiple fields or methods
 * with the same component name and different positions are concatenated
 * with {@code #} separators at runtime.
 * <p>
 * Used together with {@link Entity @Entity} for single-table designs.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KeyComponent {

    /**
     * The key component name (e.g. {@code "PK"} or {@code "SK"}).
     *
     * @return the component name
     */
    @NonNull String component();

    /**
     * The ordering position within the composite key (lower values come first).
     *
     * @return the position index
     */
    int position() default 0;
}
