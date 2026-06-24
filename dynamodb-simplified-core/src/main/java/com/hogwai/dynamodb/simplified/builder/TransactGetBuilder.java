package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.result.TransactGetResults;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a transactional get operation that reads up to 100 items atomically
 * across one or more tables.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactGet()}.
 * <p>
 * All items are retrieved in a single all-or-nothing transaction.
 * If any item cannot be read, the entire transaction fails and no results are returned.
 */
public class TransactGetBuilder {

    private final DynamoDbEnhancedClient enhancedClient;
    private final List<Entry<?>> entries = new ArrayList<>();

    public TransactGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    /**
     * Adds an item to retrieve by partition key.
     *
     * @param table        the typed table to read from
     * @param partitionKey the partition key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactGetBuilder addGetItem(@NonNull Table<T> table, @NonNull Object partitionKey) {
        entries.add(new Entry<>(table.getRawTable(), buildKey(partitionKey, null)));
        return this;
    }

    /**
     * Adds an item to retrieve by partition and sort key.
     *
     * @param table        the typed table to read from
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @param <T>          the item type
     * @return this builder
     */
    public @NonNull <T> TransactGetBuilder addGetItem(@NonNull Table<T> table, @NonNull Object partitionKey, @NonNull Object sortKey) {
        entries.add(new Entry<>(table.getRawTable(), buildKey(partitionKey, sortKey)));
        return this;
    }

    /**
     * Executes the transactional get operation.
     *
     * @return a {@link TransactGetResults} object providing typed access to retrieved items
     */
    public @NonNull TransactGetResults execute() {
        TransactGetItemsEnhancedRequest.Builder request = TransactGetItemsEnhancedRequest.builder();
        for (Entry<?> entry : entries) {
            request.addGetItem(entry.table, entry.key);
        }
        List<Document> documents = enhancedClient.transactGetItems(request.build());

        List<DynamoDbTable<?>> tables = new ArrayList<>(entries.size());
        for (Entry<?> entry : entries) {
            tables.add(entry.table);
        }
        return new TransactGetResults(documents, tables);
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
        final DynamoDbTable<T> table;
        final Key key;

        Entry(DynamoDbTable<T> table, Key key) {
            this.table = table;
            this.key = key;
        }
    }
}
