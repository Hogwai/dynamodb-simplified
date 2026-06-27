package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.TransactionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
    record TestItem(String id) {
    }

    // ============ Mocks ============

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

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
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

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
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        assertNotNull(builder.update(tableWrapper, item));
    }

    // ============ Delete ============

    @Test
    @DisplayName("delete with partition key delegates to enhancedClient")
    void delete_withPartitionKey() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.delete(tableWrapper, "pk-value");
        builder.execute().join();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("delete with partition and sort key returns this builder")
    void delete_withPartitionAndSortKey() throws Exception {
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        assertNotNull(builder.delete(tableWrapper, "pk-value", "sk-value"));
    }

    // ============ Condition Check ============

    @Test
    @DisplayName("conditionCheck with partition key delegates to enhancedClient")
    void conditionCheck_withPartitionKey() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.conditionCheck(tableWrapper, "pk-value", expr -> expr.eq("status", "active"));
        builder.execute().join();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("conditionCheck with partition and sort key returns this builder")
    void conditionCheck_withSortKey() throws Exception {
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

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
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

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
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

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
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.put(tableWrapper, item);

        CompletableFuture<Void> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }

    // ============ Update with Expression ============

    @Test
    @DisplayName("updateWithExpression delegates to low-level dynamoDbAsyncClient")
    void updateWithExpression_delegatesToLowLevel() throws Exception {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.update(tableWrapper, item, expr -> expr.set("status", "active"));
        builder.execute().join();

        verify(dynamoDbAsyncClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        verify(enhancedClient, never()).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("mixed enhanced and expression operations use low-level path")
    void mixedEnhancedAndExpressionOperations() throws Exception {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item1 = new TestItem("item-1");
        TestItem item2 = new TestItem("item-2");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.put(tableWrapper, item1);
        builder.update(tableWrapper, item2, expr -> expr.remove("old-field"));
        builder.execute().join();

        verify(dynamoDbAsyncClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        verify(enhancedClient, never()).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ TransactionCanceledException handling ============

    @Test
    @DisplayName("executeEnhanced wraps TransactionCanceledException from enhanced client")
    void executeEnhanced_wrapsTransactionCanceledException() throws Exception {
        when(enhancedClient.transactWriteItems(any(TransactWriteItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        TransactionCanceledException.builder()
                                .cancellationReasons(List.of(
                                        CancellationReason.builder().code("ConditionalCheckFailed").message("Item exists").build()))
                                .build()));

        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.put(tableWrapper, item);

        CompletableFuture<Void> future = builder.execute();
        var ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TransactionFailedException.class, ex.getCause());
        var tfe = (TransactionFailedException) ex.getCause();
        assertEquals(1, tfe.getCancellationReasons().size());
        assertTrue(tfe.getCancellationReason(0).contains("ConditionalCheckFailed"));
    }

    @Test
    @DisplayName("executeLowLevel wraps TransactionCanceledException from low-level client")
    void executeLowLevel_wrapsTransactionCanceledException() throws Exception {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        TransactionCanceledException.builder()
                                .cancellationReasons(List.of(
                                        CancellationReason.builder().code("None").message("").build()))
                                .build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item = new TestItem("item-1");
        AsyncTable<TestItem> tableWrapper = createAsyncTable(table);
        AsyncTransactWriteBuilder builder = new AsyncTransactWriteBuilder(enhancedClient, dynamoDbAsyncClient);

        builder.update(tableWrapper, item, expr -> expr.set("status", "active"));

        CompletableFuture<Void> future = builder.execute();
        var ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TransactionFailedException.class, ex.getCause());
        var tfe = (TransactionFailedException) ex.getCause();
        assertEquals(1, tfe.getCancellationReasons().size());
        assertNull(tfe.getCancellationReason(0));
    }
}
