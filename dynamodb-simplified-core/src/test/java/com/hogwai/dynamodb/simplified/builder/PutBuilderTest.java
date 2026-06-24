package com.hogwai.dynamodb.simplified.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PutBuilder")
class PutBuilderTest {

    @Mock
    private DynamoDbTable<TestItem> table;

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
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item);

        // When
        builder.execute();

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
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item);

        // When
        builder.condition(c -> c.eq("status", "active"));
        builder.execute();

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
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item);

        // When
        builder.onlyIfNotExists("id");
        builder.execute();

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
    @DisplayName("execute with condition consumer includes condition expression")
    void executeWithConditionConsumerDirect() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item);

        // When
        builder.condition(c -> c.eq("color", "blue"));
        builder.execute();

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

    @Test
    @DisplayName("condition with exists sets attribute_exists condition expression")
    void executeWithExistsCondition() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item);

        // When
        builder.condition(c -> c.exists("attr"));
        builder.execute();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());

        Expression condition = request.conditionExpression();
        assertNotNull(condition);
        assertEquals("attribute_exists(#n0)", condition.expression());
        assertEquals(Map.of("#n0", "attr"), condition.expressionNames());
        assertTrue(condition.expressionValues().isEmpty());
    }
}
