package com.hogwai.dynamodb.simplified.builder;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;

/**
 * Typed wrapper for a DynamoDB secondary index (GSI or LSI).
 * <p>
 * Obtained via {@code table.index("indexName")}. Provides fluent query and scan
 * builders scoped to this index.
 *
 * @param <T> the item type
 */
public class Index<T> {
    private final DynamoDbIndex<T> dynamoDbIndex;

    /**
     * Constructs a new {@code Index} wrapping the given {@link DynamoDbIndex}.
     *
     * @param dynamoDbIndex the DynamoDB index to wrap
     */
    public Index(@NonNull DynamoDbIndex<T> dynamoDbIndex) {
        this.dynamoDbIndex = dynamoDbIndex;
    }

    /**
     * Starts building a query operation on this index.
     *
     * @return a query builder pre-configured for this index
     */
    @NonNull
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(dynamoDbIndex);
    }

    /**
     * Starts building a scan operation on this index.
     *
     * @return a scan builder pre-configured for this index
     */
    @NonNull
    public ScanBuilder<T> scan() {
        return new ScanBuilder<>(dynamoDbIndex);
    }
}
