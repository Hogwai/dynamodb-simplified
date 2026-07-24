package dev.hogwai.dynamodb.simplified.exception;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.io.Serial;

/**
 * Thrown when a DynamoDB operation fails because the requested resource
 * (table, item, index, etc.) was not found.
 * <p>
 * Wraps {@link software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException}
 * from the AWS SDK.
 */
public class ResourceNotFoundException extends DynamoSimplifiedException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new resource not found exception for the given operation and table.
     *
     * @param operation the operation being performed (e.g., "Query", "PutItem")
     * @param tableName the table name, or null if not applicable
     * @param cause     the SDK {@link AwsServiceException}
     */
    public ResourceNotFoundException(String operation, @Nullable String tableName, AwsServiceException cause) {
        super("Resource not found for operation '" + operation + "'"
                + (tableName != null ? " on table '" + tableName + "'" : ""), cause);
    }
}
