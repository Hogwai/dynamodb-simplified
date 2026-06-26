package com.hogwai.dynamodb.simplified.exception;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Thrown when a DynamoDB operation fails due to a service-side error.
 * <p>
 * Wraps SDK exceptions such as
 * {@link software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException},
 * {@link software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException},
 * {@link software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException},
 * and {@link software.amazon.awssdk.services.dynamodb.model.DynamoDbException}.
 * The original SDK exception is available via {@link #getCause()}.
 */
public class OperationFailedException extends DynamoSimplifiedException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Wraps an SDK DynamoDbException with a descriptive message.
     *
     * @param operation the operation being performed (e.g., "Query", "PutItem")
     * @param tableName the table name, or null if not applicable
     * @param cause     the SDK exception
     */
    public OperationFailedException(@NonNull String operation,
                                     @Nullable String tableName,
                                     @NonNull Throwable cause) {
        super(buildMessage(operation, tableName, cause), cause);
    }

    private static String buildMessage(String operation, @Nullable String tableName, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(operation);
        if (tableName != null) {
            sb.append(" on table '").append(tableName).append('\'');
        }
        sb.append(" failed: ").append(cause.getMessage());
        return sb.toString();
    }
}
