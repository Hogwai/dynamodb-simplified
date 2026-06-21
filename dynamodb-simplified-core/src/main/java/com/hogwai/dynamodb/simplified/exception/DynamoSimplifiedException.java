package com.hogwai.dynamodb.simplified.exception;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Base exception for all DynamoDB Simplified library errors.
 * <p>
 * Wraps AWS SDK exceptions (e.g., {@link software.amazon.awssdk.services.dynamodb.model.DynamoDbException})
 * and other internal failures into a consistent exception hierarchy.
 */
public class DynamoSimplifiedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DynamoSimplifiedException(@Nullable String message) {
        super(message);
    }

    public DynamoSimplifiedException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public DynamoSimplifiedException(@Nullable Throwable cause) {
        super(cause);
    }
}
