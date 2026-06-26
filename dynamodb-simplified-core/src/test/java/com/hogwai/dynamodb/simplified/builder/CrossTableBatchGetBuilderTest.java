package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.result.CrossTableBatchGetResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrossTableBatchGetBuilder")
class CrossTableBatchGetBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbTable<TestItem> rawTable;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    // ============ Helpers ============

    @SuppressWarnings("unchecked")
    private <T> Table<T> createTable(DynamoDbTable<T> dynamoDbTable)
            throws ReflectiveOperationException {
        Constructor<Table> ctor = Table.class.getDeclaredConstructor(
                DynamoDbEnhancedClient.class, DynamoDbTable.class, DynamoDbClient.class);
        ctor.setAccessible(true);
        return (Table<T>) ctor.newInstance(enhancedClient, dynamoDbTable, dynamoDbClient);
    }

    private void stubTableLookup() {
        lenient().when(rawTable.tableName()).thenReturn("test_table");
        lenient().when(rawTable.tableSchema()).thenReturn(tableSchema);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    // ============ addKey ============

    @Test
    @DisplayName("addKey with partition key returns self")
    void addKey_withPartitionKey() throws Exception {
        stubTableLookup();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);

        CrossTableBatchGetBuilder result = builder.addKey(table, "pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKey with partition and sort key returns self")
    void addKey_withPartitionAndSortKey() throws Exception {
        stubTableLookup();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);

        CrossTableBatchGetBuilder result = builder.addKey(table, "pk", "sk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKeys with collection returns self")
    void addKeys_withCollection() throws Exception {
        stubTableLookup();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);

        CrossTableBatchGetBuilder result = builder.addKeys(table, List.of("pk1", "pk2"));

        assertSame(builder, result);
    }

    // ============ execute, empty ============

    @Test
    @DisplayName("execute with empty entries returns empty result")
    void execute_empty_returnsEmpty() {
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);

        CrossTableBatchGetResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        assertTrue(result.getUnprocessedKeys().isEmpty());
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ execute, with keys ============

    @Test
    @DisplayName("execute with keys calls low-level client")
    @SuppressWarnings("unchecked")
    void execute_withKeys_callsLowLevelClient() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // Mock low-level response
        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk1").build());
        responses.put("test_table", List.of(itemMap));

        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);

        TestItem expectedItem = new TestItem("result1");
        when(tableSchema.mapToItem(any(Map.class))).thenReturn(expectedItem);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        builder.addKey(table, "pk1");

        CrossTableBatchGetResult result = builder.execute();

        List<TestItem> items = result.getItems(table);
        assertEquals(1, items.size());
        assertSame(expectedItem, items.getFirst());
        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient).batchGetItem(any(BatchGetItemRequest.class));
    }

    // ============ project ============

    @Test
    @DisplayName("project(String...) returns self for chaining")
    void project_varargs_returnsSelf() throws Exception {
        stubTableLookup();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        builder.addKey(table, "pk");

        CrossTableBatchGetBuilder result = builder.project("attr1", "attr2");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("project(String...) includes projection in request")
    void project_varargs_includesProjection() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // Mock low-level response
        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));
        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        builder.addKey(table, "pk1");
        builder.project("attr1", "attr2");
        builder.execute();

        // Verify the request includes projection expression and expression attribute names
        var captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbClient).batchGetItem(captor.capture());
        BatchGetItemRequest request = captor.getValue();
        KeysAndAttributes ka = request.requestItems().get("test_table");
        assertNotNull(ka.projectionExpression());
        Map<String, String> attrNames = ka.expressionAttributeNames();
        assertNotNull(attrNames);
        assertTrue(attrNames.containsValue("attr1"));
        assertTrue(attrNames.containsValue("attr2"));
    }

    // ============ limit validation ============

    @Test
    @DisplayName("execute with more than 100 keys throws IllegalArgumentException")
    void execute_exceedsKeyLimit_throws() throws Exception {
        stubTableLookup();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        for (int i = 0; i < 101; i++) {
            builder.addKey(table, "pk" + i);
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ project on empty throws ============

    @Test
    @DisplayName("project on empty entries throws IllegalStateException")
    void project_empty_throws() {
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);

        assertThrows(IllegalStateException.class, () -> builder.project("attr"));
    }

    // ============ retry ============

    @Test
    @DisplayName("execute retries unprocessed keys and accumulates items")
    @SuppressWarnings("unchecked")
    void execute_retriesUnprocessedKeys() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // First response: 1 item + 1 unprocessed key
        Map<String, List<Map<String, AttributeValue>>> firstResponses = new HashMap<>();
        firstResponses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));
        KeysAndAttributes unprocessedKa = KeysAndAttributes.builder()
                .keys(List.of(Map.of("id", AttributeValue.builder().s("pk2").build())))
                .build();
        Map<String, KeysAndAttributes> unprocessedMap = Map.of("test_table", unprocessedKa);
        BatchGetItemResponse firstResponse = mock(BatchGetItemResponse.class);
        when(firstResponse.responses()).thenReturn(firstResponses);
        when(firstResponse.unprocessedKeys()).thenReturn(unprocessedMap);

        // Second response: 1 item, no unprocessed
        Map<String, List<Map<String, AttributeValue>>> secondResponses = new HashMap<>();
        secondResponses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk2").build())));
        BatchGetItemResponse secondResponse = mock(BatchGetItemResponse.class);
        when(secondResponse.responses()).thenReturn(secondResponses);
        when(secondResponse.unprocessedKeys()).thenReturn(Map.of());

        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(firstResponse, secondResponse);

        TestItem item1 = new TestItem("result1");
        TestItem item2 = new TestItem("result2");
        when(tableSchema.mapToItem(any(Map.class)))
                .thenReturn(item1, item2);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        builder.addKey(table, "pk1");
        builder.addKey(table, "pk2");

        CrossTableBatchGetResult result = builder.execute();

        List<TestItem> items = result.getItems(table);
        assertEquals(2, items.size());
        assertSame(item1, items.get(0));
        assertSame(item2, items.get(1));
        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient, times(2)).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    @DisplayName("execute after retry exhaustion returns remaining unprocessed keys")
    void execute_exhaustsRetries_returnsUnprocessedKeys() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // All responses return no items but always have unprocessed keys
        Map<String, List<Map<String, AttributeValue>>> emptyResponses = new HashMap<>();
        emptyResponses.put("test_table", List.of());
        KeysAndAttributes unprocessedKa = KeysAndAttributes.builder()
                .keys(List.of(Map.of("id", AttributeValue.builder().s("pk1").build())))
                .build();
        Map<String, KeysAndAttributes> unprocessedMap = Map.of("test_table", unprocessedKa);

        BatchGetItemResponse response = mock(BatchGetItemResponse.class);
        when(response.responses()).thenReturn(emptyResponses);
        when(response.unprocessedKeys()).thenReturn(unprocessedMap);

        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(response);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchGetBuilder builder = new CrossTableBatchGetBuilder(dynamoDbClient);
        builder.addKey(table, "pk1");

        CrossTableBatchGetResult result = builder.execute();

        assertTrue(result.hasUnprocessed());
        Map<String, KeysAndAttributes> remaining = result.getUnprocessedKeys();
        assertEquals(1, remaining.size());
        assertEquals(1, remaining.get("test_table").keys().size());
        // Initial call + MAX_RETRIES (3) retries = 4 total calls
        verify(dynamoDbClient, times(4)).batchGetItem(any(BatchGetItemRequest.class));
    }
}
