package dev.hogwai.dynamodb.simplified.internal;

import org.jspecify.annotations.NonNull;

/**
 * DynamoDB API operation names used for logging and error reporting.
 */
public enum DynamoDbOperations {

    /**
     * DynamoDB Query API.
     */
    QUERY("Query"),
    /** DynamoDB Scan API. */
    SCAN("Scan"),
    /** DynamoDB PutItem API. */
    PUT_ITEM("PutItem"),
    /** DynamoDB GetItem API. */
    GET_ITEM("GetItem"),
    /** DynamoDB UpdateItem API. */
    UPDATE_ITEM("UpdateItem"),
    /** DynamoDB DeleteItem API. */
    DELETE_ITEM("DeleteItem"),
    /** DynamoDB BatchGetItem API. */
    BATCH_GET_ITEM("BatchGetItem"),
    /** DynamoDB BatchWriteItem API. */
    BATCH_WRITE_ITEM("BatchWriteItem"),
    /** DynamoDB TransactWriteItems API. */
    TRANSACT_WRITE("TransactWrite"),
    /** DynamoDB TransactGetItems API. */
    TRANSACT_GET("TransactGet"),
    /** DynamoDB UpdateTimeToLive API. */
    UPDATE_TIME_TO_LIVE("UpdateTimeToLive"),
    /** DynamoDB DescribeTimeToLive API. */
    DESCRIBE_TIME_TO_LIVE("DescribeTimeToLive"),
    /** Simplified entity put operation. */
    ENTITY_PUT("EntityPut"),
    /** Simplified entity delete operation. */
    ENTITY_DELETE("EntityDelete"),
    /** Simplified entity query operation. */
    ENTITY_QUERY("EntityQuery");

    private final String operationName;

    DynamoDbOperations(@NonNull String operationName) {
        this.operationName = operationName;
    }

    /**
     * Returns the DynamoDB API operation name string.
     *
     * @return the operation name (e.g., {@code "Query"}, {@code "PutItem"})
     */
    @NonNull
    public String getOperationName() {
        return operationName;
    }

    @Override
    @NonNull
    public String toString() {
        return operationName;
    }
}
