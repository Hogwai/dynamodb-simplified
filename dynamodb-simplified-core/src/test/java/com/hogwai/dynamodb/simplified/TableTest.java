package com.hogwai.dynamodb.simplified;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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

    /**
     * A simple POJO used as the item type in tests.
     */
    static class TestItem {
        private String id;

        public TestItem() { /* Required for instantiation */ }

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
