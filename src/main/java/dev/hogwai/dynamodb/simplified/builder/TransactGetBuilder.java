package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.builder.base.AbstractTransactGetBuilder;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.internal.DefaultDocument;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a transactional get operation that reads up to 100 items atomically
 * across one or more tables.
 * <p>
 * Obtain via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactGet()}.
 * <p>
 * All items are retrieved in a single all-or-nothing transaction.
 * If any item cannot be read, the entire transaction fails and no results are returned.
 * <p>
 * <b>Note:</b> DynamoDB's TransactGetItems API does not support ConsistentRead.
 * This is an API-level limitation and cannot be worked around through any code path.
 */
public class TransactGetBuilder extends AbstractTransactGetBuilder<TransactGetBuilder> {

    private static final Logger LOG = Logging.getLogger(TransactGetBuilder.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructs a transactional get builder.
     * <p>
     * Typically obtained via {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient#transactGet()}.
     *
     * @param enhancedClient the enhanced DynamoDB client
     * @param dynamoDbClient the low-level DynamoDB client (used for projection fallback)
     */
    public TransactGetBuilder(@NonNull DynamoDbEnhancedClient enhancedClient, @NonNull DynamoDbClient dynamoDbClient) {
        super();
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    protected @NonNull TransactGetBuilder self() {
        return this;
    }

    @Override
    protected @NonNull String getTableName(@NonNull Entry entry) {
        return ((DynamoDbTable<?>) entry.table()).tableName();
    }

    @Override
    protected @NonNull TableSchema<?> getTableSchema(@NonNull Entry entry) {
        return ((DynamoDbTable<?>) entry.table()).tableSchema();
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
        entries.add(new Entry(table.getRawTable(), buildKey(partitionKey, null)));
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
        entries.add(new Entry(table.getRawTable(), buildKey(partitionKey, sortKey)));
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
                .anyMatch(AbstractTransactGetBuilder::hasNonEmptyProjection);

        if (hasProjection) {
            TransactGetResults<DynamoDbTable<?>> results = executeLowLevel();
            if (LOG.isDebugEnabled()) {
                LOG.debug("TransactGet (low-level) completed in {}ms ({} entries)",
                        (System.nanoTime() - start) / 1_000_000, entries.size());
            }
            return results;
        }

        TransactGetItemsEnhancedRequest.Builder request = TransactGetItemsEnhancedRequest.builder();
        for (Entry entry : entries) {
            request.addGetItem((DynamoDbTable<?>) entry.table(), entry.key());
        }
        List<Document> documents;
        try {
            documents = enhancedClient.transactGetItems(request.build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.TRANSACT_GET.getOperationName(), null, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.TRANSACT_GET.getOperationName(), null, e);
        }

        List<DynamoDbTable<?>> tables = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            tables.add((DynamoDbTable<?>) entry.table());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("TransactGet (enhanced) completed in {}ms ({} entries)",
                    (System.nanoTime() - start) / 1_000_000, entries.size());
        }
        return new TransactGetResults<>(documents, tables);
    }

    private TransactGetResults<DynamoDbTable<?>> executeLowLevel() {
        List<TransactGetItem> transactItems = buildTransactItems();

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(transactItems)
                .build();

        TransactGetItemsResponse response;
        try {
            response = dynamoDbClient.transactGetItems(request);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.TRANSACT_GET.getOperationName(), null, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.TRANSACT_GET.getOperationName(), null, e);
        }

        List<Document> documents = new ArrayList<>(entries.size());
        for (ItemResponse itemResponse : response.responses()) {
            Document doc = itemResponse.hasItem()
                    ? DefaultDocument.create(itemResponse.item())
                    : DefaultDocument.create(java.util.Collections.emptyMap());
            documents.add(doc);
        }

        List<DynamoDbTable<?>> tables = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            tables.add((DynamoDbTable<?>) entry.table());
        }

        List<ConsumedCapacity> consumedCapacities = response.consumedCapacity();
        ConsumedCapacity consumedCapacity = consumedCapacities.isEmpty() ? null : consumedCapacities.getFirst();
        return new TransactGetResults<>(documents, tables, consumedCapacity);
    }
}
