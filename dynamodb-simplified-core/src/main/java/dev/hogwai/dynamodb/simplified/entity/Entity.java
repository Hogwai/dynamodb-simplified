package dev.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.*;

/**
 * Marks a class as an entity type for single-table DynamoDB designs.
 * <p>
 * The {@link #discriminator()} value is stored in the
 * {@link #discriminatorAttribute()} attribute (default {@code "_type"})
 * and used by the entity subsystem to identify which entity type a given
 * item represents. The {@link #table()} specifies the DynamoDB table name.
 * <p>
 * When used together with {@link KeyComponent @KeyComponent}, the entity
 * subsystem automatically computes composite key values and filters
 * queries by discriminator.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entity {

    /**
     * The discriminator value stored in the discriminator attribute.
     *
     * @return the discriminator value
     */
    @NonNull String discriminator();

    /**
     * The attribute name used to store the discriminator value.
     *
     * @return the discriminator attribute name
     */
    @NonNull String discriminatorAttribute() default "_type";

    /**
     * The DynamoDB table name.
     *
     * @return the table name
     */
    @NonNull String table();
}
