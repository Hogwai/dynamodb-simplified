package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.result.BatchGetResult;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncBatchGetBuilder")
class AsyncBatchGetBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private EnhancedType<TestItem> enhancedType;

    @Mock
    private TableMetadata tableMetadata;

    // ============ Helpers ============

    /**
     * Creates a {@link BatchGetResultPagePublisher} that emits a single page and completes.
     */
    private static BatchGetResultPagePublisher publisherThatEmits(BatchGetResultPage page) {
        return subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(page);
            subscriber.onComplete();
        };
    }

    /**
     * Creates a mock BatchGetResultPage with items stubbed.
     */
    private static <T> BatchGetResultPage mockPageWithItems(DynamoDbAsyncTable<T> table, List<T> items) {
        BatchGetResultPage page = mock(BatchGetResultPage.class);
        when(page.resultsForTable(table)).thenReturn(items);
        return page;
    }

    private void stubTableSchema() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    // ============ addKey ============

    @Test
    @DisplayName("addKey with partition key returns self")
    void addKey_withPartitionKey() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.addKey("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKey with partition and sort key returns self")
    void addKey_withPartitionAndSortKey() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.addKey("pk", "sk");

        assertSame(builder, result);
    }

    // ============ keys ============

    @Test
    @DisplayName("keys with list returns self")
    void keys_withList() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.keys(List.of("pk1", "pk2"));

        assertSame(builder, result);
    }

    // ============ consistentRead ============

    @Test
    @DisplayName("consistentRead returns self")
    void consistentRead() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.consistentRead(true);

        assertSame(builder, result);
    }

    // ============ execute, empty keys ============

    @Test
    @DisplayName("execute with empty keys returns empty result and does not call batchGetItem")
    void execute_emptyKeys_returnsEmptyList() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        BatchGetResult<TestItem> result = builder.execute().join();

        assertTrue(result.getItems().isEmpty());
        assertFalse(result.hasUnprocessed());
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ execute, with keys ============

    @Test
    @DisplayName("execute with keys returns results from enhanced client")
    void execute_withKeys_returnsResults() {
        stubTableSchema();

        TestItem expectedItem = new TestItem("item1");
        BatchGetResultPage page = mockPageWithItems(table, List.of(expectedItem));
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(1, result.getItems().size());
        assertSame(expectedItem, result.getItems().getFirst());
        assertFalse(result.hasUnprocessed());
        verify(enhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute with multiple keys returns aggregated results from all pages")
    void execute_withMultiplePages() {
        stubTableSchema();

        TestItem item1 = new TestItem("item1");
        TestItem item2 = new TestItem("item2");
        BatchGetResultPage page1 = mockPageWithItems(table, List.of(item1));
        BatchGetResultPage page2 = mockPageWithItems(table, List.of(item2));

        // Create a publisher that emits two pages
        BatchGetResultPagePublisher twoPagePublisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(page1);
            subscriber.onNext(page2);
            subscriber.onComplete();
        };

        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(twoPagePublisher);

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk1").addKey("pk2");

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(2, result.getItems().size());
        assertSame(item1, result.getItems().get(0));
        assertSame(item2, result.getItems().get(1));
        assertFalse(result.hasUnprocessed());
    }

    @Test
    @DisplayName("execute completes exceptionally when publisher errors")
    void execute_whenPublisherErrors() {
        stubTableSchema();

        RuntimeException error = new RuntimeException("batch get failed");
        BatchGetResultPagePublisher errorPublisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onError(error);
        };

        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(errorPublisher);

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        CompletableFuture<BatchGetResult<TestItem>> future = builder.execute();

        assertThrows(RuntimeException.class, future::join);
    }

    // ============ executeWithPagination ============

    @Test
    @DisplayName("executeWithPagination with empty keys returns empty PagedResult")
    void executeWithPagination_emptyKeys() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        PagedResult<TestItem> result = builder.executeWithPagination().join();

        assertTrue(result.isEmpty());
        assertNull(result.getLastEvaluatedKey());
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("executeWithPagination returns first page items with null last evaluated key")
    void executeWithPagination_returnsFirstPage() {
        stubTableSchema();

        TestItem item1 = new TestItem("item1");
        TestItem item2 = new TestItem("item2");
        BatchGetResultPage page = mockPageWithItems(table, List.of(item1, item2));
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk1").addKey("pk2");

        PagedResult<TestItem> result = builder.executeWithPagination().join();

        assertEquals(2, result.size());
        assertNull(result.getLastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination completes exceptionally when publisher errors")
    void executeWithPagination_whenPublisherErrors() {
        stubTableSchema();

        RuntimeException error = new RuntimeException("batch get failed");
        BatchGetResultPagePublisher errorPublisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onError(error);
        };

        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(errorPublisher);

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        CompletableFuture<PagedResult<TestItem>> future = builder.executeWithPagination();

        assertThrows(RuntimeException.class, future::join);
    }

    // ============ limit validation ============

    @Test
    @DisplayName("execute with more than 100 keys throws IllegalArgumentException")
    void execute_exceedsKeyLimit_throws() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        for (int i = 0; i < 101; i++) {
            builder.addKey("pk" + i);
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }
}
