package com.hogwai.dynamodb.simplified.async;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;

/**
 * Typed wrapper for an async DynamoDB secondary index (GSI or LSI).
 * <p>
 * Obtained via {@link AsyncTable#index(String)}. Provides query and scan
 * builders scoped to this index.
 *
 * @param <T> the item type
 */
public class AsyncIndex<T> {
    private final DynamoDbAsyncIndex<T> dynamoDbAsyncIndex;

    /** Package-private constructor. */
    AsyncIndex(@NonNull DynamoDbAsyncIndex<T> dynamoDbAsyncIndex) {
        this.dynamoDbAsyncIndex = dynamoDbAsyncIndex;
    }

    /**
     * Returns the underlying {@link DynamoDbAsyncIndex}.
     *
     * @return the raw async index
     */
    @NonNull
    public DynamoDbAsyncIndex<T> getRawIndex() {
        return dynamoDbAsyncIndex;
    }

    /**
     * Starts building an async query operation on this index.
     *
     * @return an {@link AsyncQueryBuilder} for configuring and executing the query
     */
    @NonNull
    public AsyncQueryBuilder<T> query() {
        return new AsyncQueryBuilder<>(dynamoDbAsyncIndex);
    }

    /**
     * Starts building an async scan operation on this index.
     *
     * @return an {@link AsyncScanBuilder} for configuring and executing the scan
     */
    @NonNull
    public AsyncScanBuilder<T> scan() {
        return new AsyncScanBuilder<>(dynamoDbAsyncIndex);
    }
}
