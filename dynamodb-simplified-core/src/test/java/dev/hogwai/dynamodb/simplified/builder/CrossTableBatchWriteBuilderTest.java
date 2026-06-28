package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrossTableBatchWriteBuilder")
class CrossTableBatchWriteBuilderTest {

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

    private void stubTableNameAndSchema() {
        lenient().when(rawTable.tableName()).thenReturn("test_table");
        lenient().when(rawTable.tableSchema()).thenReturn(tableSchema);
    }

    private void stubTableMetadata() {
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
    }

    // ============ put ============

    @Test
    @DisplayName("put returns self")
    void put_returnsSelf() throws Exception {
        stubTableNameAndSchema();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);

        CrossTableBatchWriteBuilder result = builder.put(table, new TestItem("item1"));

        assertSame(builder, result);
    }

    // ============ delete ============

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey_returnsSelf() throws Exception {
        stubTableNameAndSchema();
        stubTableMetadata();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);

        CrossTableBatchWriteBuilder result = builder.delete(table, "pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey_returnsSelf() throws Exception {
        stubTableNameAndSchema();
        stubTableMetadata();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);

        CrossTableBatchWriteBuilder result = builder.delete(table, "pk", "sk");

        assertSame(builder, result);
    }

    // ============ execute, empty ============

    @Test
    @DisplayName("execute with empty operations returns empty result")
    void execute_empty_returnsEmpty() {
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);

        CrossTableBatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ execute, with put ============

    @Test
    @DisplayName("execute with put calls low-level client")
    void execute_withPut_callsLowLevelClient() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(mockResponse);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);
        builder.put(table, new TestItem("item1"));

        CrossTableBatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ execute, retry ============

    @Test
    @DisplayName("execute retries unprocessed items and succeeds")
    void execute_retriesUnprocessed() throws Exception {
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

        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(firstResponse, secondResponse);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);
        builder.put(table, new TestItem("item1"));

        CrossTableBatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ 25 item limit ============

    @Test
    @DisplayName("execute with more than 25 items throws IllegalArgumentException")
    void execute_exceedsItemLimit_throws() throws Exception {
        stubTableNameAndSchema();
        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);
        for (int i = 0; i < 26; i++) {
            builder.put(table, new TestItem("item" + i));
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ delete with key ============

    @Test
    @DisplayName("execute with delete calls low-level client")
    void execute_withDelete_callsLowLevelClient() throws Exception {
        when(rawTable.tableName()).thenReturn("test_table");
        when(rawTable.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        BatchWriteItemResponse mockResponse = mock(BatchWriteItemResponse.class);
        when(mockResponse.unprocessedItems()).thenReturn(Map.of());
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(mockResponse);

        Table<TestItem> table = createTable(rawTable);
        CrossTableBatchWriteBuilder builder = new CrossTableBatchWriteBuilder(dynamoDbClient);
        builder.delete(table, "pk");

        CrossTableBatchWriteResult result = builder.execute();

        assertFalse(result.hasUnprocessed());
        verify(dynamoDbClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }
}
