package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.builder.base.AbstractTransactGetBuilder;
import dev.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.internal.DefaultDocument;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;

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
public class AsyncTransactGetBuilder extends AbstractTransactGetBuilder<AsyncTransactGetBuilder> {

    private static final Logger LOG = Logging.getLogger(AsyncTransactGetBuilder.class);

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    /**
     * Constructs a new {@code AsyncTransactGetBuilder}.
     *
     * @param enhancedClient the enhanced async DynamoDB client
     */
    public AsyncTransactGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.dynamoDbAsyncClient = null;
    }

    /**
     * Constructs a new {@code AsyncTransactGetBuilder} with the low-level async client
     * for projection support.
     *
     * @param enhancedClient      the enhanced async DynamoDB client
     * @param dynamoDbAsyncClient the low-level async DynamoDB client
     */
    public AsyncTransactGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient,
                                   @NonNull DynamoDbAsyncClient dynamoDbAsyncClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    protected @NonNull AsyncTransactGetBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Entry entry) {
        return ((DynamoDbAsyncTable<?>) entry.table()).tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Entry entry) {
        return ((DynamoDbAsyncTable<?>) entry.table()).tableSchema();
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
        entries.add(new Entry(table.getRawTable(), buildKey(partitionKey, null)));
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
        entries.add(new Entry(table.getRawTable(), buildKey(partitionKey, sortKey)));
        return this;
    }

    /**
     * Executes the transactional get operation asynchronously.
     * <p>
     * If any item has a projection expression configured via {@link #project(String...)},
     * the operation falls back to the low-level DynamoDB API to support attribute filtering.
     *
     * @return a {@link CompletableFuture} containing a {@link TransactGetResults} object
     * providing typed access to retrieved items
     */
    @NonNull
    public CompletableFuture<TransactGetResults<DynamoDbAsyncTable<?>>> execute() {
        long start = System.nanoTime();
        boolean hasProjection = entries.stream()
                .anyMatch(AbstractTransactGetBuilder::hasNonEmptyProjection);

        if (hasProjection) {
            if (dynamoDbAsyncClient == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Projection expressions require a low-level DynamoDbAsyncClient. "
                                + "Use AsyncDynamoSimplifiedClient.transactGet() to create an AsyncTransactGetBuilder."));
            }
            return executeLowLevel().thenApply(results -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("AsyncTransactGet (low-level) completed in {}ms ({} entries)",
                            (System.nanoTime() - start) / 1_000_000, entries.size());
                }
                return results;
            });
        }

        TransactGetItemsEnhancedRequest.Builder request = TransactGetItemsEnhancedRequest.builder();
        for (Entry entry : entries) {
            request.addGetItem((DynamoDbAsyncTable<?>) entry.table(), entry.key());
        }

        return enhancedClient.transactGetItems(request.build())
                .thenApply(documents -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("AsyncTransactGet (enhanced) completed in {}ms ({} entries)",
                                (System.nanoTime() - start) / 1_000_000, entries.size());
                    }
                    List<DynamoDbAsyncTable<?>> tables = new ArrayList<>(entries.size());
                    for (Entry entry : entries) {
                        tables.add((DynamoDbAsyncTable<?>) entry.table());
                    }
                    return new TransactGetResults<>(documents, tables);
                })
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.TRANSACT_GET.getOperationName(), null));
    }

    private CompletableFuture<TransactGetResults<DynamoDbAsyncTable<?>>> executeLowLevel() {
        List<TransactGetItem> transactItems = buildTransactItems();

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(transactItems)
                .build();

        return dynamoDbAsyncClient.transactGetItems(request)
                .thenApply(response -> {
                    List<Document> documents = new ArrayList<>(entries.size());
                    for (ItemResponse itemResponse : response.responses()) {
                        Document doc = itemResponse.hasItem()
                                ? DefaultDocument.create(itemResponse.item())
                                : DefaultDocument.create(java.util.Collections.emptyMap());
                        documents.add(doc);
                    }
                    List<DynamoDbAsyncTable<?>> tables = new ArrayList<>(entries.size());
                    for (Entry entry : entries) {
                        tables.add((DynamoDbAsyncTable<?>) entry.table());
                    }
                    List<ConsumedCapacity> consumedCapacities = response.consumedCapacity();
                    ConsumedCapacity consumedCapacity = consumedCapacities.isEmpty() ? null : consumedCapacities.getFirst();
                    return new TransactGetResults<>(documents, tables, consumedCapacity);
                })
                .exceptionally(AsyncExceptionMapper.handler(DynamoDbOperations.TRANSACT_GET.getOperationName(), null));
    }
}
