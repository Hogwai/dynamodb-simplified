package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncTransactWriteBuilder}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncTransactWriteBuilder")
class AsyncTransactWriteBuilderTest {

    // ============ Test Items ============

    @SuppressWarnings("unused")
    static class TestItem {
        final String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    // ============ Helpers ============

    @SuppressWarnings("unchecked")
    private <T> AsyncTable<T> createAsyncTable(DynamoDbAsyncTable<T> dynamoDbAsyncTable)
            throws ReflectiveOperationException {
        Constructor<AsyncTable> ctor = AsyncTable.class.getDeclaredConstructor(
                DynamoDbEnhancedAsyncClient.class, DynamoDbAsyncTable.class, DynamoDbAsyncClient.class);
        ctor.setAccessible(true);
        return (AsyncTable<T>) ctor.newInstance(enhancedClient, dynamoDbAsyncTable, null);
    }

    // ============ Put ============

    @Test
    @DisplayName("put adds a put item and execute delegates to enhancedClient")
    void put_addsPutItem() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.put(tableWrapper, item);
        builder.execute().join();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Update ============

    @Test
    @DisplayName("update returns this builder")
    void update_returnsBuilder() throws Exception {
        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        assertNotNull(builder.update(tableWrapper, item));
    }

    // ============ Delete ============

    @Test
    @DisplayName("delete with partition key delegates to enhancedClient")
    void delete_withPartitionKey() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.delete(tableWrapper, "pk-value");
        builder.execute().join();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("delete with partition and sort key returns this builder")
    void delete_withPartitionAndSortKey() throws Exception {
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        assertNotNull(builder.delete(tableWrapper, "pk-value", "sk-value"));
    }

    // ============ Condition Check ============

    @Test
    @DisplayName("conditionCheck with partition key delegates to enhancedClient")
    void conditionCheck_withPartitionKey() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.conditionCheck(tableWrapper, "pk-value", expr -> expr.eq("status", "active"));
        builder.execute().join();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("conditionCheck with partition and sort key returns this builder")
    void conditionCheck_withSortKey() throws Exception {
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        assertNotNull(builder.conditionCheck(tableWrapper, "pk-value", "sk-value",
                expr -> expr.eq("status", "active")));
    }

    // ============ Execute Delegation ============

    @Test
    @DisplayName("execute delegates to enhancedClient.transactWriteItems")
    void execute_delegatesToEnhancedClient() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.put(tableWrapper, item);
        builder.execute().join();

        verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Multiple Operations ============

    @Test
    @DisplayName("multiple put and delete operations chain into a single transaction")
    void multipleOperations() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.put(tableWrapper, item)
                .delete(tableWrapper, "pk-to-delete");

        builder.execute().join();

        verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Error propagation ============

    @Test
    @DisplayName("execute propagates error from enhanced client")
    void execute_propagatesError() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("transaction failed")));
        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient);

        builder.put(tableWrapper, item);

        CompletableFuture<Void> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }
}
