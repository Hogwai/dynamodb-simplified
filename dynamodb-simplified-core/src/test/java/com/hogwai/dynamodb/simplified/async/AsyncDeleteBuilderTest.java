package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncDeleteBuilder")
class AsyncDeleteBuilderTest {

    static class TestItem {
        public String id;
        public String name;
    }

    @Mock
    private DynamoDbAsyncTable<TestItem> table;

    @Captor
    private ArgumentCaptor<DeleteItemEnhancedRequest> requestCaptor;

    @Test
    @DisplayName("execute with partition key only deletes by pk and returns deleted item")
    void executeWithPartitionKeyOnly() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        TestItem result = builder.execute().join();

        // Then
        assertNotNull(result);
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertEquals(AttributeValue.builder().s("pk").build(), request.key().partitionKeyValue());
        assertTrue(request.key().sortKeyValue().isEmpty());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with partition and sort key deletes by pk+sk and returns deleted item")
    void executeWithPartitionAndSortKey() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", "sk");

        // When
        TestItem result = builder.execute().join();

        // Then
        assertNotNull(result);
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertEquals(AttributeValue.builder().s("pk").build(), request.key().partitionKeyValue());
        assertEquals(AttributeValue.builder().s("sk").build(), request.key().sortKeyValue().get());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression and returns deleted item")
    void executeWithConditionConsumer() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        builder.condition(c -> c.eq("status", "archived"));
        TestItem result = builder.execute().join();

        // Then
        assertNotNull(result);
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "status"), request.conditionExpression().expressionNames());
        assertEquals(
                Map.of(":v0", AttributeValue.builder().s("archived").build()),
                request.conditionExpression().expressionValues()
        );
    }

    @Test
    @DisplayName("execute with onlyIfExists includes attribute_exists condition and returns deleted item")
    void executeWithOnlyIfExists() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        builder.onlyIfExists("id");
        TestItem result = builder.execute().join();

        // Then
        assertNotNull(result);
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("attribute_exists(#n0)", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "id"), request.conditionExpression().expressionNames());
        assertTrue(request.conditionExpression().expressionValues().isEmpty());
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression and returns deleted item")
    void executeWithConditionConsumerDirect() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        builder.condition(c -> c.eq("color", "blue"));
        TestItem result = builder.execute().join();

        // Then
        assertNotNull(result);
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "color"), request.conditionExpression().expressionNames());
        assertEquals(
                Map.of(":v0", AttributeValue.builder().s("blue").build()),
                request.conditionExpression().expressionValues()
        );
    }

    // ============ Error paths ============

    @Test
    @DisplayName("execute wraps ConditionalCheckFailedException into ConditionFailedException")
    void execute_wrapsConditionalCheckFailedException() {
        // Given
        ConditionalCheckFailedException sdkEx = ConditionalCheckFailedException.builder()
                .message("The conditional request failed")
                .build();
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(sdkEx));

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        CompletableFuture<TestItem> future = builder.execute();

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().startsWith("Conditional check failed:"));
    }

    @Test
    @DisplayName("execute rethrows RuntimeException thrown by the SDK")
    void execute_rethrowsRuntimeException() {
        // Given
        RuntimeException runtimeEx = new IllegalStateException("some runtime error");
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(runtimeEx));

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        CompletableFuture<TestItem> future = builder.execute();

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("some runtime error", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("execute wraps non-RuntimeException in DynamoSimplifiedException")
    void execute_wrapsNonRuntimeException() {
        // Given
        Exception nonRuntimeEx = new Exception("checked exception");
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(nonRuntimeEx));

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);

        // When
        CompletableFuture<TestItem> future = builder.execute();

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DynamoSimplifiedException.class, ex.getCause());
        assertSame(nonRuntimeEx, ex.getCause().getCause());
    }
}
