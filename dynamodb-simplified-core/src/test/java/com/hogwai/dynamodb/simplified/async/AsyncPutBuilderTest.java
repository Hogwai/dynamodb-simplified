package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncPutBuilder")
class AsyncPutBuilderTest {

    @Mock
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Captor
    private ArgumentCaptor<PutItemEnhancedRequest<TestItem>> requestCaptor;

    @Captor
    private ArgumentCaptor<PutItemRequest> lowLevelRequestCaptor;

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
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        builder.execute().join();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());
        assertNull(request.conditionExpression());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("execute with condition consumer calls putItem with condition expression")
    void executeWithConditionConsumer() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);
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
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("execute with onlyIfNotExists sets attribute_not_exists condition")
    void executeWithOnlyIfNotExists() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);
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
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("execute throws ConditionFailedException on ConditionalCheckFailedException")
    void execute_throwsConditionFailedException() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        // When / Then
        CompletableFuture<Void> result = builder.execute();
        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression")
    void executeWithConditionConsumerDirect() {
        // Given
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        builder.condition(c -> c.eq("color", "blue"));
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
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // ============ ReturnValues tests ============

    @Test
    @DisplayName("returnValues() sets the returnValues field")
    void returnValues_setsReturnValue() {
        TestItem item = new TestItem("item-1");
        AsyncPutBuilder<TestItem> builder = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient);

        assertSame(builder, builder.returnValues(ReturnValue.ALL_OLD));
    }

    @Test
    @DisplayName("with returnValues ALL_OLD uses low-level async client")
    void put_withReturnValuesAllOld_usesLowLevelClient() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

        new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .execute()
                .join();

        verify(dynamoDbAsyncClient).putItem(lowLevelRequestCaptor.capture());
        PutItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals("test-table", request.tableName());
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        assertEquals(Map.of("id", AttributeValue.builder().s("item-1").build()), request.item());
        verify(table, never()).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("with returnValues and condition includes condition expression in low-level request")
    void put_withReturnValues_conditionStillWorks() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

        new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .condition(c -> c.eq("status", "active"))
                .execute()
                .join();

        verify(dynamoDbAsyncClient).putItem(lowLevelRequestCaptor.capture());
        PutItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        assertEquals("#n0 = :v0", request.conditionExpression());
        assertEquals(Map.of("#n0", "status"), request.expressionAttributeNames());
        assertEquals(
                AttributeValue.builder().s("active").build(),
                request.expressionAttributeValues().get(":v0"));
        verify(table, never()).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("with returnValues and failed condition throws ConditionFailedException")
    void put_withReturnValues_failedCondition_throwsConditionFailedException() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        CompletableFuture<Void> result = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("without returnValues uses enhanced client even when async client is available")
    void put_withoutReturnValues_usesEnhancedClient() {
        TestItem item = new TestItem("item-1");
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .execute()
                .join();

        verify(table).putItem(any(PutItemEnhancedRequest.class));
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    // ============ executeReturning tests ============

    @Test
    @DisplayName("executeReturning with returnValues ALL_OLD returns the previous item")
    void executeReturning_withReturnValuesAllOld_returnsItem() {
        TestItem item = new TestItem("item-1");
        TestItem previousItem = new TestItem("old-item");
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("old-item").build());
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(tableSchema.mapToItem(itemMap)).thenReturn(previousItem);
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        PutItemResponse.builder().attributes(itemMap).build()));

        Optional<TestItem> result = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .executeReturning()
                .join();

        assertTrue(result.isPresent());
        assertSame(previousItem, result.get());
        verify(dynamoDbAsyncClient).putItem(lowLevelRequestCaptor.capture());
        PutItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
    }

    @Test
    @DisplayName("executeReturning without returnValues returns empty")
    void executeReturning_withoutReturnValues_returnsEmpty() {
        TestItem item = new TestItem("item-1");

        Optional<TestItem> result = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .executeReturning()
                .join();

        assertTrue(result.isEmpty());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("executeReturning with returnValues ALL_OLD and empty response returns empty")
    void executeReturning_withReturnValuesAllOld_emptyResponse() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

        Optional<TestItem> result = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .executeReturning()
                .join();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("executeReturning with failed condition throws ConditionFailedException")
    void executeReturning_withFailedCondition_throwsConditionFailedException() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbAsyncClient.putItem(any(PutItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        CompletableFuture<Optional<TestItem>> result = new AsyncPutBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_OLD)
                .executeReturning();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
    }
}
