package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncCrossTableBatchWriteBuilder")
class AsyncCrossTableBatchWriteBuilderTest {

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
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    private DynamoDbAsyncTable<TestItem> rawTable;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    // ============ Helpers ============

    private void stubTableNameAndSchema() {
        lenient().when(rawTable.tableName()).thenReturn("test_table");
        lenient().when(rawTable.tableSchema()).thenReturn(tableSchema);
    }

    private void stubTableMetadata() {
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    private AsyncTable<TestItem> asyncTable() {
        return new AsyncTable<>(enhancedClient, rawTable, dynamoDbAsyncClient);
    }

    // ============ put ============

    @Test
    @DisplayName("put returns self")
    void put_returnsSelf() {
        stubTableNameAndSchema();
        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchWriteBuilder result = builder.put(asyncTable(), new TestItem("item1"));

        assertSame(builder, result);
    }

    // ============ delete ============

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey_returnsSelf() {
        stubTableNameAndSchema();
        stubTableMetadata();
        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchWriteBuilder result = builder.delete(asyncTable(), "pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey_returnsSelf() {
        stubTableNameAndSchema();
        stubTableMetadata();
        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchWriteBuilder result = builder.delete(asyncTable(), "pk", "sk");

        assertSame(builder, result);
    }

    // ============ execute, empty ============

    @Test
    @DisplayName("execute with empty operations returns empty result")
    void execute_empty_returnsEmpty() {
        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);

        CrossTableBatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // ============ execute, with put ============

    @Test
    @DisplayName("execute with put calls low-level client")
    void execute_withPut_callsLowLevelClient() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);
        builder.put(asyncTable(), new TestItem("item1"));

        CrossTableBatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ execute, with delete ============

    @Test
    @DisplayName("execute with delete calls low-level client")
    void execute_withDelete_callsLowLevelClient() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);
        builder.delete(asyncTable(), "pk");

        CrossTableBatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ retry ============

    @Test
    @DisplayName("execute retries unprocessed items and succeeds")
    void execute_retriesUnprocessedAndSucceeds() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        Map<String, List<WriteRequest>> unprocessed = Map.of("test_table",
                List.of(WriteRequest.builder().putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item1").build()))).build()));

        BatchWriteItemResponse firstResponse = mock(BatchWriteItemResponse.class);
        when(firstResponse.unprocessedItems()).thenReturn(unprocessed);

        BatchWriteItemResponse secondResponse = mock(BatchWriteItemResponse.class);
        when(secondResponse.unprocessedItems()).thenReturn(Map.of());

        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(firstResponse))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);
        builder.put(asyncTable(), new TestItem("item1"));

        CrossTableBatchWriteResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ 25 item limit ============

    @Test
    @DisplayName("execute with more than 25 items throws IllegalArgumentException")
    void execute_exceedsItemLimit_throws() {
        stubTableNameAndSchema();
        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);
        for (int i = 0; i < 26; i++) {
            builder.put(asyncTable(), new TestItem("item" + i));
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // ============ error propagation ============

    @Test
    @DisplayName("execute propagates error from low-level client")
    void execute_propagatesError() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        when(dynamoDbAsyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));

        AsyncCrossTableBatchWriteBuilder builder = new AsyncCrossTableBatchWriteBuilder(dynamoDbAsyncClient);
        builder.put(asyncTable(), new TestItem("item1"));

        CompletableFuture<CrossTableBatchWriteResult> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }
}
