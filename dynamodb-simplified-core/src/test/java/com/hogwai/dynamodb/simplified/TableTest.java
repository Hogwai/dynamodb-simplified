package com.hogwai.dynamodb.simplified;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import com.hogwai.dynamodb.simplified.builder.Index;

import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Table}.
 * <p>
 * Uses Mockito to mock the {@link DynamoDbTable}, {@link DynamoDbEnhancedClient},
 * and {@link DynamoDbClient} dependencies. Verifies that builder factory methods
 * return non-null builders, convenience methods delegate to the underlying
 * {@link DynamoDbTable}, and accessor methods return the correct injected instances.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Table")
class TableTest {

    @Mock
    DynamoDbTable<TestItem> dynamoDbTable;

    @Mock
    DynamoDbEnhancedClient enhancedClient;

    @Mock
    DynamoDbClient dynamoDbClient;

    @Mock
    DynamoDbIndex<TestItem> dynamoDbIndex;

    /**
     * A simple POJO used as the item type in tests.
     */
    static class TestItem {
        private String id;

        TestItem() {
            /* Required for instantiation */
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    private Table<TestItem> createTable() {
        return new Table<>(enhancedClient, dynamoDbTable, dynamoDbClient);
    }

    // ============ Builder Factories ============

    @Test
    @DisplayName("query() returns a non-null QueryBuilder")
    void query() {
        Table<TestItem> table = createTable();
        assertNotNull(table.query());
    }

    @Test
    @DisplayName("scan() returns a non-null ScanBuilder")
    void scan() {
        Table<TestItem> table = createTable();
        assertNotNull(table.scan());
    }

    @Test
    @DisplayName("get(pk) returns a non-null GetItemBuilder")
    void getWithPartitionKey() {
        Table<TestItem> table = createTable();
        assertNotNull(table.get("pk"));
    }

    @Test
    @DisplayName("get(pk, sk) returns a non-null GetItemBuilder")
    void getWithPartitionAndSortKey() {
        Table<TestItem> table = createTable();
        assertNotNull(table.get("pk", "sk"));
    }

    @Test
    @DisplayName("getItem(pk, sk) delegates to DynamoDbTable.getItem(Key) with sort key")
    void getItemWithPartitionAndSortKey() {
        Table<TestItem> table = createTable();
        table.getItem("pk", "sk");
        verify(dynamoDbTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("delete(pk, sk) returns a non-null DeleteBuilder")
    void deleteWithPartitionAndSortKey() {
        Table<TestItem> table = createTable();
        assertNotNull(table.delete("pk", "sk"));
    }

    @Test
    @DisplayName("deleteItem(pk) delegates to DynamoDbTable.deleteItem(Key) and returns deleted item")
    void deleteItemDelegates() {
        TestItem expected = new TestItem();
        when(dynamoDbTable.deleteItem(any(Key.class))).thenReturn(expected);

        Table<TestItem> table = createTable();
        TestItem result = table.deleteItem("pk");

        assertSame(expected, result);
        verify(dynamoDbTable).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("deleteItem(pk, sk) delegates to DynamoDbTable.deleteItem(Key) with sort key and returns deleted item")
    void deleteItemWithPartitionAndSortKey() {
        TestItem expected = new TestItem();
        when(dynamoDbTable.deleteItem(any(Key.class))).thenReturn(expected);

        Table<TestItem> table = createTable();
        TestItem result = table.deleteItem("pk", "sk");

        assertSame(expected, result);
        verify(dynamoDbTable).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("deleteItem(pk) returns null when item does not exist")
    void deleteItemReturnsNullWhenNotFound() {
        when(dynamoDbTable.deleteItem(any(Key.class))).thenReturn(null);

        Table<TestItem> table = createTable();
        TestItem result = table.deleteItem("pk");

        assertNull(result);
        verify(dynamoDbTable).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("deleteItem(pk, sk) returns null when item does not exist")
    void deleteItemWithSortKeyReturnsNullWhenNotFound() {
        when(dynamoDbTable.deleteItem(any(Key.class))).thenReturn(null);

        Table<TestItem> table = createTable();
        TestItem result = table.deleteItem("pk", "sk");

        assertNull(result);
        verify(dynamoDbTable).deleteItem(any(Key.class));
    }

    // ============ Convenience Delegation Methods ============

    @Test
    @DisplayName("getItem(pk) delegates to DynamoDbTable.getItem(Key)")
    void getItemDelegates() {
        Table<TestItem> table = createTable();

        table.getItem("pk");

        verify(dynamoDbTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("putItem(item) delegates to DynamoDbTable.putItem(item)")
    void putItemDelegates() {
        Table<TestItem> table = createTable();
        TestItem item = new TestItem();

        table.putItem(item);

        verify(dynamoDbTable).putItem(item);
    }

    @Test
    @DisplayName("updateItem(item) delegates to DynamoDbTable.updateItem(item)")
    void updateItemDelegates() {
        Table<TestItem> table = createTable();
        TestItem item = new TestItem();

        table.updateItem(item);

        verify(dynamoDbTable).updateItem(item);
    }

    // ============ Fluent Builder Methods ============

    @Test
    @DisplayName("put(item) returns a non-null PutBuilder")
    void putReturnsBuilder() {
        Table<TestItem> table = createTable();
        TestItem item = new TestItem();

        assertNotNull(table.put(item));
    }

    @Test
    @DisplayName("update(item) returns a non-null UpdateBuilder")
    void updateReturnsBuilder() {
        Table<TestItem> table = createTable();
        TestItem item = new TestItem();

        assertNotNull(table.update(item));
    }

    @Test
    @DisplayName("delete(pk) returns a non-null DeleteBuilder")
    void deleteReturnsBuilder() {
        Table<TestItem> table = createTable();

        assertNotNull(table.delete("pk"));
    }

    // ============ Batch Operations ============

    @Test
    @DisplayName("batchGet() returns a non-null BatchGetBuilder")
    void batchGetReturnsBuilder() {
        Table<TestItem> table = createTable();

        assertNotNull(table.batchGet());
    }

    @Test
    @DisplayName("batchWrite() returns a non-null BatchWriteBuilder")
    void batchWriteReturnsBuilder() {
        Table<TestItem> table = createTable();

        assertNotNull(table.batchWrite());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("putAll(items) puts all items via batch write")
    void putAllDelegatesToBatchWrite() {
        TableSchema<TestItem> schema = mock(TableSchema.class);
        when(schema.itemToMap(any(), anyBoolean())).thenReturn(Map.of());
        when(dynamoDbTable.tableSchema()).thenReturn(schema);
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(BatchWriteItemResponse.builder().build());

        Table<TestItem> table = createTable();
        TestItem item1 = new TestItem();
        TestItem item2 = new TestItem();
        assertNotNull(table.putAll(List.of(item1, item2)));

        verify(dynamoDbClient).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    // ============ Secondary Index ============

    @Test
    @DisplayName("index(name) returns a non-null Index and delegates to DynamoDbTable.index()")
    void index() {
        when(dynamoDbTable.index(anyString())).thenReturn(dynamoDbIndex);

        Table<TestItem> table = createTable();
        Index<TestItem> idx = table.index("my-index");

        assertNotNull(idx);
        verify(dynamoDbTable).index("my-index");
    }

    // ============ Key-Only Update ============

    @SuppressWarnings("unchecked")
    private void mockSchema(String sortKeyName) {
        TableSchema<TestItem> schema = mock(TableSchema.class);
        TableMetadata tableMetadata = mock(TableMetadata.class);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("key");
        if (sortKeyName != null) {
            when(tableMetadata.primarySortKey()).thenReturn(Optional.of(sortKeyName));
        } else {
            lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.empty());
        }
        when(schema.tableMetadata()).thenReturn(tableMetadata);
        when(dynamoDbTable.tableSchema()).thenReturn(schema);
    }

    @Test
    @DisplayName("update(pk, null, consumer) delegates to low-level client")
    void updateWithKeyOnlyDelegatesToLowLevelClient() {
        mockSchema(null);
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of()).build());

        Table<TestItem> table = createTable();
        table.update("pk-val", null, expr -> expr.set("name", "new"));

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    @DisplayName("update(pk, sk, consumer) delegates to low-level client with composite key")
    void updateWithKeyAndSortKeyDelegatesToLowLevelClient() {
        mockSchema("sk");
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of()).build());

        Table<TestItem> table = createTable();
        table.update("pk-val", "sk-val", expr -> expr.set("name", "new"));

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    // ============ Raw Access ============

    @Test
    @DisplayName("getRawTable() returns the same DynamoDbTable instance")
    void getRawTable() {
        Table<TestItem> table = createTable();

        assertSame(dynamoDbTable, table.getRawTable());
    }

    @Test
    @DisplayName("getEnhancedClient() returns the same DynamoDbEnhancedClient instance")
    void getEnhancedClient() {
        Table<TestItem> table = createTable();

        assertSame(enhancedClient, table.getEnhancedClient());
    }

    @Test
    @DisplayName("getDynamoDbClient() returns the same DynamoDbClient instance")
    void getDynamoDbClient() {
        Table<TestItem> table = createTable();

        assertSame(dynamoDbClient, table.getDynamoDbClient());
    }
}
