package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncCrossTableBatchGetBuilder")
class AsyncCrossTableBatchGetBuilderTest {

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

    private void stubTableLookup() {
        lenient().when(rawTable.tableName()).thenReturn("test_table");
        lenient().when(rawTable.tableSchema()).thenReturn(tableSchema);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    private AsyncTable<TestItem> asyncTable() {
        return new AsyncTable<>(enhancedClient, rawTable, dynamoDbAsyncClient);
    }

    // ============ addKey ============

    @Test
    @DisplayName("addKey with partition key returns self")
    void addKey_withPartitionKey() {
        stubTableLookup();
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchGetBuilder result = builder.addKey(asyncTable(), "pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKey with partition and sort key returns self")
    void addKey_withPartitionAndSortKey() {
        stubTableLookup();
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchGetBuilder result = builder.addKey(asyncTable(), "pk", "sk");

        assertSame(builder, result);
    }

    // ============ addKeys ============

    @Test
    @DisplayName("addKeys with collection returns self")
    void addKeys_withCollection() {
        stubTableLookup();
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchGetBuilder result = builder.addKeys(asyncTable(), List.of("pk1", "pk2"));

        assertSame(builder, result);
    }

    // ============ execute, empty ============

    @Test
    @DisplayName("execute with empty entries returns empty result")
    void execute_empty_returnsEmpty() {
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        CrossTableBatchGetResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        assertTrue(result.getUnprocessedKeys().isEmpty());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // ============ execute, with keys ============

    @Test
    @DisplayName("execute with keys calls low-level client")
    void execute_withKeys_callsLowLevelClient() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk1").build());
        responses.put("test_table", List.of(itemMap));

        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        lenient().when(tableSchema.mapToItem(any(Map.class))).thenReturn(new TestItem("result1"));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");

        CrossTableBatchGetResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbAsyncClient).batchGetItem(any(BatchGetItemRequest.class));
    }

    // ============ returnConsumedCapacity ============

    @Test
    @DisplayName("returnConsumedCapacity is propagated to the request")
    void returnConsumedCapacity_propagatesToRequest() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));
        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");
        builder.returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
        builder.execute().join();

        var captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());
        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    @Test
    @DisplayName("returnConsumedCapacity returns self for chaining")
    void returnConsumedCapacity_returnsSelf() {
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        AsyncCrossTableBatchGetBuilder result = builder.returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

        assertSame(builder, result);
    }

    // ============ project ============

    @Test
    @DisplayName("project(String...) returns self for chaining")
    void project_varargs_returnsSelf() {
        stubTableLookup();
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk");

        AsyncCrossTableBatchGetBuilder result = builder.project("attr1", "attr2");

        assertSame(builder, result);
    }

    // ============ limit validation ============

    @Test
    @DisplayName("execute with more than 100 keys throws IllegalArgumentException")
    void execute_exceedsKeyLimit_throws() {
        stubTableLookup();
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        for (int i = 0; i < 101; i++) {
            builder.addKey(asyncTable(), "pk" + i);
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
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("batch get failed")));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk");

        CompletableFuture<CrossTableBatchGetResult> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }

    // ============ project on empty throws ============

    @Test
    @DisplayName("project on empty entries throws IllegalStateException")
    void project_empty_throws() {
        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);

        assertThrows(IllegalStateException.class, () -> builder.project("attr"));
    }

    // ============ buildCrossTableBatchGetResult — null responses ============

    @Test
    @DisplayName("execute handles null responses and unprocessedKeys from DynamoDB gracefully")
    void execute_withNullResponsesAndUnprocessed_handlesGracefully() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // Mock response with unstubbed responses()/unprocessedKeys() → Mockito returns null by default
        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");

        CrossTableBatchGetResult result = builder.execute().join();

        assertFalse(result.hasUnprocessed());
        assertTrue(result.getUnprocessedKeys().isEmpty());
        verify(dynamoDbAsyncClient).batchGetItem(any(BatchGetItemRequest.class));
    }

    // ============ buildTableKeysAndAttributes — multiple keys, same table ============

    @Test
    @DisplayName("execute with multiple keys for same table includes all keys in request")
    void execute_multipleKeysSameTable_includesAllKeys() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        Map<String, AttributeValue> item1 = Map.of("id", AttributeValue.builder().s("pk1").build());
        Map<String, AttributeValue> item2 = Map.of("id", AttributeValue.builder().s("pk2").build());
        responses.put("test_table", List.of(item1, item2));

        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");
        builder.addKey(asyncTable(), "pk2");

        builder.execute().join();

        var captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());

        Map<String, KeysAndAttributes> requestItems = captor.getValue().requestItems();
        assertEquals(1, requestItems.size());
        assertEquals(2, requestItems.get("test_table").keys().size());
    }

    // ============ buildTableKeysAndAttributes — no projection ============

    @Test
    @DisplayName("execute without projection omits projectionExpression in request")
    void execute_withoutProjection_projectionNotSet() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));

        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");

        builder.execute().join();

        var captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());

        Map<String, KeysAndAttributes> requestItems = captor.getValue().requestItems();
        assertNull(requestItems.get("test_table").projectionExpression());
    }

    // ============ buildTableKeysAndAttributes — empty projection expression ============

    @Test
    @DisplayName("execute with empty projection expression omits projectionExpression in request")
    void execute_withEmptyProjectionExpression_projectionNotSet() {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));

        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbAsyncClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AsyncCrossTableBatchGetBuilder builder = new AsyncCrossTableBatchGetBuilder(dynamoDbAsyncClient);
        builder.addKey(asyncTable(), "pk1");
        builder.project();  // Empty projection expression (no attributes → isEmpty() == true)

        builder.execute().join();

        var captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbAsyncClient).batchGetItem(captor.capture());

        Map<String, KeysAndAttributes> requestItems = captor.getValue().requestItems();
        assertNull(requestItems.get("test_table").projectionExpression());
    }
}
