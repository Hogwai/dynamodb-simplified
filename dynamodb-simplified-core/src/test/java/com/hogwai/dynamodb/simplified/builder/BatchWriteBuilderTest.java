package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.result.BatchWriteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchWriteBuilder")
class BatchWriteBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbTable<TestItem> table;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private DynamoDbClient dynamoDbClient;

    // ============ put ============

    @Test
    @DisplayName("put adds item and returns self")
    void put_addsItem() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);
        TestItem item = new TestItem("item1");

        BatchWriteBuilder<TestItem> result = builder.put(item);

        assertSame(builder, result);
    }

    // ============ delete ============

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);

        BatchWriteBuilder<TestItem> result = builder.delete("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);

        BatchWriteBuilder<TestItem> result = builder.delete("pk", "sk");

        assertSame(builder, result);
    }

    // ============ execute, empty queues ============

    @Test
    @DisplayName("execute with empty queues returns BatchWriteResult with no unprocessed items")
    void execute_emptyQueues_doesNothing() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);

        BatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        assertTrue(result.unprocessedItems().isEmpty());
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ execute, with put ============

    @Test
    @DisplayName("execute with put delegates to low-level client")
    void execute_withPut_delegatesToLowLevel() {
        // Mock table schema chain
        when(table.tableSchema()).thenReturn(tableSchema);
        when(table.tableName()).thenReturn("test_table");

        // Mock itemToMap
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        // Mock low-level response
        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(mockResponse);

        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);
        builder.put(new TestItem("item1"));

        BatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ execute, retry logic ============

    @Test
    @DisplayName("execute retries unprocessed items and succeeds")
    void execute_retriesUnprocessedAndSucceeds() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(table.tableName()).thenReturn("test_table");
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        // First response returns unprocessed items
        BatchWriteItemResponse firstResponse = mock(BatchWriteItemResponse.class);
        Map<String, List<WriteRequest>> unprocessed = Map.of("test_table",
                List.of(WriteRequest.builder().putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item1").build()))).build()));
        when(firstResponse.unprocessedItems()).thenReturn(unprocessed);

        // Second response succeeds
        BatchWriteItemResponse secondResponse = mock(BatchWriteItemResponse.class);
        when(secondResponse.unprocessedItems()).thenReturn(Map.of());

        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(firstResponse, secondResponse);

        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);
        builder.put(new TestItem("item1"));

        BatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    @DisplayName("execute retries unprocessed items and returns remaining after exhaustion")
    void execute_retriesUnprocessedAndReturnsRemaining() {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(table.tableName()).thenReturn("test_table");
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        // All responses return unprocessed
        Map<String, List<WriteRequest>> unprocessed = Map.of("test_table",
                List.of(WriteRequest.builder().putRequest(r -> r.item(Map.of("id", AttributeValue.builder().s("item1").build()))).build()));

        BatchWriteItemResponse response = mock(BatchWriteItemResponse.class);
        when(response.unprocessedItems()).thenReturn(unprocessed);
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(response);

        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);
        builder.put(new TestItem("item1"));

        BatchWriteResult result = builder.execute();

        assertTrue(result.hasUnprocessed());
        // Called 4 times: attempt 0, 1, 2, 3 (maxRetries = 3, so loop runs 0..3 = 4 times)
        verify(dynamoDbClient, times(4)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ limit validation ============

    @Test
    @DisplayName("execute with more than 25 items throws IllegalArgumentException")
    void execute_exceedsItemLimit_throws() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(table, dynamoDbClient);
        // Add 26 puts
        for (int i = 0; i < 26; i++) {
            builder.put(new TestItem("item" + i));
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verifyNoInteractions(dynamoDbClient);
    }
}
