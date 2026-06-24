package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds an async transactional get operation that reads up to 100 items atomically
 * across one or more tables.
 * <p>
 * Obtain via {@link AsyncDynamoSimplifiedClient#transactGet()}. All items are retrieved
 * in a single all-or-nothing transaction. If any item cannot be read, the entire
 * transaction fails and no results are returned.
 */
public class AsyncTransactGetBuilder {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final List<Entry<?>> entries = new ArrayList<>();

    /**
     * Constructs a new {@code AsyncTransactGetBuilder}.
     *
     * @param enhancedClient the enhanced async DynamoDB client
     */
    public AsyncTransactGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    /**
     * Adds an item to retrieve by partition key.
     *
     * @param table        the typed async table to read from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactGetBuilder addGetItem(@NonNull AsyncTable<T> table, @NonNull Object partitionKey) {
        entries.add(new Entry<>(table.getRawTable(), buildKey(partitionKey, null)));
        return this;
    }

    /**
     * Adds an item to retrieve by partition and sort key.
     *
     * @param table        the typed async table to read from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    @NonNull
    public <T> AsyncTransactGetBuilder addGetItem(@NonNull AsyncTable<T> table, @NonNull Object partitionKey, @NonNull Object sortKey) {
        entries.add(new Entry<>(table.getRawTable(), buildKey(partitionKey, sortKey)));
        return this;
    }

    /**
     * Executes the transactional get operation asynchronously.
     *
     * @return a {@link CompletableFuture} containing a {@link TransactGetResults} object
     *         providing typed access to retrieved items
     */
    @NonNull
    public CompletableFuture<TransactGetResults> execute() {
        TransactGetItemsEnhancedRequest.Builder request = TransactGetItemsEnhancedRequest.builder();
        for (Entry<?> entry : entries) {
            request.addGetItem(entry.table, entry.key);
        }

        return enhancedClient.transactGetItems(request.build())
                .thenApply(documents -> {
                    List<DynamoDbAsyncTable<?>> tables = new ArrayList<>(entries.size());
                    for (Entry<?> entry : entries) {
                        tables.add(entry.table);
                    }
                    return new TransactGetResults(documents, tables);
                });
    }

    private static Key buildKey(Object partitionKey, Object sortKey) {
        Key.Builder builder = Key.builder().partitionValue(toAttributeValue(partitionKey));
        if (sortKey != null) {
            builder.sortValue(toAttributeValue(sortKey));
        }
        return builder.build();
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }

    private static class Entry<T> {
        final DynamoDbAsyncTable<T> table;
        final Key key;

        Entry(DynamoDbAsyncTable<T> table, Key key) {
            this.table = table;
            this.key = key;
        }
    }
}
