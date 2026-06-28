package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.*;

/**
 * Sets a static prefix for a composite key component.
 * <p>
 * When present on a class, the {@link #value()} is prepended to the
 * computed key value for the named {@link #component()} at runtime,
 * separated by a {@code #} character.
 * <p>
 * This annotation is {@link Repeatable}, allowing multiple prefixes for
 * different key components on the same entity class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(KeyPrefix.Container.class)
public @interface KeyPrefix {

    /**
     * The key component name to which this prefix applies.
     *
     * @return the component name
     */
    @NonNull String component();

    /**
     * The static prefix string prepended to the computed key value.
     *
     * @return the prefix value
     */
    @NonNull String value();

    /**
     * Container annotation for repeatable {@link KeyPrefix @KeyPrefix}.
     *
     * @see KeyPrefix
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Container {
        /**
         * The repeatable KeyPrefix values.
         *
         * @return the array of KeyPrefix annotations
         */
        KeyPrefix[] value();
    }
}
