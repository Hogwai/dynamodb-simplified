package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncBatchWriteBuilder")
class AsyncBatchWriteBuilderTest {

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

    private void stubTableSchema() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("id");
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    private void stubItemToMap() {
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(java.util.Map.of("id",
                        software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s("item1").build()));
    }

    // ============ put ============

    @Test
    @DisplayName("put adds item and returns self")
    void put_addsItem() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);
        TestItem item = new TestItem("item1");

        AsyncBatchWriteBuilder<TestItem> result = builder.put(item);

        assertSame(builder, result);
    }

    // ============ delete ============

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);

        AsyncBatchWriteBuilder<TestItem> result = builder.delete("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);

        AsyncBatchWriteBuilder<TestItem> result = builder.delete("pk", "sk");

        assertSame(builder, result);
    }

    // ============ execute — empty queues ============

    @Test
    @DisplayName("execute with empty queues returns completed future and does not call batchWriteItem")
    void execute_emptyQueues_doesNothing() {
        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);

        CompletableFuture<Void> future = builder.execute();
        assertDoesNotThrow(future::join);
        verify(enhancedClient, never()).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    // ============ execute — with put ============

    @Test
    @DisplayName("execute with put delegates to enhanced client")
    void execute_withPut_delegatesToEnhancedClient() {
        stubTableSchema();
        stubItemToMap();

        when(enhancedClient.batchWriteItem(any(BatchWriteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(BatchWriteResult.class)));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);
        builder.put(new TestItem("item1"));

        CompletableFuture<Void> future = builder.execute();
        assertDoesNotThrow(future::join);
        verify(enhancedClient).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    // ============ execute — with delete ============

    @Test
    @DisplayName("execute with delete delegates to enhanced client")
    void execute_withDelete_delegatesToEnhancedClient() {
        stubTableSchema();

        when(enhancedClient.batchWriteItem(any(BatchWriteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(BatchWriteResult.class)));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);
        builder.delete("pk");

        CompletableFuture<Void> future = builder.execute();
        assertDoesNotThrow(future::join);
        verify(enhancedClient).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    // ============ execute — propagation of error ============

    @Test
    @DisplayName("execute propagates error from enhanced client")
    void execute_propagatesError() {
        stubTableSchema();
        stubItemToMap();

        when(enhancedClient.batchWriteItem(any(BatchWriteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));

        AsyncBatchWriteBuilder<TestItem> builder = new AsyncBatchWriteBuilder<>(enhancedClient, table);
        builder.put(new TestItem("item1"));

        CompletableFuture<Void> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }
}
