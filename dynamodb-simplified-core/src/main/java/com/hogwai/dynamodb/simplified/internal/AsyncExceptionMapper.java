package com.hogwai.dynamodb.simplified.internal;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Centralized utility for consistent async exception mapping across all async builders.
 * <p>
 * Ensures every async builder wraps exceptions with the same semantics:
 * <ul>
 *   <li>{@link ConditionalCheckFailedException} → {@link ConditionFailedException}</li>
 *   <li>{@link DynamoDbException} → {@link OperationFailedException}</li>
 *   <li>All other {@link RuntimeException} → rethrown as-is</li>
 *   <li>Checked exceptions → wrapped in {@link DynamoSimplifiedException}</li>
 * </ul>
 */
public final class AsyncExceptionMapper {

    private AsyncExceptionMapper() {
    }

    /**
     * Wraps a {@link Throwable} from a {@link java.util.concurrent.CompletableFuture#exceptionally}
     * handler into the appropriate {@link DynamoSimplifiedException} subtype.
     *
     * @param operation the DynamoDB operation name (e.g., "Query", "UpdateItem")
     * @param tableName the table name (nullable for cross-table operations)
     * @param cause     the throwable from the exceptionally handler
     * @return never returns: always throws
     */
    public static @NonNull RuntimeException mapException(
            @NonNull String operation, @Nullable String tableName, @NonNull Throwable cause) {
        Throwable unwrapped = cause;
        if (unwrapped instanceof CompletionException ce) {
            unwrapped = ce.getCause() != null ? ce.getCause() : ce;
        }

        if (unwrapped instanceof ConditionalCheckFailedException ccf) {
            return ConditionFailedException.fromSdk(ccf);
        }
        if (unwrapped instanceof DynamoDbException dde) {
            return new OperationFailedException(operation, tableName, dde);
        }
        if (unwrapped instanceof RuntimeException re) {
            return re;
        }
        return new DynamoSimplifiedException(operation + " failed", unwrapped);
    }

    /**
     * Creates a {@link Function} suitable for passing to
     * {@link java.util.concurrent.CompletableFuture#exceptionally}.
     *
     * @param <T>       the future result type
     * @param operation the DynamoDB operation name
     * @param tableName the table name (nullable)
     * @return a function that re-throws the wrapped exception
     */
    public static @NonNull <T> Function<Throwable, T> handler(
            @NonNull String operation, @Nullable String tableName) {
        return e -> {
            throw mapException(operation, tableName, e);
        };
    }
}
