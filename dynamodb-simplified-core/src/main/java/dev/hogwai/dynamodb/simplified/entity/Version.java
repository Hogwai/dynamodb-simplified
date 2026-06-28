package dev.hogwai.dynamodb.simplified.entity;

import java.lang.annotation.*;

/**
 * Marks an attribute as the version field for optimistic locking.
 * <p>
 * When present and optimistic locking is enabled via
 * {@code withOptimisticLocking()}, the library automatically adds a condition
 * expression that checks the version hasn't changed, and increments the version
 * on successful write.
 * <p>
 * The annotated field must be of type {@link Integer} or {@code int}.
 * <pre>{@code
 * @DynamoDbBean
 * public class Item {
 *     @Version
 *     private Integer version;
 * }
 * }</pre>
 */
@SuppressWarnings("unused")
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Version {
}
