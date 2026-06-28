package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteBuilder")
class DeleteBuilderTest {

    static class TestItem {
        public String id;
        public String name;
    }

    @Mock
    private DynamoDbTable<TestItem> table;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private TableSchema<TestItem> tableSchema;
    @Mock
    private TableMetadata tableMetadata;

    @Captor
    private ArgumentCaptor<DeleteItemEnhancedRequest> requestCaptor;
    @Captor
    private ArgumentCaptor<DeleteItemRequest> lowLevelRequestCaptor;

    @Test
    @DisplayName("execute with partition key only deletes by pk and returns deleted item")
    void executeWithPartitionKeyOnly() {
        // Given
        TestItem deletedItem = new TestItem();
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(deletedItem);
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, null);

        // When
        Optional<TestItem> result = builder.execute();

        // Then
        assertTrue(result.isPresent());
        assertSame(deletedItem, result.get());
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertEquals(AttributeValue.builder().s("pk").build(), request.key().partitionKeyValue());
        assertTrue(request.key().sortKeyValue().isEmpty());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with partition and sort key deletes by pk+sk")
    void executeWithPartitionAndSortKey() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(new TestItem());
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", "sk", null);

        // When
        builder.execute();

        // Then
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertEquals(AttributeValue.builder().s("pk").build(), request.key().partitionKeyValue());
        assertTrue(request.key().sortKeyValue().isPresent());
        assertEquals(AttributeValue.builder().s("sk").build(), request.key().sortKeyValue().get());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression in request")
    void executeWithConditionConsumer() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(new TestItem());
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, null);

        // When
        builder.condition(c -> c.eq("status", "archived"));
        builder.execute();

        // Then
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
    @DisplayName("execute with onlyIfExists includes attribute_exists condition")
    void executeWithOnlyIfExists() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(new TestItem());
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, null);

        // When
        builder.onlyIfExists("id");
        builder.execute();

        // Then
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("attribute_exists(#n0)", request.conditionExpression().expression());
        assertEquals(Map.of("#n0", "id"), request.conditionExpression().expressionNames());
        assertTrue(request.conditionExpression().expressionValues().isEmpty());
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression")
    void executeWithConditionConsumerDirect() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(new TestItem());
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, null);

        // When
        builder.condition(c -> c.eq("color", "blue"));
        builder.execute();

        // Then
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

    @Test
    @DisplayName("execute returns the item returned by table.deleteItem")
    void executeReturnsDeletedItem() {
        // Given
        TestItem deletedItem = new TestItem();
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(deletedItem);
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", "sk", null);

        // When
        Optional<TestItem> result = builder.execute();

        // Then
        assertTrue(result.isPresent());
        assertSame(deletedItem, result.get());
    }

    @Test
    @DisplayName("returnValues with ALL_OLD uses low-level deleteItem and maps response")
    void returnValues_setsOnRequest() {
        // Given
        TestItem deletedItem = new TestItem();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk").build());
        DeleteItemResponse response = DeleteItemResponse.builder().attributes(itemMap).build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableSchema.mapToItem(itemMap)).thenReturn(deletedItem);

        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        Optional<TestItem> result = builder.execute();

        // Then
        assertTrue(result.isPresent());
        assertSame(deletedItem, result.get());
        verify(dynamoDbClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals("test-table", request.tableName());
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        assertEquals(Map.of("pk", AttributeValue.builder().s("pk").build()), request.key());
    }

    @Test
    @DisplayName("returnValues with ALL_OLD but no low-level client throws IllegalStateException")
    void returnValues_withoutLowLevelClient_throws() {
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.returnValues(ReturnValue.ALL_OLD));
        assertTrue(ex.getMessage().contains("low-level DynamoDbClient"));
    }

    @Test
    @DisplayName("returnValues not set uses enhanced client path")
    void returnValues_none_usesEnhancedPath() {
        // Given
        TestItem deletedItem = new TestItem();
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(deletedItem);
        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);

        // When
        Optional<TestItem> result = builder.execute();

        // Then
        assertTrue(result.isPresent());
        assertSame(deletedItem, result.get());
        verify(table).deleteItem(any(DeleteItemEnhancedRequest.class));
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("returnValues with condition expression includes both in low-level request")
    void returnValues_withCondition() {
        // Given
        TestItem deletedItem = new TestItem();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk").build());
        DeleteItemResponse response = DeleteItemResponse.builder().attributes(itemMap).build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableSchema.mapToItem(itemMap)).thenReturn(deletedItem);

        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.condition(c -> c.eq("status", "archived"));
        builder.execute();

        // Then
        verify(dynamoDbClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression());
        assertEquals(Map.of("#n0", "status"), request.expressionAttributeNames());
        assertEquals(
                Map.of(":v0", AttributeValue.builder().s("archived").build()),
                request.expressionAttributeValues()
        );
    }

    @Test
    @DisplayName("returnValues(NONE) returns null when response has no attributes")
    void returnValues_none_returnsNull() {
        // Given
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(DeleteItemResponse.builder().build());
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);

        // When
        builder.returnValues(ReturnValue.NONE);
        Optional<TestItem> result = builder.execute();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returnValues with ALL_OLD throws ConditionFailedException when condition fails")
    void returnValues_wrapsConditionalCheckFailedException() {
        // Given
        ConditionalCheckFailedException sdkEx = ConditionalCheckFailedException.builder()
                .message("The conditional request failed")
                .build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenThrow(sdkEx);
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.condition(c -> c.eq("status", "active"));

        // Then
        ConditionFailedException ex = assertThrows(ConditionFailedException.class, builder::execute);
        assertTrue(ex.getMessage().startsWith("Conditional check failed:"));
    }

    @Test
    @DisplayName("condition with ConditionExpression directly sets the condition")
    void executeWithDirectConditionExpression() {
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenReturn(new TestItem());
        var builder = new DeleteBuilder<>(table, "pk", null, null);
        ConditionExpression expr = ConditionExpression.builder().eq("status", "active").build();
        builder.condition(expr);
        builder.execute();
        verify(table).deleteItem(requestCaptor.capture());
        assertNotNull(requestCaptor.getValue().conditionExpression());
    }

    @Test
    @DisplayName("execute wraps DynamoDbException in OperationFailedException")
    void execute_wrapsDynamoDbException() {
        DynamoDbException sdkEx = mock(DynamoDbException.class);
        when(sdkEx.getMessage()).thenReturn("db error");
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class))).thenThrow(sdkEx);
        var builder = new DeleteBuilder<>(table, "pk", null, null);
        OperationFailedException ex = assertThrows(OperationFailedException.class, builder::execute);
        assertTrue(ex.getMessage().contains("DeleteItem"));
    }

    @Test
    @DisplayName("execute with returnValues wraps DynamoDbException in OperationFailedException")
    void execute_withReturnValues_wrapsDynamoDbException() {
        DynamoDbException sdkEx = mock(DynamoDbException.class);
        when(sdkEx.getMessage()).thenReturn("db error");
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenThrow(sdkEx);
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        var builder = new DeleteBuilder<>(table, "pk", null, dynamoDbClient);
        builder.returnValues(ReturnValue.ALL_OLD);
        OperationFailedException ex = assertThrows(OperationFailedException.class, builder::execute);
        assertTrue(ex.getMessage().contains("DeleteItem"));
    }

    @Test
    @DisplayName("returnValues with ALL_OLD and sort key builds key with both pk and sk")
    void returnValues_withSortKey() {
        // Given
        Map<String, AttributeValue> itemMap = Map.of(
                "pk", AttributeValue.builder().s("pk-val").build(),
                "sk", AttributeValue.builder().s("sk-val").build()
        );
        DeleteItemResponse response = DeleteItemResponse.builder().attributes(itemMap).build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));

        DeleteBuilder<TestItem> builder = new DeleteBuilder<>(table, "pk-val", "sk-val", dynamoDbClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.execute();

        // Then
        verify(dynamoDbClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        Map<String, AttributeValue> expectedKey = new java.util.HashMap<>();
        expectedKey.put("pk", AttributeValue.builder().s("pk-val").build());
        expectedKey.put("sk", AttributeValue.builder().s("sk-val").build());
        assertEquals(expectedKey, request.key());
    }
}
