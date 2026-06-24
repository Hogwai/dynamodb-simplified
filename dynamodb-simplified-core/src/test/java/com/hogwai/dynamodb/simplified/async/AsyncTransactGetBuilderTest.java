package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncTransactGetBuilder}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncTransactGetBuilder")
class AsyncTransactGetBuilderTest {

    // ============ Mocks ============

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DynamoDbAsyncTable<?> table;

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

    // ============ addGetItem ============

    @Test
    @DisplayName("addGetItem with partition key adds entry and execute returns results")
    void addGetItem_withPartitionKey() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        AsyncTable<?> asyncTableWrapper = createAsyncTable(table);
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);

        assertDoesNotThrow(() -> builder.addGetItem(asyncTableWrapper, "pk-value"));

        TransactGetResults results = builder.execute().join();
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("addGetItem with partition and sort key adds entry without throwing")
    void addGetItem_withPartitionAndSortKey() throws Exception {
        AsyncTable<?> asyncTableWrapper = createAsyncTable(table);
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);

        assertDoesNotThrow(() -> builder.addGetItem(asyncTableWrapper, "pk-value", "sk-value"));
    }

    // ============ Execute ============

    @Test
    @DisplayName("execute returns a non-null TransactGetResults instance")
    void execute_returnsTransactGetResults() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        AsyncTable<?> asyncTableWrapper = createAsyncTable(table);
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);

        builder.addGetItem(asyncTableWrapper, "pk-value");
        TransactGetResults results = builder.execute().join();

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("execute with multiple entries calls transactGetItems once")
    void execute_multipleEntries() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        AsyncTable<?> asyncTableWrapper = createAsyncTable(table);
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);

        builder.addGetItem(asyncTableWrapper, "pk1");
        builder.addGetItem(asyncTableWrapper, "pk2");
        builder.execute().join();

        verify(enhancedClient, times(1)).transactGetItems(any(TransactGetItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute with no entries returns empty TransactGetResults")
    void execute_empty_noEntries() {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);

        TransactGetResults results = builder.execute().join();

        assertNotNull(results);
        assertEquals(0, results.size());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("execute propagates error from enhanced client")
    void execute_propagatesError() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("transaction failed")));
        AsyncTable<?> asyncTableWrapper = createAsyncTable(table);
        AsyncTransactGetBuilder builder = new AsyncTransactGetBuilder(enhancedClient);
        builder.addGetItem(asyncTableWrapper, "pk");

        CompletableFuture<TransactGetResults> future = builder.execute();
        assertThrows(RuntimeException.class, future::join);
    }
}
