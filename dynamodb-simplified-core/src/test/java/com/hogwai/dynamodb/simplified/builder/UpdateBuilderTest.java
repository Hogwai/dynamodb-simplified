package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateBuilder")
class UpdateBuilderTest {

    @Mock DynamoDbTable<TestItem> table;
    @Mock DynamoDbClient dynamoDbClient;
    @Mock TableSchema<TestItem> tableSchema;
    @Mock Key key;

    @Captor ArgumentCaptor<UpdateItemEnhancedRequest<TestItem>> enhancedRequestCaptor;
    @Captor ArgumentCaptor<UpdateItemRequest> lowLevelRequestCaptor;

    private TestItem item;
    private TestItem resultItem;

    static class TestItem {
        public String id;
        public String name;
        public int version;
    }

    @BeforeEach
    void setUp() {
        item = new TestItem();
        item.id = "123";
        item.name = "original";
        item.version = 1;

        resultItem = new TestItem();
        resultItem.id = "123";
        resultItem.name = "updated";
        resultItem.version = 2;
    }

    // ============ Full-item replacement path ============

    @Test
    @DisplayName("execute() without update expression performs full-item replacement via table.updateItem()")
    void execute_fullItemReplacement() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        TestItem actual = new UpdateBuilder<>(table, item, dynamoDbClient)
                .execute();

        assertSame(resultItem, actual);
        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertSame(item, request.item());
        assertEquals(IgnoreNullsMode.SCALAR_ONLY, request.ignoreNullsMode());
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("ignoreNulls(true) sets SCALAR_ONLY mode on the request")
    void ignoreNulls() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .ignoreNulls(true)
                .execute();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        assertEquals(IgnoreNullsMode.SCALAR_ONLY, enhancedRequestCaptor.getValue().ignoreNullsMode());
    }

    @Test
    @DisplayName("condition(Consumer) adds condition expression to full-item update request")
    void conditionWithConsumer() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .condition(c -> c.eq("status", "active"))
                .execute();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "status"), request.conditionExpression().expressionNames());
        assertEquals(
                AttributeValue.builder().s("active").build(),
                request.conditionExpression().expressionValues().get(":v0"));
    }

    @Test
    @DisplayName("condition(ConditionExpression) overload accepts a pre-built condition expression")
    void conditionWithConditionExpression() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        ConditionExpression conditionExpr = ConditionExpression.builder().eq("age", 18).build();
        new UpdateBuilder<>(table, item, dynamoDbClient)
                .condition(conditionExpr)
                .execute();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(
                AttributeValue.builder().n("18").build(),
                request.conditionExpression().expressionValues().get(":v0"));
    }

    @Test
    @DisplayName("full update with condition includes condition expression in UpdateItemEnhancedRequest and respects ignoreNulls")
    void fullUpdateWithCondition() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        ConditionExpression condition = ConditionExpression.builder().eq("active", true).build();
        new UpdateBuilder<>(table, item, dynamoDbClient)
                .condition(condition)
                .ignoreNulls(false)
                .execute();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertSame(item, request.item());
        assertEquals(IgnoreNullsMode.DEFAULT, request.ignoreNullsMode());
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "active"), request.conditionExpression().expressionNames());
        assertEquals(
                AttributeValue.builder().bool(true).build(),
                request.conditionExpression().expressionValues().get(":v0"));
    }

    // ============ Partial update with expression path ============

    @Test
    @DisplayName("execute() with update expression performs partial update via low-level client")
    void execute_partialUpdate() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of()).build());
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        TestItem actual = new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"))
                .execute();

        assertSame(resultItem, actual);
        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals("SET #u0 = :u0", request.updateExpression());
        assertEquals(Map.of("#u0", "name"), request.expressionAttributeNames());
        assertEquals(
                AttributeValue.builder().s("new").build(),
                request.expressionAttributeValues().get(":u0"));
        assertEquals("test-table", request.tableName());
        verify(table, never()).updateItem(any(UpdateItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute() with update expression and condition merges expression names and values")
    void execute_partialUpdateWithCondition() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of()).build());
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"))
                .condition(c -> c.eq("status", "active"))
                .execute();

        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals("SET #u0 = :u0", request.updateExpression());
        assertEquals("#n0 = :v0", request.conditionExpression());

        Map<String, String> names = request.expressionAttributeNames();
        assertEquals("name", names.get("#u0"));
        assertEquals("status", names.get("#n0"));
        assertEquals(2, names.size());

        Map<String, AttributeValue> values = request.expressionAttributeValues();
        assertEquals(AttributeValue.builder().s("new").build(), values.get(":u0"));
        assertEquals(AttributeValue.builder().s("active").build(), values.get(":v0"));
        assertEquals(2, values.size());

        verify(table, never()).updateItem(any(UpdateItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute() with update expression wraps ConditionalCheckFailedException")
    void partialUpdate_withConditionFailure() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.class);

        var updateBuilder = new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"));
        assertThrows(ConditionFailedException.class, updateBuilder::execute);
    }

    @Test
    @DisplayName("ignoreNulls(false) with update(Consumer) throws IllegalStateException")
    void ignoreNullsFalseWithPartialUpdateThrowsException() {
        var builder = new UpdateBuilder<>(table, item, dynamoDbClient);
        var updateBuilder = builder.update(u -> u.set("name", "updated"))
                                   .ignoreNulls(false);

        assertThrows(IllegalStateException.class, updateBuilder::execute);
    }
}
