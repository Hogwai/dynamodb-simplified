package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncTable}.
 * <p>
 * Uses Mockito to mock the {@link DynamoDbAsyncTable}, {@link DynamoDbEnhancedAsyncClient},
 * and {@link DynamoDbAsyncClient} dependencies. Verifies that convenience methods delegate
 * to the underlying {@link DynamoDbAsyncTable} and return {@link CompletableFuture}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncTable")
class AsyncTableTest {

    @Mock
    DynamoDbAsyncTable<TestItem> dynamoDbAsyncTable;

    @Mock
    DynamoDbEnhancedAsyncClient enhancedAsyncClient;

    @Mock
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    DynamoDbAsyncIndex<TestItem> dynamoDbAsyncIndex;

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

    private AsyncTable<TestItem> createTable() {
        return new AsyncTable<>(enhancedAsyncClient, dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("getItem(pk) delegates to DynamoDbAsyncTable.getItem(Key) and returns Optional")
    void getItemDelegates() {
        TestItem item = new TestItem();
        when(dynamoDbAsyncTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(item));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Optional<TestItem>> result = table.getItem("pk");

        assertNotNull(result);
        assertEquals(item, result.join().orElse(null));
        verify(dynamoDbAsyncTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("putItem(item) delegates to DynamoDbAsyncTable.putItem(item)")
    void putItemDelegates() {
        when(dynamoDbAsyncTable.putItem(any(TestItem.class))).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        TestItem item = new TestItem();
        CompletableFuture<Void> result = table.putItem(item);

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).putItem(item);
    }

    @Test
    @DisplayName("deleteItem(pk) delegates to DynamoDbAsyncTable.deleteItem(Key)")
    void deleteItemDelegates() {
        when(dynamoDbAsyncTable.deleteItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.deleteItem("pk");

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("index(name) returns a non-null AsyncIndex and delegates to DynamoDbAsyncTable.index()")
    void indexDelegates() {
        when(dynamoDbAsyncTable.index(anyString())).thenReturn(dynamoDbAsyncIndex);

        AsyncTable<TestItem> table = createTable();
        AsyncIndex<TestItem> idx = table.index("my-index");

        assertNotNull(idx);
        assertSame(dynamoDbAsyncIndex, idx.getRawIndex());
        verify(dynamoDbAsyncTable).index("my-index");
    }

    @Test
    @DisplayName("create() delegates to DynamoDbAsyncTable.createTable()")
    void createDelegates() {
        when(dynamoDbAsyncTable.createTable()).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.create();

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).createTable();
    }

    @Test
    @DisplayName("delete() delegates to DynamoDbAsyncTable.deleteTable()")
    void deleteDelegates() {
        when(dynamoDbAsyncTable.deleteTable()).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.delete();

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).deleteTable();
    }

    @Test
    @DisplayName("describe() delegates to DynamoDbAsyncTable.describeTable()")
    void describeDelegates() {
        DescribeTableEnhancedResponse response = mock(DescribeTableEnhancedResponse.class);
        when(dynamoDbAsyncTable.describeTable()).thenReturn(CompletableFuture.completedFuture(response));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<DescribeTableEnhancedResponse> result = table.describe();

        assertNotNull(result);
        assertSame(response, result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() returns true when table exists")
    void existsReturnsTrue() {
        DescribeTableEnhancedResponse response = mock(DescribeTableEnhancedResponse.class);
        when(dynamoDbAsyncTable.describeTable()).thenReturn(CompletableFuture.completedFuture(response));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        assertTrue(result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() returns false when table does not exist")
    void existsReturnsFalseWhenTableNotFound() {
        when(dynamoDbAsyncTable.describeTable())
                .thenReturn(CompletableFuture.failedFuture(ResourceNotFoundException.builder().build()));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        assertFalse(result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() propagates non-ResourceNotFoundException exceptions")
    void existsPropagatesOtherExceptions() {
        when(dynamoDbAsyncTable.describeTable())
                .thenReturn(CompletableFuture.failedFuture(new DynamoSimplifiedException("other error")));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(DynamoSimplifiedException.class, ex.getCause());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("create(Consumer) delegates to DynamoDbAsyncTable.createTable(Consumer)")
    void createWithConsumerDelegates() {
        when(dynamoDbAsyncTable.createTable(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.create(b -> b
                .provisionedThroughput(p -> p
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(10L)));

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).createTable(any(Consumer.class));
    }

    @Test
    @DisplayName("create(null Consumer) throws NullPointerException")
    void createNullConsumerThrows() {
        AsyncTable<TestItem> table = createTable();
        assertThrows(NullPointerException.class, () -> table.create((Consumer) null));
    }

    // ========== CRUD convenience methods ==========

    @Test
    @DisplayName("getItem(pk, sk) delegates to DynamoDbAsyncTable.getItem(Key) and returns Optional")
    void getItemWithSortKeyDelegates() {
        TestItem item = new TestItem();
        when(dynamoDbAsyncTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(item));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Optional<TestItem>> result = table.getItem("pk", "sk");

        assertNotNull(result);
        assertEquals(item, result.join().orElse(null));
        verify(dynamoDbAsyncTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("deleteItem(pk, sk) delegates to DynamoDbAsyncTable.deleteItem(Key)")
    void deleteItemWithSortKeyDelegates() {
        when(dynamoDbAsyncTable.deleteItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.deleteItem("pk", "sk");

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).deleteItem(any(Key.class));
    }

    // ========== Builder return methods ==========

    @Test
    @DisplayName("get(pk) returns a non-null AsyncGetItemBuilder")
    void getWithPartitionKeyReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.get("pk"));
    }

    @Test
    @DisplayName("get(pk, sk) returns a non-null AsyncGetItemBuilder")
    void getWithSortKeyReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.get("pk", "sk"));
    }

    @Test
    @DisplayName("put(item) returns a non-null AsyncPutBuilder")
    void putReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.put(new TestItem()));
    }

    @Test
    @DisplayName("update(item) returns a non-null AsyncUpdateBuilder")
    void updateReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.update(new TestItem()));
    }

    @Test
    @DisplayName("updateItem(item) delegates to DynamoDbAsyncTable.updateItem(item) and returns item")
    void updateItemDelegates() {
        TestItem item = new TestItem();
        when(dynamoDbAsyncTable.updateItem(any(TestItem.class))).thenReturn(CompletableFuture.completedFuture(item));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<TestItem> result = table.updateItem(item);

        assertNotNull(result);
        assertSame(item, result.join());
        verify(dynamoDbAsyncTable).updateItem(item);
    }

    @Test
    @DisplayName("delete(pk) returns a non-null AsyncDeleteBuilder")
    void deleteWithPartitionKeyReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.delete("pk"));
    }

    @Test
    @DisplayName("delete(pk, sk) returns a non-null AsyncDeleteBuilder")
    void deleteWithSortKeyReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.delete("pk", "sk"));
    }

    @Test
    @DisplayName("batchGet() returns a non-null AsyncBatchGetBuilder")
    void batchGetReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.batchGet());
    }

    @Test
    @DisplayName("batchWrite() returns a non-null AsyncBatchWriteBuilder")
    void batchWriteReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.batchWrite());
    }

    @Test
    @DisplayName("query() returns a non-null AsyncQueryBuilder")
    void queryReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.query());
    }

    @Test
    @DisplayName("scan() returns a non-null AsyncScanBuilder")
    void scanReturnsBuilder() {
        AsyncTable<TestItem> table = createTable();
        assertNotNull(table.scan());
    }

    // ========== Accessor methods ==========

    @Test
    @DisplayName("getEnhancedClient() returns the underlying DynamoDbEnhancedAsyncClient")
    void getEnhancedClientReturnsClient() {
        AsyncTable<TestItem> table = createTable();
        assertSame(enhancedAsyncClient, table.getEnhancedClient());
    }

    @Test
    @DisplayName("getDynamoDbClient() returns the underlying DynamoDbAsyncClient")
    void getDynamoDbClientReturnsClient() {
        AsyncTable<TestItem> table = createTable();
        assertSame(dynamoDbAsyncClient, table.getDynamoDbClient());
    }

    // ========== DDL ==========

    @Test
    @DisplayName("create(CreateTableEnhancedRequest) delegates to DynamoDbAsyncTable.createTable(CreateTableEnhancedRequest)")
    void createWithRequestDelegates() {
        when(dynamoDbAsyncTable.createTable(any(CreateTableEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CreateTableEnhancedRequest request = CreateTableEnhancedRequest.builder().build();
        CompletableFuture<Void> result = table.create(request);

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).createTable(request);
    }

    // ========== Key-Only Update ==========

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
        when(dynamoDbAsyncTable.tableSchema()).thenReturn(schema);
    }

    @Test
    @DisplayName("update(pk, null, consumer) delegates to low-level client")
    void updateWithKeyOnlyDelegatesToLowLevelClient() {
        mockSchema(null);
        when(dynamoDbAsyncTable.tableName()).thenReturn("test-table");
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.update("pk-val", null, expr -> expr.set("name", "new"));
        assertNotNull(result);
        result.join();

        verify(dynamoDbAsyncClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    @DisplayName("update(pk, sk, consumer) delegates to low-level client with composite key")
    void updateWithKeyAndSortKeyDelegatesToLowLevelClient() {
        mockSchema("sk");
        when(dynamoDbAsyncTable.tableName()).thenReturn("test-table");
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.update("pk-val", "sk-val", expr -> expr.set("name", "new"));
        assertNotNull(result);
        result.join();

        verify(dynamoDbAsyncClient).updateItem(any(UpdateItemRequest.class));
    }
}
