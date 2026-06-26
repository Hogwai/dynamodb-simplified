package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
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
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<T> table;
    private final List<Key> keys = new ArrayList<>();
    private Boolean consistentRead;

    /**
     * Constructs a new {@code AsyncBatchGetBuilder}.
     *
     * @param enhancedClient the enhanced async DynamoDB client
     * @param table          the async DynamoDB table
     */
    AsyncBatchGetBuilder(@NonNull DynamoDbEnhancedAsyncClient enhancedClient, @NonNull DynamoDbAsyncTable<T> table) {
        this.enhancedClient = enhancedClient;
        this.table = table;
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

        ReadBatch readBatch = buildReadBatch();

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatch)
                .build();

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

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(buildReadBatch())
                .build();

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
                            new OperationFailedException("BatchGetItem", table.tableName(), dde));
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
                    new OperationFailedException("BatchGetItem", table.tableName(), dde));
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
