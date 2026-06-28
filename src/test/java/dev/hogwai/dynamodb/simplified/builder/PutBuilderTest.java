package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Versioned;
import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PutBuilder")
class PutBuilderTest {

    @Mock
    private DynamoDbTable<TestItem> table;

    @Mock
    private DynamoDbTable<VersionedTestItem> versionedTable;

    @Mock
    private DynamoDbClient dynamoDbClient;

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

    static class VersionedTestItem implements Versioned {
        public String id;
        private Integer version;

        VersionedTestItem(String id) {
            this.id = id;
            this.version = 1;
        }

        @Override
        public Integer getVersion() {
            return version;
        }

        @Override
        public void setVersion(Integer version) {
            this.version = version;
        }
    }

    @Test
    @DisplayName("execute without conditions calls table.putItem with request containing the item")
    void executeWithoutConditions() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);

        // When
        builder.execute();

        // Then
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertSame(item, request.item());
        assertNull(request.conditionExpression());
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("execute with condition consumer calls putItem with condition expression")
    void executeWithConditionConsumer() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);

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
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("execute with onlyIfNotExists sets attribute_not_exists condition")
    void executeWithOnlyIfNotExists() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);

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
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("condition with exists sets attribute_exists condition expression")
    void executeWithExistsCondition() {
        // Given
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);

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
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ ReturnValues tests ============

    @Test
    @DisplayName("returnValues() returns itself for fluent chaining")
    void returnValues_returnsItself() {
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);

        assertSame(builder, builder.returnValues(ReturnValue.ALL_OLD));
    }

    @Test
    @DisplayName("returnValues(null) routes to enhanced client (same as never called)")
    void returnValues_null_usesEnhancedClient() {
        TestItem item = new TestItem("item-1");
        new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(null)
                .execute();

        verify(table).putItem(any(PutItemEnhancedRequest.class));
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("with returnValues ALL_OLD uses low-level client")
    void put_withReturnValuesAllOld_usesLowLevelClient() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD)
                .execute();

        verify(dynamoDbClient).putItem(lowLevelRequestCaptor.capture());
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
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD)
                .condition(c -> c.eq("status", "active"))
                .execute();

        verify(dynamoDbClient).putItem(lowLevelRequestCaptor.capture());
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
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.class);

        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD);

        assertThrows(ConditionFailedException.class, builder::execute);
    }

    @Test
    @DisplayName("without returnValues uses enhanced client even when low-level client is available")
    void put_withoutReturnValues_usesEnhancedClient() {
        TestItem item = new TestItem("item-1");

        new PutBuilder<>(table, item, dynamoDbClient)
                .execute();

        verify(table).putItem(any(PutItemEnhancedRequest.class));
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("enhanced path condition failure throws ConditionFailedException")
    void execute_withConditionFailure_throwsConditionFailedException() {
        TestItem item = new TestItem("item-1");
        doThrow(ConditionalCheckFailedException.class)
                .when(table).putItem(any(PutItemEnhancedRequest.class));

        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient)
                .condition(c -> c.eq("status", "active"));

        assertThrows(ConditionFailedException.class, builder::execute);
        verifyNoInteractions(dynamoDbClient);
    }

    // ============ Optimistic locking tests ============

    @Test
    @DisplayName("withOptimisticLocking returns itself for fluent chaining")
    void withOptimisticLocking_returnsItself() {
        TestItem item = new TestItem("item-1");
        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient);
        assertSame(builder, builder.withOptimisticLocking());
    }

    @Test
    @DisplayName("withOptimisticLocking on non-Versioned item executes without condition")
    void withOptimisticLocking_nonVersioned_executesWithoutCondition() {
        TestItem item = new TestItem("item-1");
        new PutBuilder<>(table, item, dynamoDbClient)
                .withOptimisticLocking()
                .execute();
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<TestItem> request = requestCaptor.getValue();
        assertNull(request.conditionExpression(), "Non-Versioned item should not get a condition");
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
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().attributes(itemMap).build());

        Optional<TestItem> result = new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD)
                .executeReturning();

        assertTrue(result.isPresent());
        assertSame(previousItem, result.get());
        verify(dynamoDbClient).putItem(lowLevelRequestCaptor.capture());
        PutItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_OLD, request.returnValues());
    }

    @Test
    @DisplayName("executeReturning without returnValues returns empty")
    void executeReturning_withoutReturnValues_returnsEmpty() {
        TestItem item = new TestItem("item-1");

        Optional<TestItem> result = new PutBuilder<>(table, item, dynamoDbClient)
                .executeReturning();

        assertTrue(result.isEmpty());
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("executeReturning with returnValues ALL_OLD and empty response returns empty")
    void executeReturning_withReturnValuesAllOld_emptyResponse() {
        TestItem item = new TestItem("item-1");
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemToMap(any(), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item-1").build()));
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        Optional<TestItem> result = new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD)
                .executeReturning();

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
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.class);

        PutBuilder<TestItem> builder = new PutBuilder<>(table, item, dynamoDbClient)
                .returnValues(ReturnValue.ALL_OLD);

        assertThrows(ConditionFailedException.class, builder::executeReturning);
    }

    // ============ Optimistic locking with existing condition ============

    @Test
    @DisplayName("withOptimisticLocking merges version condition with existing condition")
    void execute_withOptimisticLockingAndExistingCondition() {
        VersionedTestItem versionedItem = new VersionedTestItem("item-1");

        new PutBuilder<>(versionedTable, versionedItem, dynamoDbClient)
                .withOptimisticLocking()
                .condition(c -> c.exists("attr"))
                .execute();

        assertEquals(2, versionedItem.getVersion(), "Version should be incremented from 1 to 2");
        verify(versionedTable).putItem(any(PutItemEnhancedRequest.class));
    }
}
