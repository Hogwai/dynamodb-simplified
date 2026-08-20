package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.result.BatchWriteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncBatchWriteBuilder")
class AsyncBatchWriteBuilderTest {

    // region Test Item

    static class TestItem {
        String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // endregion

    // region Mocks

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

    // endregion

    // region Helpers

    private void stubTableSchema() {
        when(table.tableSchema()).thenReturn(tableSchema);
        lenient().when(tableSchema.itemType()).thenReturn(enhancedType);
        lenient().when(enhancedType.rawClass()).thenReturn(TestItem.class);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("id");
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    private void stubItemToMap() {
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id",
                        AttributeValue.builder().s("item1").build()));
    }

    private void stubTableName() {
        when(table.tableName()).thenReturn("test_table");
    }

    // endregion

    // region put

    @Test
    @DisplayName("put adds item and returns self")
    void put_addsItem() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);
        TestItem item = new TestItem("item1");

        AsyncBatchWriteBuilder<TestItem> result = builder.put(item);

        assertSame(builder, result);
    }

    // endregion

    // region delete

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);

        AsyncBatchWriteBuilder<TestItem> result = builder.delete("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);

        AsyncBatchWriteBuilder<TestItem> result = builder.delete("pk", "sk");

        assertSame(builder, result);
    }

    // endregion

    // region execute, empty queues

    @Test
    @DisplayName("execute with empty queues returns completed BatchWriteResult with no unprocessed")
    void execute_emptyQueues_doesNothing() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);

        BatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        assertTrue(result.unprocessedItems().isEmpty());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // endregion

    // region execute, with put

    @Test
    @DisplayName("execute with put delegates to low-level async client")
    void execute_withPut_delegatesToLowLevel() {
        stubTableSchema();
        stubItemToMap();
        stubTableName();

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(
                table, dynamoDbAsyncClient, ignored -> CompletableFuture.completedFuture(null));
        builder.put(new TestItem("item1"));

        BatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // endregion

    // region execute, with delete

    @Test
    @DisplayName("execute with delete delegates to low-level async client")
    void execute_withDelete_delegatesToLowLevel() {
        stubTableSchema();
        stubTableName();

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);
        builder.delete("pk");

        BatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // endregion

    // region execute, propagation of error

    @Test
    @DisplayName("execute propagates error from low-level async client")
    void execute_propagatesError() {
        stubTableSchema();
        stubItemToMap();
        stubTableName();

        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);
        builder.put(new TestItem("item1"));

        CompletableFuture<BatchWriteResult> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }

    // endregion

    // region retry logic

    @Test
    @DisplayName("execute retries unprocessed items and succeeds")
    void execute_retriesUnprocessedAndSucceeds() {
        stubTableSchema();
        stubItemToMap();
        stubTableName();

        Map<String, List<WriteRequest>> unprocessed = Map.of("test_table",
                List.of(WriteRequest.builder().putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item1").build()))).build()));

        // First response has unprocessed
        BatchWriteItemResponse firstResponse = mock(BatchWriteItemResponse.class);
        when(firstResponse.unprocessedItems()).thenReturn(unprocessed);

        // Second response succeeds after the controlled retry gate opens.
        BatchWriteItemResponse secondResponse = mock(BatchWriteItemResponse.class);
        when(secondResponse.unprocessedItems()).thenReturn(Map.of());

        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(firstResponse))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        CompletableFuture<Void> retryGate = new CompletableFuture<>();
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(
                table, dynamoDbAsyncClient, ignored -> retryGate);
        builder.put(new TestItem("item1"));

        CompletableFuture<BatchWriteResult> resultFuture = builder.execute();

        assertFalse(resultFuture.isDone(), "Retry must wait for the gate");
        verify(dynamoDbAsyncClient).batchWriteItem(any(BatchWriteItemRequest.class));

        retryGate.complete(null);
        BatchWriteResult result = resultFuture.join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    @DisplayName("execute retries unprocessed items and returns remaining after exhaustion")
    void execute_retriesUnprocessedAndReturnsRemaining() {
        stubTableSchema();
        stubItemToMap();
        stubTableName();

        WriteRequest item1 = WriteRequest.builder()
                .putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item1").build())))
                .build();
        WriteRequest item2 = WriteRequest.builder()
                .putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item2").build())))
                .build();
        Map<String, List<WriteRequest>> firstUnprocessed = Map.of("test_table", List.of(item1, item2));
        Map<String, List<WriteRequest>> secondUnprocessed = Map.of("test_table", List.of(item2));
        Map<String, List<WriteRequest>> thirdUnprocessed = Map.of("test_table", List.of(item1));

        List<BatchWriteItemResponse> responses = List.of(
                responseWith(firstUnprocessed),
                responseWith(secondUnprocessed),
                responseWith(thirdUnprocessed),
                responseWith(thirdUnprocessed));
        List<BatchWriteItemRequest> requests = new ArrayList<>();
        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(responses.get(requests.size() - 1));
                });

        List<Long> delays = new ArrayList<>();
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(
                table, dynamoDbAsyncClient, delay -> {
            delays.add(delay);
            return CompletableFuture.completedFuture(null);
        });
        builder.put(new TestItem("item1"));
        builder.put(new TestItem("item2"));
        builder.put(new TestItem("item3"));

        BatchWriteResult result = builder.execute().join();

        assertTrue(result.hasUnprocessed());
        assertEquals(thirdUnprocessed, result.unprocessedItems());
        assertEquals(3, delays.size());
        assertTrue(delays.get(0) >= 100 && delays.get(0) < 200);
        assertTrue(delays.get(1) >= 200 && delays.get(1) < 300);
        assertTrue(delays.get(2) >= 400 && delays.get(2) < 500);
        assertEquals(firstUnprocessed, requests.get(1).requestItems());
        assertEquals(secondUnprocessed, requests.get(2).requestItems());
        assertEquals(thirdUnprocessed, requests.get(3).requestItems());
        verify(dynamoDbAsyncClient, times(4)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    private BatchWriteItemResponse responseWith(Map<String, List<WriteRequest>> unprocessed) {
        BatchWriteItemResponse response = mock(BatchWriteItemResponse.class);
        when(response.unprocessedItems()).thenReturn(unprocessed);
        return response;
    }

    // endregion

    // region limit validation

    @Test
    @DisplayName("execute with more than 25 items throws IllegalArgumentException")
    void execute_exceedsItemLimit_throws() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(table, dynamoDbAsyncClient);
        for (int i = 0; i < 26; i++) {
            builder.put(new TestItem("item" + i));
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verifyNoInteractions(dynamoDbAsyncClient);
    }
}
// endregion
