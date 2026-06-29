package dev.hogwai.dynamodb.simplified.internal;

/**
 * Shared error and validation messages used across builders.
 */
public final class Messages {

    // ============ Select.COUNT validation ============

    /** Template: "Cannot call %s with Select.COUNT. Use count() instead." */
    public static final String SELECT_COUNT_FMT = "Cannot call %s with Select.COUNT. Use count() instead.";

    // ============ Partition key validation ============

    /** Message when partition key is not set before executing a query. */
    public static final String PK_NOT_SET = "Partition key value must be set before executing a query. "
            + "Call partitionKey(), partitionKeyBeginsWith(), or a similar method first.";

    // ============ Batch validation ============

    /** Template: "BatchGet supports a maximum of %d keys per request, but %d were provided" */
    public static final String BATCH_GET_SIZE_FMT = "BatchGet supports a maximum of %d keys per request, but %d were provided";

    /** Template: "CrossTable batch get supports a maximum of %d keys per request, but %d were provided" */
    public static final String CROSS_TABLE_BATCH_GET_SIZE_FMT = "CrossTable batch get supports a maximum of %d keys per request, but %d were provided";

    /** Template: "CrossTable batch write supports a maximum of %d items per request, but %d were provided" */
    public static final String CROSS_TABLE_BATCH_WRITE_SIZE_FMT = "CrossTable batch write supports a maximum of %d items per request, but %d were provided";

    /** Template: "BatchWrite supports a maximum of %d items per request, but %d were provided" */
    public static final String BATCH_WRITE_SIZE_FMT = "BatchWrite supports a maximum of %d items per request, but %d were provided";

    /** Template: "CrossTable transact write supports a maximum of %d items per request, but %d were provided" */
    public static final String CROSS_TABLE_TRANSACT_WRITE_SIZE_FMT = "CrossTable transact write supports a maximum of %d items per request, but %d were provided";

    // ============ Transact / batch empty item list ============

    /** Message when no items added to transact get. */
    public static final String NO_TRANSACT_GET_ITEMS = "No items have been added. Call addGetItem() first.";

    /** Message when no keys added to cross-table batch get. */
    public static final String NO_CROSS_TABLE_BATCH_GET_KEYS = "No entries have been added. Call addKey() or addKeys() first.";

    // ============ Enhanced client path issues ============

    /** Template: "Unexpected operation type in enhanced path: %s" */
    public static final String UNEXPECTED_OPERATION_TYPE_FMT = "Unexpected operation type in enhanced path: %s";

    // ============ Projection fallback ============

    /** Message when projection requires a low-level client but none provided. */
    public static final String PROJECTION_REQUIRES_LOW_LEVEL_CLIENT_SYNC =
            "Projection requires a low-level DynamoDbClient, but none was provided. "
                    + "Use the three-argument constructor or obtain the builder via Table.";

    /** Message when projection requires a low-level async client but none provided. */
    public static final String PROJECTION_REQUIRES_LOW_LEVEL_CLIENT_ASYNC =
            "Projection requires a low-level DynamoDbAsyncClient, but none was provided. "
                    + "Use the three-argument constructor or obtain the builder via AsyncTable.";

    private Messages() {
    }
}
