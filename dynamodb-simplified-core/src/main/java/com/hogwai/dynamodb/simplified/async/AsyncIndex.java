package com.hogwai.dynamodb.simplified.async;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

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
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Package-private constructor.
     */
    AsyncIndex(@NonNull DynamoDbAsyncIndex<T> dynamoDbAsyncIndex) {
        this(dynamoDbAsyncIndex, null);
    }

    /**
     * Constructs a new {@code AsyncIndex} with a low-level async client.
     *
     * @param dynamoDbAsyncIndex  the async DynamoDB index
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable)
     */
    AsyncIndex(@NonNull DynamoDbAsyncIndex<T> dynamoDbAsyncIndex, @Nullable DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncIndex = dynamoDbAsyncIndex;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
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
     * Returns the name of this index.
     *
     * @return the index name
     */
    @NonNull
    public String indexName() {
        return dynamoDbAsyncIndex.indexName();
    }

    /**
     * Returns the name of the table this index belongs to.
     *
     * @return the table name
     */
    @NonNull
    public String tableName() {
        return dynamoDbAsyncIndex.tableName();
    }

    /**
     * Starts building an async query operation on this index.
     *
     * @return an {@link AsyncQueryBuilder} for configuring and executing the query
     */
    @NonNull
    public AsyncQueryBuilder<T> query() {
        return new AsyncQueryBuilder<>(dynamoDbAsyncIndex, dynamoDbAsyncClient);
    }

    /**
     * Starts building an async scan operation on this index.
     *
     * @return an {@link AsyncScanBuilder} for configuring and executing the scan
     */
    @NonNull
    public AsyncScanBuilder<T> scan() {
        return new AsyncScanBuilder<>(dynamoDbAsyncIndex, dynamoDbAsyncClient);
    }
}
