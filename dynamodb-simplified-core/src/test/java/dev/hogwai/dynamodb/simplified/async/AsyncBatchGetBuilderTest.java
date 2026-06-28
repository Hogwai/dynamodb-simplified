package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.result.BatchGetResult;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

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

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

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
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
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

        assertTrue(result.items().isEmpty());
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

        assertEquals(1, result.items().size());
        assertSame(expectedItem, result.items().getFirst());
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

        assertEquals(2, result.items().size());
        assertSame(item1, result.items().get(0));
        assertSame(item2, result.items().get(1));
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
        assertNull(result.lastEvaluatedKey());
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
        assertNull(result.lastEvaluatedKey());
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

    // ============ project ============

    @Test
    @DisplayName("project with varargs returns self")
    void project_withVarargs_returnsSelf() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.project("attr1", "attr2");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("project with consumer returns self")
    void project_withConsumer_returnsSelf() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);

        AsyncBatchGetBuilder<TestItem> result = builder.project(p -> p.include("attr1"));

        assertSame(builder, result);
    }

    @Test
    @DisplayName("execute with projection uses low-level path")
    void execute_withProjection_usesLowLevelPath() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("testTable");

        TestItem expectedItem = new TestItem("item1");
        when(tableSchema.mapToItem(any())).thenReturn(expectedItem);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(Map.of("testTable",
                List.of(Map.of("id", AttributeValue.builder().s("item1").build()))));
        when(response.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1").project("id");

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(1, result.items().size());
        assertSame(expectedItem, result.items().getFirst());
        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchGetItem(any(BatchGetItemRequest.class));
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute without projection uses enhanced path even when low-level client is available")
    void execute_withoutProjection_usesEnhancedPath() {
        stubTableSchema();

        TestItem expectedItem = new TestItem("item1");
        BatchGetResultPage page = mockPageWithItems(table, List.of(expectedItem));
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1");

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(1, result.items().size());
        assertSame(expectedItem, result.items().getFirst());
        verify(enhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
        verify(dynamoDbAsyncClient, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    @DisplayName("execute with projection includes projection expression in low-level request")
    void execute_withProjection_includesProjectionExpression() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("testTable");

        TestItem expectedItem = new TestItem("item1");
        when(tableSchema.mapToItem(any())).thenReturn(expectedItem);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(Map.of("testTable",
                List.of(Map.of("id", AttributeValue.builder().s("item1").build()))));
        when(response.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1").project("id");

        builder.execute().join();

        verify(dynamoDbAsyncClient).batchGetItem(argThat((BatchGetItemRequest request) ->
                !request.requestItems().isEmpty()
                        && request.requestItems().containsKey("testTable")
                        && request.requestItems().get("testTable").projectionExpression() != null
        ));
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ returnConsumedCapacity ============

    @Test
    @DisplayName("execute with returnConsumedCapacity passes it to enhanced request")
    void execute_withReturnConsumedCapacity() {
        stubTableSchema();

        TestItem expectedItem = new TestItem("item1");
        BatchGetResultPage page = mockPageWithItems(table, List.of(expectedItem));
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk").returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(1, result.items().size());
        assertSame(expectedItem, result.items().getFirst());

        ArgumentCaptor<BatchGetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(BatchGetItemEnhancedRequest.class);
        verify(enhancedClient).batchGetItem(captor.capture());
        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    @Test
    @DisplayName("executeWithPagination with returnConsumedCapacity passes it to enhanced request")
    void executeWithPagination_withReturnConsumedCapacity() {
        stubTableSchema();

        TestItem item = new TestItem("item1");
        BatchGetResultPage page = mockPageWithItems(table, List.of(item));
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk").returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

        PagedResult<TestItem> result = builder.executeWithPagination().join();

        assertEquals(1, result.size());

        ArgumentCaptor<BatchGetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(BatchGetItemEnhancedRequest.class);
        verify(enhancedClient).batchGetItem(captor.capture());
        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    // ============ execute with projection edge cases ============

    @Test
    @DisplayName("execute with projection without low-level client throws IllegalStateException")
    void execute_withProjection_withoutLowLevelClient_throws() {
        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk1").project("id");

        assertThrows(IllegalStateException.class, builder::execute);
    }

    @Test
    @DisplayName("execute with projection and consistentRead includes consistentRead in low-level request")
    void execute_withProjection_withConsistentRead() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("testTable");

        TestItem expectedItem = new TestItem("item1");
        when(tableSchema.mapToItem(any())).thenReturn(expectedItem);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(Map.of("testTable",
                List.of(Map.of("id", AttributeValue.builder().s("item1").build()))));
        when(response.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1").project("id").consistentRead(true);

        builder.execute().join();

        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());

        Map<String, KeysAndAttributes> requestItems = captor.getValue().requestItems();
        KeysAndAttributes knA = requestItems.get("testTable");
        assertNotNull(knA);
        assertTrue(knA.consistentRead());
    }

    @Test
    @DisplayName("execute with projection and returnConsumedCapacity includes it in low-level request")
    void execute_withProjection_withReturnConsumedCapacity() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("testTable");

        TestItem expectedItem = new TestItem("item1");
        when(tableSchema.mapToItem(any())).thenReturn(expectedItem);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(Map.of("testTable",
                List.of(Map.of("id", AttributeValue.builder().s("item1").build()))));
        when(response.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1").project("id").returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

        builder.execute().join();

        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());

        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    @Test
    @DisplayName("execute with projection handles null unprocessed keys gracefully")
    void execute_withProjection_whenUnprocessedKeysIsNull() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("testTable");

        TestItem expectedItem = new TestItem("item1");
        when(tableSchema.mapToItem(any())).thenReturn(expectedItem);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(Map.of("testTable",
                List.of(Map.of("id", AttributeValue.builder().s("item1").build()))));
        when(response.unprocessedKeys()).thenReturn(null);
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table, dynamoDbAsyncClient);
        builder.addKey("pk1").project("id");

        BatchGetResult<TestItem> result = builder.execute().join();

        assertEquals(1, result.items().size());
        assertFalse(result.hasUnprocessed());
    }

    // ============ publisher error handling with DynamoDbException ============

    @Test
    @DisplayName("execute completes exceptionally with OperationFailedException when publisher errors with DynamoDbException")
    void execute_whenPublisherErrors_withDynamoDbException() {
        stubTableSchema();

        DynamoDbException dde = mock(DynamoDbException.class);
        BatchGetResultPagePublisher errorPublisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onError(dde);
        };

        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(errorPublisher);

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        CompletableFuture<BatchGetResult<TestItem>> future = builder.execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(OperationFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("executeWithPagination completes exceptionally with OperationFailedException when publisher errors with DynamoDbException")
    void executeWithPagination_whenPublisherErrors_withDynamoDbException() {
        stubTableSchema();

        DynamoDbException dde = mock(DynamoDbException.class);
        BatchGetResultPagePublisher errorPublisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onError(dde);
        };

        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(errorPublisher);

        AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        CompletableFuture<PagedResult<TestItem>> future = builder.executeWithPagination();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(OperationFailedException.class, ex.getCause());
    }

    // ============ buildReadBatch with consistentRead ============

    @Test
    @DisplayName("execute with consistentRead(true) uses per-item consistentRead in batch")
    @SuppressWarnings("unchecked")
    void execute_withConsistentRead_usesConsistentReadPerItem() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);

        BatchGetResultPage page = mock(BatchGetResultPage.class);
        when(page.resultsForTable(table)).thenReturn(List.of());
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page));

        try (var ignored = mockStatic(ReadBatch.class)) {
            ReadBatch.Builder<TestItem> batchBuilder = mock(ReadBatch.Builder.class);
            ReadBatch readBatch = mock(ReadBatch.class);

            when(ReadBatch.builder(TestItem.class)).thenReturn(batchBuilder);
            when(batchBuilder.mappedTableResource(table)).thenReturn(batchBuilder);
            when(batchBuilder.build()).thenReturn(readBatch);

            AsyncBatchGetBuilder<TestItem> builder = new AsyncBatchGetBuilder<>(enhancedClient, table);
            builder.addKey("pk").consistentRead(true);

            builder.execute().join();

            ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> consumerCaptor =
                    ArgumentCaptor.forClass(Consumer.class);
            verify(batchBuilder).addGetItem(consumerCaptor.capture());

            GetItemEnhancedRequest.Builder getItemBuilder = GetItemEnhancedRequest.builder();
            consumerCaptor.getValue().accept(getItemBuilder);
            assertTrue(getItemBuilder.build().consistentRead());
        }
    }
}
