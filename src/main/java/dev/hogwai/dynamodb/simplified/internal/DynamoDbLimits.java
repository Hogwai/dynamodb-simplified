package dev.hogwai.dynamodb.simplified.internal;

/**
 * Shared constants for DynamoDB resource limits and retry configuration.
 */
public final class DynamoDbLimits {

    /** Maximum items per batch get request (DynamoDB hard limit). */
    public static final int BATCH_GET_MAX_SIZE = 100;

    /** Maximum items per batch write request (DynamoDB hard limit). */
    public static final int BATCH_WRITE_MAX_SIZE = 25;

    /** Default number of retry attempts for batch operations. */
    public static final int MAX_RETRIES = 3;

    /** Default base backoff delay in milliseconds. */
    public static final long BASE_BACKOFF_MS = 100L;

    private DynamoDbLimits() {
    }
}
