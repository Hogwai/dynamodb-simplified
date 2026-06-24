package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    @DisplayName("execute with direct FilterExpression overload includes condition expression and returns deleted item")
    void executeWithDirectFilterExpression() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null);
        FilterExpression fe = FilterExpression.builder().eq("color", "blue");

        // When
        builder.condition(fe);
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
}
