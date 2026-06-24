package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncPutBuilder")
class AsyncPutBuilderTest {

    @Mock
    private DynamoDbAsyncTable<TestItem> table;

    @Captor
    private ArgumentCaptor<PutItemEnhancedRequest<TestItem>> requestCaptor;

    static class TestItem {
        public String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    @Test
    @DisplayName("execute without conditions calls table.putItem with request containing the item")
    void executeWithoutConditions() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        builder.execute().join();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with condition consumer calls putItem with condition expression")
    void executeWithConditionConsumer() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        builder.condition(c -> c.eq("status", "active"));
        builder.execute().join();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());

        Expression condition = request.conditionExpression();
        assertNotNull(condition);
        assertEquals("#n0 = :v0", condition.expression());
        assertEquals(Map.of("#n0", "status"), condition.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("active").build()), condition.expressionValues());
    }

    @Test
    @DisplayName("execute with onlyIfNotExists sets attribute_not_exists condition")
    void executeWithOnlyIfNotExists() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        builder.onlyIfNotExists("id");
        builder.execute().join();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());

        Expression condition = request.conditionExpression();
        assertNotNull(condition);
        assertEquals("attribute_not_exists(#n0)", condition.expression());
        assertEquals(Map.of("#n0", "id"), condition.expressionNames());
        assertTrue(condition.expressionValues().isEmpty());
    }

    @Test
    @DisplayName("execute throws ConditionFailedException on ConditionalCheckFailedException")
    void execute_throwsConditionFailedException() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        // When / Then
        CompletableFuture<Void> result = builder.execute();
        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("execute with direct FilterExpression overload includes condition expression")
    void executeWithDirectFilterExpression() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        FilterExpression fe = FilterExpression.builder().eq("color", "blue");

        // When
        builder.condition(fe);
        builder.execute().join();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());

        Expression condition = request.conditionExpression();
        assertNotNull(condition);
        assertEquals("#n0 = :v0", condition.expression());
        assertEquals(Map.of("#n0", "color"), condition.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("blue").build()), condition.expressionValues());
    }
}
