package dev.hogwai.dynamodb.simplified.internal;

import org.jspecify.annotations.NonNull;

/**
 * DynamoDB API operation names used for logging and error reporting.
 */
public enum DynamoDbOperations {

    QUERY("Query"),
    SCAN("Scan"),
    PUT_ITEM("PutItem"),
    GET_ITEM("GetItem"),
    UPDATE_ITEM("UpdateItem"),
    DELETE_ITEM("DeleteItem"),
    BATCH_GET_ITEM("BatchGetItem"),
    BATCH_WRITE_ITEM("BatchWriteItem"),
    TRANSACT_WRITE("TransactWrite"),
    TRANSACT_GET("TransactGet"),
    UPDATE_TIME_TO_LIVE("UpdateTimeToLive"),
    DESCRIBE_TIME_TO_LIVE("DescribeTimeToLive"),
    ENTITY_PUT("EntityPut"),
    ENTITY_DELETE("EntityDelete"),
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
