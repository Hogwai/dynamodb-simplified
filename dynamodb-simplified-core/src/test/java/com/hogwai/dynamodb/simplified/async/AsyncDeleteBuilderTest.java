package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncDeleteBuilder")
class AsyncDeleteBuilderTest {

    static class TestItem {
        public String id;
        public String name;
    }

    @Mock
    private DynamoDbAsyncTable<TestItem> table;
    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;
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
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
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
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", "sk", null);

        // When
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertEquals(AttributeValue.builder().s("pk").build(), request.key().partitionKeyValue());
        assertTrue(request.key().sortKeyValue().isPresent());
        assertEquals(AttributeValue.builder().s("sk").build(), request.key().sortKeyValue().get());
        assertNull(request.conditionExpression());
    }

    @Test
    @DisplayName("execute with condition consumer includes condition expression and returns deleted item")
    void executeWithConditionConsumer() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        builder.condition(c -> c.eq("status", "archived"));
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
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
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        builder.onlyIfExists("id");
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
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
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        builder.condition(c -> c.eq("color", "blue"));
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
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

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        CompletableFuture<Optional<TestItem>> future = builder.execute();

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

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        CompletableFuture<Optional<TestItem>> future = builder.execute();

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

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);

        // When
        CompletableFuture<Optional<TestItem>> future = builder.execute();

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DynamoSimplifiedException.class, ex.getCause());
        assertSame(nonRuntimeEx, ex.getCause().getCause());
    }

    // ============ Return values ============

    @Test
    @DisplayName("returnValues with ALL_OLD uses low-level deleteItem and maps response")
    void returnValues_setsOnRequest() {
        // Given
        TestItem deletedItem = new TestItem();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk").build());
        DeleteItemResponse response = DeleteItemResponse.builder().attributes(itemMap).build();
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableSchema.mapToItem(itemMap)).thenReturn(deletedItem);

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, dynamoDbAsyncClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
        assertSame(deletedItem, result.get());
        verify(dynamoDbAsyncClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals("test-table", request.tableName());
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        assertEquals(Map.of("pk", AttributeValue.builder().s("pk").build()), request.key());
    }

    @Test
    @DisplayName("returnValues not set uses enhanced client path")
    void returnValues_none_usesEnhancedPath() {
        // Given
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, dynamoDbAsyncClient);

        // When
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isPresent());
        verify(table).deleteItem(any(DeleteItemEnhancedRequest.class));
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("returnValues with condition expression includes both in low-level request")
    void returnValues_withCondition() {
        // Given
        TestItem deletedItem = new TestItem();
        Map<String, AttributeValue> itemMap = Map.of("id", AttributeValue.builder().s("pk").build());
        DeleteItemResponse response = DeleteItemResponse.builder().attributes(itemMap).build();
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableSchema.mapToItem(itemMap)).thenReturn(deletedItem);

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, dynamoDbAsyncClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.condition(c -> c.eq("status", "archived"));
        builder.execute().join();

        // Then
        verify(dynamoDbAsyncClient).deleteItem(lowLevelRequestCaptor.capture());
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
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteItemResponse.builder().build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, dynamoDbAsyncClient);

        // When
        builder.returnValues(ReturnValue.NONE);
        Optional<TestItem> result = builder.execute().join();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returnValues with ALL_OLD wraps ConditionalCheckFailedException")
    void returnValues_wrapsConditionalCheckFailedException() {
        // Given
        ConditionalCheckFailedException sdkEx = ConditionalCheckFailedException.builder()
                .message("The conditional request failed")
                .build();
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(sdkEx));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, dynamoDbAsyncClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.condition(c -> c.eq("status", "active"));
        CompletableFuture<Optional<TestItem>> future = builder.execute();

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().startsWith("Conditional check failed:"));
    }

    @Test
    @DisplayName("returnValues with ALL_OLD and sort key builds key with both pk and sk")
    void returnValues_withSortKey() {
        // Given
        Map<String, AttributeValue> itemMap = Map.of(
                "pk", AttributeValue.builder().s("pk-val").build(),
                "sk", AttributeValue.builder().s("sk-val").build()
        );
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteItemResponse.builder().attributes(itemMap).build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk-val", "sk-val", dynamoDbAsyncClient);

        // When
        builder.returnValues(ReturnValue.ALL_OLD);
        builder.execute().join();

        // Then
        verify(dynamoDbAsyncClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        Map<String, AttributeValue> expectedKey = new HashMap<>();
        expectedKey.put("pk", AttributeValue.builder().s("pk-val").build());
        expectedKey.put("sk", AttributeValue.builder().s("sk-val").build());
        assertEquals(expectedKey, request.key());
    }

    // ============ Missed branches: sort key schema present but no sort key provided ============

    @Test
    @DisplayName("returnValues with sort key in schema but no sort key provided builds pk-only key")
    void returnValues_withSortKeySchemaButNoSortKey() {
        Map<String, AttributeValue> itemMap = Map.of("pk", AttributeValue.builder().s("pk-val").build());
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteItemResponse.builder().attributes(itemMap).build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk-val", /* sortKey= */ null, dynamoDbAsyncClient);

        builder.returnValues(ReturnValue.ALL_OLD);
        builder.execute().join();

        verify(dynamoDbAsyncClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        Map<String, AttributeValue> expectedKey = new HashMap<>();
        expectedKey.put("pk", AttributeValue.builder().s("pk-val").build());
        assertEquals(expectedKey, request.key());
    }

    @Test
    @DisplayName("returnValues with DynamoDbException from low-level client wraps into OperationFailedException")
    void returnValues_withDynamoDbException_throwsOperationFailedException() {
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        DynamoDbException.builder().message("Service error").build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk-val", null, dynamoDbAsyncClient);
        builder.returnValues(ReturnValue.ALL_OLD);
        CompletableFuture<Optional<TestItem>> future = builder.execute();

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(OperationFailedException.class, ex.getCause());
    }

    // ============ Missed branch: empty condition expression in returnValues path ============

    @Test
    @DisplayName("returnValues with empty condition expression does not include condition in low-level request")
    void returnValues_withEmptyConditionExpression() {
        Map<String, AttributeValue> itemMap = Map.of("pk", AttributeValue.builder().s("pk-val").build());
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteItemResponse.builder().attributes(itemMap).build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk-val", null, dynamoDbAsyncClient);
        builder.returnValues(ReturnValue.ALL_OLD);
        // ConditionExpression.builder().build() creates an empty condition expression
        builder.condition(ConditionExpression.builder().build());
        builder.execute().join();

        verify(dynamoDbAsyncClient).deleteItem(lowLevelRequestCaptor.capture());
        DeleteItemRequest request = lowLevelRequestCaptor.getValue();
        assertNull(request.conditionExpression());
    }

    // ============ Missed branch: non-null empty response attributes ============

    @Test
    @DisplayName("returnValues with non-null empty response attributes returns empty")
    void returnValues_withEmptyAttributes_returnsEmpty() {
        when(dynamoDbAsyncClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        DeleteItemResponse.builder().attributes(Map.of()).build()));
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");

        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk-val", null, dynamoDbAsyncClient);
        builder.returnValues(ReturnValue.ALL_OLD);
        Optional<TestItem> result = builder.execute().join();

        assertTrue(result.isEmpty());
    }

    // ============ Missed branch: empty condition expression in enhanced path ============

    @Test
    @DisplayName("execute with empty condition expression does not include condition in enhanced request")
    void execute_withEmptyConditionExpression() {
        when(table.deleteItem(any(DeleteItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        AsyncDeleteBuilder<TestItem> builder = new AsyncDeleteBuilder<>(table, "pk", null, null);
        builder.condition(ConditionExpression.builder().build());
        Optional<TestItem> result = builder.execute().join();

        assertTrue(result.isPresent());
        verify(table).deleteItem(requestCaptor.capture());
        DeleteItemEnhancedRequest request = requestCaptor.getValue();
        assertNull(request.conditionExpression());
    }
}
