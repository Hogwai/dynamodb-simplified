package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.Versioned;
import dev.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import dev.hogwai.dynamodb.simplified.expression.ConditionExpression;
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
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateBuilder")
class UpdateBuilderTest {

    @Mock
    DynamoDbTable<TestItem> table;
    @Mock
    DynamoDbTable<VersionedTestItem> versionedTable;
    @Mock
    DynamoDbClient dynamoDbClient;
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

    static class VersionedTestItem implements Versioned {
        public String id;
        public String name;
        private Integer version = 1;

        @Override
        public Integer getVersion() {
            return version;
        }

        @Override
        public void setVersion(Integer version) {
            this.version = version;
        }
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

        Optional<TestItem> actual = new UpdateBuilder<>(table, item, dynamoDbClient)
                .execute();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
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
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build());
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        Optional<TestItem> actual = new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"))
                .execute();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
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
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build());
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

    // ============ ReturnValues tests ============

    @Test
    @DisplayName("returnValues() sets the returnValues field")
    void returnValues_setsReturnValue() {
        var builder = new UpdateBuilder<>(table, item, dynamoDbClient);

        assertSame(builder, builder.returnValues(ReturnValue.ALL_NEW));
    }

    @Test
    @DisplayName("partial update defaults to ReturnValue.ALL_NEW when returnValues not set")
    void update_returnValues_defaultAllNew() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build());
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"))
                .execute();

        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.ALL_NEW, request.returnValues());
    }

    @Test
    @DisplayName("partial update with custom returnValues uses the specified value")
    void update_returnValues_customValue() {
        when(table.tableName()).thenReturn("test-table");
        when(table.keyFrom(any())).thenReturn(key);
        when(key.primaryKeyMap(any())).thenReturn(Map.of("id", AttributeValue.builder().s("123").build()));
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build());
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.mapToItem(any())).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .update(u -> u.set("name", "new"))
                .returnValues(ReturnValue.NONE)
                .execute();

        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertEquals(ReturnValue.NONE, request.returnValues());
    }

    // ============ Key-only update tests ============

    @SuppressWarnings("unchecked")
    private TableSchema<TestItem> mockSchema() {
        TableSchema<TestItem> schema = mock(TableSchema.class);
        TableMetadata tableMetadata = mock(TableMetadata.class);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("key");
        lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.empty());
        when(schema.tableMetadata()).thenReturn(tableMetadata);
        when(table.tableSchema()).thenReturn(schema);
        return schema;
    }

    @Test
    @DisplayName("key-only constructor with expression performs partial update via low-level client")
    void execute_withKeyOnlyAndExpression_usesLowLevelClient() {
        TableSchema<TestItem> schema = mockSchema();
        doReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build())
                .when(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
        when(schema.mapToItem(any())).thenReturn(resultItem);
        when(table.tableName()).thenReturn("test-table");

        Optional<TestItem> actual = new UpdateBuilder<>(table, dynamoDbClient, "pk-val", null)
                .update(u -> u.set("name", "new"))
                .execute();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
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
        TableSchema<TestItem> schema = mock(TableSchema.class);
        TableMetadata tableMetadata = mock(TableMetadata.class);
        when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));
        when(schema.tableMetadata()).thenReturn(tableMetadata);
        when(table.tableSchema()).thenReturn(schema);
        doReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build())
                .when(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
        when(schema.mapToItem(any())).thenReturn(resultItem);
        when(table.tableName()).thenReturn("test-table");

        new UpdateBuilder<>(table, dynamoDbClient, "pk-val", "sk-val")
                .update(u -> u.set("name", "new"))
                .execute();

        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertNotNull(request.key());
        assertFalse(request.key().isEmpty());
        assertEquals("test-table", request.tableName());
    }

    @Test
    @DisplayName("key-only constructor without expression throws IllegalStateException")
    void execute_withKeyOnlyNoExpression_throwsIllegalStateException() {
        mockSchema();

        var builder = new UpdateBuilder<>(table, dynamoDbClient, "pk-val", null);

        assertThrows(IllegalStateException.class, builder::execute);
    }

    // ============ Optimistic locking tests ============

    @Test
    @DisplayName("withOptimisticLocking returns itself for fluent chaining")
    void withOptimisticLocking_returnsItself() {
        var testItem = new TestItem();
        UpdateBuilder<TestItem> builder = new UpdateBuilder<>(table, testItem, dynamoDbClient);
        assertSame(builder, builder.withOptimisticLocking());
    }

    @Test
    @DisplayName("key-only constructor with optimistic locking skips locking because item is null")
    void execute_withKeyOnlyAndOptimisticLocking_doesNotApplyLocking() {
        TableSchema<TestItem> schema = mockSchema();
        doReturn(UpdateItemResponse.builder().attributes(Map.of("id", AttributeValue.builder().s("123").build())).build())
                .when(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
        when(schema.mapToItem(any())).thenReturn(resultItem);
        when(table.tableName()).thenReturn("test-table");

        Optional<TestItem> actual = new UpdateBuilder<>(table, dynamoDbClient, "pk-val", null)
                .withOptimisticLocking()
                .update(u -> u.set("name", "new"))
                .execute();

        assertTrue(actual.isPresent());
        assertSame(resultItem, actual.get());
        verify(dynamoDbClient).updateItem(lowLevelRequestCaptor.capture());
        UpdateItemRequest request = lowLevelRequestCaptor.getValue();
        assertNull(request.conditionExpression(), "Optimistic locking should not apply when item is null");
    }

    @Test
    @DisplayName("withOptimisticLocking on non-Versioned item skips version increment")
    void execute_withOptimisticLockingAndNonVersionedItem_doesNotIncrementVersion() {
        when(table.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(resultItem);

        new UpdateBuilder<>(table, item, dynamoDbClient)
                .withOptimisticLocking()
                .execute();

        // item is not Versioned, so version should remain unchanged
        assertEquals(1, item.version);
        verify(table).updateItem(any(UpdateItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("withOptimisticLocking merges version condition with existing condition")
    void execute_withOptimisticLockingAndExistingCondition() {
        VersionedTestItem versionedItem = new VersionedTestItem();
        versionedItem.id = "123";
        versionedItem.name = "original";
        VersionedTestItem versionedResultItem = new VersionedTestItem();
        versionedResultItem.id = "123";
        versionedResultItem.name = "updated";

        when(versionedTable.updateItem(any(UpdateItemEnhancedRequest.class))).thenReturn(versionedResultItem);

        new UpdateBuilder<>(versionedTable, versionedItem, dynamoDbClient)
                .withOptimisticLocking()
                .condition(c -> c.exists("attr"))
                .execute();

        assertEquals(2, versionedItem.getVersion(), "Version should be incremented from 1 to 2");
        ArgumentCaptor<UpdateItemEnhancedRequest<VersionedTestItem>> captor =
                ArgumentCaptor.forClass(UpdateItemEnhancedRequest.class);
        verify(versionedTable).updateItem(captor.capture());
        assertNotNull(captor.getValue().conditionExpression(),
                "Condition expression should be set (user condition merged with version check)");
    }
}
