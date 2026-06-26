package com.hogwai.dynamodb.simplified.builder;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a new {@code Index} wrapping the given {@link DynamoDbIndex}.
     *
     * @param dynamoDbIndex the DynamoDB index to wrap
     */
    public Index(@NonNull DynamoDbIndex<T> dynamoDbIndex) {
        this(dynamoDbIndex, null);
    }

    /**
     * Constructs a new {@code Index} wrapping the given {@link DynamoDbIndex}
     * with a low-level client.
     *
     * @param dynamoDbIndex  the DynamoDB index to wrap
     * @param dynamoDbClient the low-level DynamoDB client (nullable)
     */
    public Index(@NonNull DynamoDbIndex<T> dynamoDbIndex, @Nullable DynamoDbClient dynamoDbClient) {
        this.dynamoDbIndex = dynamoDbIndex;
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Returns the underlying {@link DynamoDbIndex}.
     *
     * @return the raw DynamoDB index
     */
    @NonNull
    public DynamoDbIndex<T> getRawIndex() {
        return dynamoDbIndex;
    }

    /**
     * Returns the name of this index.
     *
     * @return the index name
     */
    @NonNull
    public String indexName() {
        return dynamoDbIndex.indexName();
    }

    /**
     * Returns the name of the table this index belongs to.
     *
     * @return the table name
     */
    @NonNull
    public String tableName() {
        return dynamoDbIndex.tableName();
    }

    /**
     * Starts building a query operation on this index.
     *
     * @return a query builder pre-configured for this index
     */
    @NonNull
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(dynamoDbIndex, dynamoDbClient);
    }

    /**
     * Starts building a scan operation on this index.
     *
     * @return a scan builder pre-configured for this index
     */
    @NonNull
    public ScanBuilder<T> scan() {
        return new ScanBuilder<>(dynamoDbIndex, dynamoDbClient);
    }
}
