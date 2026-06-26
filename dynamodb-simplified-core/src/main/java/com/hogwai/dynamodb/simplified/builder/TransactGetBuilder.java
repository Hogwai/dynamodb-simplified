package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.DefaultDocument;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.Get;
import software.amazon.awssdk.services.dynamodb.model.ItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds a transactional get operation that reads up to 100 items atomically
 * across one or more tables.
 * <p>
 * Obtain via {@link com.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactGet()}.
 * <p>
 * All items are retrieved in a single all-or-nothing transaction.
 * If any item cannot be read, the entire transaction fails and no results are returned.
 * <p>
 * <b>Note:</b> DynamoDB's TransactGetItems API does not support ConsistentRead.
 * This is an API-level limitation and cannot be worked around through any code path.
 */
public class TransactGetBuilder {

    private static final Logger LOG = Logging.getLogger(TransactGetBuilder.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private final List<Entry<?>> entries = new ArrayList<>();

    public TransactGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
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
     * Restricts the returned attributes for the most recently added item
     * to the specified attribute names.
     * <p>
     * Projection is applied server-side, reducing data transfer and consumed capacity.
     *
     * @param attributes the attribute names to include
     * @return this builder
     * @throws IllegalStateException if no items have been added yet
     */
    public @NonNull TransactGetBuilder project(@NonNull String... attributes) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("No items have been added. Call addGetItem() first.");
        }
        ProjectionExpression expression = ProjectionExpression.builder().include(attributes);
        Entry<?> lastEntry = entries.removeLast();
        entries.add(new Entry<>(lastEntry.table, lastEntry.key, expression));
        return this;
    }

    /**
     * Restricts the returned attributes for the most recently added item
     * using a {@link ProjectionExpression} builder consumer.
     *
     * @param consumer a consumer to configure the projection expression
     * @return this builder
     * @throws IllegalStateException if no items have been added yet
     */
    public @NonNull TransactGetBuilder project(@NonNull Consumer<ProjectionExpression> consumer) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("No items have been added. Call addGetItem() first.");
        }
        ProjectionExpression expression = ProjectionExpression.builder();
        consumer.accept(expression);
        Entry<?> lastEntry = entries.removeLast();
        entries.add(new Entry<>(lastEntry.table, lastEntry.key, expression));
        return this;
    }

    /**
     * Executes the transactional get operation.
     * <p>
     * If any item has a projection expression configured via {@link #project(String...)},
     * the operation falls back to the low-level DynamoDB API to support attribute filtering.
     *
     * @return a {@link TransactGetResults} object providing typed access to retrieved items
     */
    public @NonNull TransactGetResults<DynamoDbTable<?>> execute() {
        long start = System.nanoTime();
        boolean hasProjection = entries.stream()
                .anyMatch(TransactGetBuilder::hasNonEmptyProjection);

        if (hasProjection) {
            TransactGetResults<DynamoDbTable<?>> results = executeLowLevel();
            if (LOG.isDebugEnabled()) {
                LOG.debug("TransactGet (low-level) completed in {}ms ({} entries)",
                        (System.nanoTime() - start) / 1_000_000, entries.size());
            }
            return results;
        }

        TransactGetItemsEnhancedRequest.Builder request = TransactGetItemsEnhancedRequest.builder();
        for (Entry<?> entry : entries) {
            request.addGetItem(entry.table, entry.key);
        }
        List<Document> documents;
        try {
            documents = enhancedClient.transactGetItems(request.build());
        } catch (DynamoDbException e) {
            throw new OperationFailedException("TransactGet", null, e);
        }

        List<DynamoDbTable<?>> tables = new ArrayList<>(entries.size());
        for (Entry<?> entry : entries) {
            tables.add(entry.table);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("TransactGet (enhanced) completed in {}ms ({} entries)",
                    (System.nanoTime() - start) / 1_000_000, entries.size());
        }
        return new TransactGetResults<>(documents, tables);
    }

    private TransactGetResults<DynamoDbTable<?>> executeLowLevel() {
        List<TransactGetItem> transactItems = new ArrayList<>();
        for (Entry<?> entry : entries) {
            ProjectionExpression projection = entry.projectionExpression;
            Get.Builder getBuilder = Get.builder()
                    .tableName(entry.table.tableName())
                    .key(entry.key.primaryKeyMap(entry.table.tableSchema()));

            if (projection != null && !projection.isEmpty()) {
                getBuilder
                        .projectionExpression(projection.getExpression())
                        .expressionAttributeNames(projection.getExpressionNames());
            }

            transactItems.add(TransactGetItem.builder().get(_ -> getBuilder.build()).build());
        }

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(transactItems)
                .build();

        TransactGetItemsResponse response;
        try {
            response = dynamoDbClient.transactGetItems(request);
        } catch (DynamoDbException e) {
            throw new OperationFailedException("TransactGet", null, e);
        }

        List<Document> documents = new ArrayList<>(entries.size());
        for (ItemResponse itemResponse : response.responses()) {
            Document doc = itemResponse.hasItem()
                ? DefaultDocument.create(itemResponse.item())
                : DefaultDocument.create(java.util.Collections.emptyMap());
            documents.add(doc);
        }

        List<DynamoDbTable<?>> tables = new ArrayList<>(entries.size());
        for (Entry<?> entry : entries) {
            tables.add(entry.table);
        }

        return new TransactGetResults<>(documents, tables);
    }

    private static boolean hasNonEmptyProjection(Entry<?> entry) {
        return entry.projectionExpression != null && !entry.projectionExpression.isEmpty();
    }

    private static Key buildKey(Object partitionKey, Object sortKey) {
        Key.Builder builder = Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey));
        if (sortKey != null) {
            builder.sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey));
        }
        return builder.build();
    }


    private record Entry<T>(DynamoDbTable<T> table, Key key, @Nullable ProjectionExpression projectionExpression) {
            Entry(DynamoDbTable<T> table, Key key) {
                this(table, key, null);
            }

    }
}
