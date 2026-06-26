package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import com.hogwai.dynamodb.simplified.internal.AsyncExceptionMapper;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.Logging;
import com.hogwai.dynamodb.simplified.result.BatchGetResult;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Builds an async batch get operation to retrieve multiple items from a single table by their keys.
 * <p>
 * Obtain via {@link AsyncTable#batchGet()}. Executes using the async enhanced client and
 * returns a {@link CompletableFuture} that completes when all pages have been collected.
 *
 * @param <T> the item type
 */
public class AsyncBatchGetBuilder<T> {

    private static final Logger LOG = Logging.getLogger(AsyncBatchGetBuilder.class);
    private static final int MAX_BATCH_SIZE = 100;
    public static final String BATCH_GET_ITEM = "BatchGetItem";

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<T> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final List<Key> keys = new ArrayList<>();
    private Boolean consistentRead;
    private ProjectionExpression projectionExpression;
    private ReturnConsumedCapacity returnConsumedCapacity;

    /**
     * Constructs a new {@code AsyncBatchGetBuilder}.
     *
     * @param enhancedClient the enhanced async DynamoDB client
     * @param table          the async DynamoDB table
     */
    AsyncBatchGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient, @NonNull DynamoDbAsyncTable<T> table) {
        this(enhancedClient, table, null);
    }

    /**
     * Constructs a new {@code AsyncBatchGetBuilder} with a low-level client for projection support.
     *
     * @param enhancedClient     the enhanced async DynamoDB client
     * @param table              the async DynamoDB table
     * @param dynamoDbAsyncClient the low-level async DynamoDB client (nullable, required for projection)
     */
    AsyncBatchGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient, @NonNull DynamoDbAsyncTable<T> table,
                         DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.enhancedClient = enhancedClient;
        this.table = table;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    /**
     * Adds a collection of partition keys to retrieve (without sort keys).
     *
     * @param partitionKeys the partition key values
     * @return this builder
     */
    @NonNull
    public AsyncBatchGetBuilder<T> keys(@NonNull List<Object> partitionKeys) {
        for (Object pk : partitionKeys) {
            this.keys.add(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(pk)).build());
        }
        return this;
    }

    /**
     * Adds a key to retrieve by partition key only.
     *
     * @param partitionKey the partition key value
     * @return this builder
     */
    @NonNull
    public AsyncBatchGetBuilder<T> addKey(@NonNull Object partitionKey) {
        this.keys.add(Key.builder().partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey)).build());
        return this;
    }

    /**
     * Adds a key to retrieve by partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    @NonNull
    public AsyncBatchGetBuilder<T> addKey(@NonNull Object partitionKey, @NonNull Object sortKey) {
        this.keys.add(Key.builder()
                .partitionValue(AttributeValueConverter.toKeyAttributeValue(partitionKey))
                .sortValue(AttributeValueConverter.toKeyAttributeValue(sortKey))
                .build());
        return this;
    }

    /**
     * Enables or disables strongly consistent reads for this batch get.
     * <p>
     * Note: This setting is applied per-item in the batch. If not set, the
     * default (eventually consistent) is used by the DynamoDB API.
     *
     * @param consistentRead {@code true} for strongly consistent reads
     * @return this builder
     */
    @NonNull
    public AsyncBatchGetBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    /**
     * Restricts the returned attributes to the specified ones for this batch get.
     * <p>
     * When projection is set, the operation falls back to the low-level DynamoDB API.
     *
     * @param attributes the attribute names to include in each result
     * @return this builder for chaining
     */
    @NonNull
    public AsyncBatchGetBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer for this batch get.
     * <p>
     * When projection is set, the operation falls back to the low-level DynamoDB API.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    @NonNull
    public AsyncBatchGetBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public AsyncBatchGetBuilder<T> returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return this;
    }

    /**
     * Executes the batch get operation asynchronously and returns all matching items
     * aggregated from all pages.
     *
     * @return a {@link CompletableFuture} containing the {@link BatchGetResult}
     * @throws IllegalArgumentException if more than 100 keys are provided
     */
    @NonNull
    public CompletableFuture<BatchGetResult<T>> execute() {
        long start = System.nanoTime();
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(new BatchGetResult<>(List.of(), Map.of()));
        }

        if (keys.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "BatchGet supports a maximum of " + MAX_BATCH_SIZE + " keys per request, but " + keys.size() + " were provided");
        }

        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            return executeWithProjection();
        }

        ReadBatch readBatch = buildReadBatch();

        BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatch);
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        BatchGetItemEnhancedRequest request = requestBuilder.build();

        BatchGetResultPagePublisher publisher = enhancedClient.batchGetItem(request);

        CompletableFuture<BatchGetResult<T>> resultFuture = new CompletableFuture<>();
        List<T> allItems = Collections.synchronizedList(new ArrayList<>());

        publisher.subscribe(createBatchGetSubscriber(resultFuture, allItems, start));

        return resultFuture;
    }

    /**
     * Executes the batch get operation and returns a single page of results
     * along with any unprocessed keys.
     * <p>
     * Since batch get does not use traditional cursor-based pagination, the
     * last evaluated key is always {@code null}. If there are unprocessed keys,
     * they are not automatically retried.
     *
     * @return a {@link CompletableFuture} containing a {@link PagedResult} with
     *         items from the first response page
     */
    @NonNull
    public CompletableFuture<PagedResult<T>> executeWithPagination() {
        long start = System.nanoTime();
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(new PagedResult<>(Collections.emptyList(), null));
        }

        BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder()
                .readBatches(buildReadBatch());
        if (returnConsumedCapacity != null) {
            requestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        BatchGetItemEnhancedRequest request = requestBuilder.build();

        BatchGetResultPagePublisher publisher = enhancedClient.batchGetItem(request);

        CompletableFuture<PagedResult<T>> resultFuture = new CompletableFuture<>();

        publisher.subscribe(createPaginationSubscriber(resultFuture, start));

        return resultFuture;
    }

    private Subscriber<BatchGetResultPage> createBatchGetSubscriber(
            CompletableFuture<BatchGetResult<T>> resultFuture, List<T> allItems, long start) {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(BatchGetResultPage page) {
                allItems.addAll(page.resultsForTable(table));
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof DynamoDbException dde) {
                    resultFuture.completeExceptionally(
                            new OperationFailedException(BATCH_GET_ITEM, table.tableName(), dde));
                } else {
                    resultFuture.completeExceptionally(t);
                }
            }

            @Override
            public void onComplete() {
                List<T> items = List.copyOf(allItems);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("AsyncBatchGet on table '{}' returned {} items in {}ms",
                            table.tableName(), items.size(), (System.nanoTime() - start) / 1_000_000);
                }
                resultFuture.complete(new BatchGetResult<>(items, Map.of()));
            }
        };
    }

    private Subscriber<BatchGetResultPage> createPaginationSubscriber(
            CompletableFuture<PagedResult<T>> resultFuture, long start) {
        AtomicBoolean firstPageReceived = new AtomicBoolean();
        return new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(BatchGetResultPage page) {
                onNextPage(page, firstPageReceived, resultFuture, start);
            }

            @Override
            public void onError(Throwable t) {
                onPageError(t, resultFuture);
            }

            @Override
            public void onComplete() {
                onPageComplete(resultFuture, start);
            }
        };
    }

    private void onNextPage(BatchGetResultPage page,
                            AtomicBoolean firstPageReceived,
                            CompletableFuture<PagedResult<T>> resultFuture, long start) {
        if (!firstPageReceived.compareAndSet(false, true)) {
            return;
        }
        List<T> items = page.resultsForTable(table);
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncBatchGet on table '{}' returned {} items in {}ms (first page)",
                    table.tableName(), items.size(), (System.nanoTime() - start) / 1_000_000);
        }
        resultFuture.complete(new PagedResult<>(items, null));
    }

    private void onPageError(Throwable t,
                             CompletableFuture<PagedResult<T>> resultFuture) {
        if (resultFuture.isDone()) {
            return;
        }
        if (t instanceof DynamoDbException dde) {
            resultFuture.completeExceptionally(
                    new OperationFailedException(BATCH_GET_ITEM, table.tableName(), dde));
        } else {
            resultFuture.completeExceptionally(t);
        }
    }

    private void onPageComplete(CompletableFuture<PagedResult<T>> resultFuture, long start) {
        if (resultFuture.isDone()) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("AsyncBatchGet on table '{}' returned 0 items in {}ms (first page)",
                    table.tableName(), (System.nanoTime() - start) / 1_000_000);
        }
        resultFuture.complete(new PagedResult<>(Collections.emptyList(), null));
    }

    private CompletableFuture<BatchGetResult<T>> executeWithProjection() {
        String tableName = table.tableName();
        if (dynamoDbAsyncClient == null) {
            throw new IllegalStateException(
                    "Projection requires a low-level DynamoDbAsyncClient, but none was provided. " +
                    "Use the three-argument constructor or obtain the builder via AsyncTable.");
        }

        List<Map<String, AttributeValue>> sdkKeys = new ArrayList<>(keys.size());
        for (Key key : keys) {
            sdkKeys.add(key.primaryKeyMap(table.tableSchema()));
        }

        KeysAndAttributes.Builder keysAndAttributesBuilder = KeysAndAttributes.builder()
                .keys(sdkKeys)
                .projectionExpression(projectionExpression.getExpression())
                .expressionAttributeNames(projectionExpression.getExpressionNames());
        if (consistentRead != null) {
            keysAndAttributesBuilder.consistentRead(consistentRead);
        }

        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        requestItems.put(tableName, keysAndAttributesBuilder.build());

        BatchGetItemRequest.Builder lowLevelRequestBuilder = BatchGetItemRequest.builder()
                .requestItems(requestItems);
        if (returnConsumedCapacity != null) {
            lowLevelRequestBuilder.returnConsumedCapacity(returnConsumedCapacity);
        }
        BatchGetItemRequest request = lowLevelRequestBuilder.build();

        return dynamoDbAsyncClient.batchGetItem(request)
                .thenApply(response -> {
                    List<Map<String, AttributeValue>> items = response.responses()
                            .getOrDefault(tableName, List.of());
                    Map<String, KeysAndAttributes> unprocessed = response.unprocessedKeys();
                    List<T> results = new ArrayList<>(items.size());
                    for (Map<String, AttributeValue> item : items) {
                        results.add(table.tableSchema().mapToItem(item));
                    }
                    return new BatchGetResult<>(results, unprocessed != null ? unprocessed : Map.of());
                })
                .exceptionally(AsyncExceptionMapper.handler(BATCH_GET_ITEM, tableName));
    }

    private ReadBatch buildReadBatch() {
        Class<T> itemClass = table.tableSchema().itemType().rawClass();
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);
        if (consistentRead != null) {
            for (Key key : keys) {
                batchBuilder.addGetItem(b -> b.key(key).consistentRead(consistentRead));
            }
        } else {
            for (Key key : keys) {
                batchBuilder.addGetItem(key);
            }
        }
        return batchBuilder.build();
    }

}
