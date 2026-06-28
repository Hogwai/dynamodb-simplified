package com.hogwai.dynamodb.simplified.exception;

import org.jspecify.annotations.Nullable;

import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.io.Serial;

/**
 * Thrown when a conditional write (put/update/delete with a condition expression)
 * fails because the condition is not met.
 * <p>
 * Wraps {@link ConditionalCheckFailedException} from the AWS SDK.
 */
public class ConditionFailedException extends DynamoSimplifiedException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new condition failed exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ConditionFailedException(@Nullable String message) {
        super(message);
    }

    /**
     * Constructs a new condition failed exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause (typically a {@link ConditionalCheckFailedException})
     */
    public ConditionFailedException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a ConditionFailedException from an SDK
     * {@link ConditionalCheckFailedException}.
     *
     * @param cause the SDK condition check failure exception
     * @return a new ConditionFailedException wrapping the cause
     */
    public static ConditionFailedException fromSdk(ConditionalCheckFailedException cause) {
        return new ConditionFailedException(
                "Conditional check failed: " + cause.getMessage(), cause);
    }
}
