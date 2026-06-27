package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.expression.ConditionExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncUpdateBuilder")
class AsyncUpdateBuilderTest {

    @Mock
    DynamoDbAsyncTable<TestItem> table;
    @Mock
    DynamoDbAsyncClient dynamoDbAsyncClient;
    @Mock
    TableSchema<TestItem> tableSchema;
    @Mock
    Key key;

    @Captor
    ArgumentCaptor<UpdateItemEnhancedRequest<TestItem>> enhancedRequestCaptor;
    @Captor
    ArgumentCaptor<UpdateItemRequest> lowLevelRequestCaptor;

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
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(resultItem));

        Optional<TestItem> actual = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .execute()
                .join();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertSame(item, request.item());
        assertEquals(IgnoreNullsMode.SCALAR_ONLY, request.ignoreNullsMode());
        verifyNoInteractions(dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("ignoreNulls(true) sets SCALAR_ONLY mode on the request")
    void ignoreNulls() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(resultItem));

        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .ignoreNulls(true)
                .execute()
                .join();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        assertEquals(IgnoreNullsMode.SCALAR_ONLY, enhancedRequestCaptor.getValue().ignoreNullsMode());
    }

    @Test
    @DisplayName("condition(Consumer) adds condition expression to full-item update request")
    void conditionWithConsumer() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(resultItem));

        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .condition(c -> c.eq("status", "active"))
                .execute()
                .join();

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
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(resultItem));

        ConditionExpression conditionExpr = ConditionExpression.builder().eq("age", 18).build();
        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .condition(conditionExpr)
                .execute()
                .join();

        verify(table).updateItem(enhancedRequestCaptor.capture());
        UpdateItemEnhancedRequest<TestItem> request = enhancedRequestCaptor.getValue();
        assertNotNull(request.conditionExpression());
        assertEquals("#n0 = :v0", request.conditionExpression().expression());
        assertEquals(
                AttributeValue.builder().n("18").build(),
                request.conditionExpression().expressionValues().get(":v0"));
    }

    @Test
    @DisplayName("full update with condition includes condition expression and respects ignoreNulls")
    void fullUpdateWithCondition() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(resultItem));

        ConditionExpression condition = ConditionExpression.builder().eq("active", true).build();
        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .condition(condition)
                .ignoreNulls(false)
                .execute()
                .join();

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

    @Test
    @DisplayName("full update with condition failure throws ConditionFailedException")
    void fullUpdate_withConditionFailure() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        CompletableFuture<Optional<TestItem>> result = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .condition(c -> c.eq("status", "active"))
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
    }

    // ============ Partial update with expression path ============

    @Test
    @DisplayName("execute() with update expression performs partial update via low-level client")
    void execute_partialUpdate() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        Optional<TestItem> actual = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .update(u -> u.set("name", "new"))
                .execute()
                .join();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
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
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .update(u -> u.set("name", "new"))
                .condition(c -> c.eq("status", "active"))
                .execute()
                .join();

        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
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
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().build()));

        CompletableFuture<Optional<TestItem>> result = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .update(u -> u.set("name", "new"))
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ConditionFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("ignoreNulls(false) with update(Consumer) throws IllegalStateException")
    void ignoreNullsFalseWithPartialUpdateThrowsException() {
        var builder = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient);

        CompletableFuture<Optional<TestItem>> future = builder
                .update(u -> u.set("name", "updated"))
                .ignoreNulls(false)
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ============ ReturnValues tests ============

    @Test
    @DisplayName("returnValues() sets the returnValues field")
    void returnValues_setsReturnValue() {
        var builder = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient);

        assertSame(builder, builder.returnValues(ReturnValue.ALL_NEW));
    }

    @Test
    @DisplayName("partial update defaults to ReturnValue.ALL_NEW when returnValues not set")
    void update_returnValues_defaultAllNew() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .update(u -> u.set("name", "new"))
                .execute()
                .join();

        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_NEW, request.returnValues());
    }

    @Test
    @DisplayName("partial update with custom returnValues uses the specified value")
    void update_returnValues_customValue() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .update(u -> u.set("name", "new"))
                .returnValues(ReturnValue.NONE)
                .execute()
                .join();

        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.NONE, request.returnValues());
    }

    // ============ Key-only update tests ============

    /**
     * Sets up minimal TableSchema/TableMetadata mocks so that
     * {@code buildKeyMapFromValues} in the key-only constructor
     * does not NPE when calling {@code Key.primaryKeyMap(TableSchema)}.
     */
    @SuppressWarnings("unchecked")
    private TableSchema<TestItem> mockSchema(String sortKeyName) {
        TableSchema<TestItem> schema = mock(TableSchema.class);
        TableMetadata tableMetadata = mock(TableMetadata.class);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("key");
        if (sortKeyName != null) {
            when(tableMetadata.primarySortKey()).thenReturn(Optional.of(sortKeyName));
        } else {
            lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.empty());
        }
        when(schema.tableMetadata()).thenReturn(tableMetadata);
        when(table.tableSchema()).thenReturn(schema);
        return schema;
    }

    @Test
    @DisplayName("key-only constructor with expression performs partial update via low-level client")
    void execute_withKeyOnlyAndExpression_usesLowLevelClient() {
        TableSchema<TestItem> schema = mockSchema(null);
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(schema.mapToItem(any())).thenReturn(resultItem);
        when(table.tableName()).thenReturn("test-table");

        Optional<TestItem> actual = new AsyncUpdateBuilder<>(table, dynamoDbAsyncClient, "pk-val", null)
                .update(u -> u.set("name", "new"))
                .execute()
                .join();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertNotNull(request.key());
        assertFalse(request.key().isEmpty());
        assertEquals("SET #u0 = :u0", request.updateExpression());
        assertEquals("test-table", request.tableName());
        verify(table, never()).updateItem(any(UpdateItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("key-only constructor with sort key builds correct composite key")
    void execute_withKeyOnlyAndSortKey_buildsCompositeKey() {
        TableSchema<TestItem> schema = mockSchema("sk");
        when(dynamoDbAsyncClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UpdateItemResponse.builder().attributes(Map.of()).build()));
        when(schema.mapToItem(any())).thenReturn(resultItem);
        when(table.tableName()).thenReturn("test-table");

        new AsyncUpdateBuilder<>(table, dynamoDbAsyncClient, "pk-val", "sk-val")
                .update(u -> u.set("name", "new"))
                .execute()
                .join();

        verify(dynamoDbAsyncClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertNotNull(request.key());
        assertFalse(request.key().isEmpty());
        assertEquals("test-table", request.tableName());
    }

    @Test
    @DisplayName("key-only constructor without expression throws IllegalStateException")
    void execute_withKeyOnlyNoExpression_throwsIllegalStateException() {
        mockSchema(null);

        var builder = new AsyncUpdateBuilder<>(table, dynamoDbAsyncClient, "pk-val", null);

        CompletableFuture<Optional<TestItem>> result = builder.execute();
        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ============ ReturnValues without expression guard (missed branch) ============

    @Test
    @DisplayName("returnValues without update expression throws IllegalStateException")
    void returnValues_withoutExpression_throwsIllegalStateException() {
        CompletableFuture<Optional<TestItem>> result = new AsyncUpdateBuilder<>(table, item, dynamoDbAsyncClient)
                .returnValues(ReturnValue.ALL_NEW)
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("ReturnValues is not supported for full-item replacement"));
    }
}
