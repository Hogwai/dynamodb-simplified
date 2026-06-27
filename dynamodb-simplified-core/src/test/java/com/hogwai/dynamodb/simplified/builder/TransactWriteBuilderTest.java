package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.exception.TransactionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactWriteBuilder}.
 * <p>
 * Tests confirm that each builder method registers operations correctly and
 * that {@code execute()} delegates to the enhanced client. Operations that
 * require real item serialization (e.g. update) or primary-index sort key
 * support are tested for fluent-API correctness only, without calling
 * {@code execute()}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactWriteBuilder")
class TransactWriteBuilderTest {

    // ============ Test Items ============

    @SuppressWarnings("unused")
    record TestItem(String id) {
    }

    // ============ Mocks ============

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DynamoDbTable<TestItem> table;

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    // ============ Helpers ============

    @SuppressWarnings("unchecked")
    private <T> Table<T> createTable(DynamoDbTable<T> dynamoDbTable)
            throws ReflectiveOperationException {
        Constructor<Table> ctor = Table.class.getDeclaredConstructor(
                DynamoDbEnhancedClient.class, DynamoDbTable.class, DynamoDbClient.class);
        ctor.setAccessible(true);
        return (Table<T>) ctor.newInstance(enhancedClient, dynamoDbTable, null);
    }

    // ============ Put ============

    @Test
    @DisplayName("put adds a put item and execute delegates to enhancedClient")
    void put_addsPutItem() throws Exception {
        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.put(tableWrapper, item);
        builder.execute();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Update ============

    @Test
    @DisplayName("update returns this builder")
    void update_returnsBuilder() throws Exception {
        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        assertNotNull(builder.update(tableWrapper, item));
    }

    // ============ Delete ============

    @Test
    @DisplayName("delete with partition key delegates to enhancedClient")
    void delete_withPartitionKey() throws Exception {
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.delete(tableWrapper, "pk-value");
        builder.execute();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("delete with partition and sort key returns this builder")
    void delete_withPartitionAndSortKey() throws Exception {
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        assertNotNull(builder.delete(tableWrapper, "pk-value", "sk-value"));
    }

    // ============ Condition Check ============

    @Test
    @DisplayName("conditionCheck with partition key delegates to enhancedClient")
    void conditionCheck_withPartitionKey() throws Exception {
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.conditionCheck(tableWrapper, "pk-value", expr -> expr.eq("status", "active"));
        builder.execute();

        verify(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("conditionCheck with partition and sort key returns this builder")
    void conditionCheck_withSortKey() throws Exception {
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        assertNotNull(builder.conditionCheck(tableWrapper, "pk-value", "sk-value",
                expr -> expr.eq("status", "active")));
    }

    // ============ Execute Delegation ============

    @Test
    @DisplayName("execute delegates to enhancedClient.transactWriteItems")
    void execute_delegatesToEnhancedClient() throws Exception {
        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.put(tableWrapper, item);
        builder.execute();

        verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Multiple Operations ============

    @Test
    @DisplayName("multiple put and delete operations chain into a single transaction")
    void multipleOperations() throws Exception {
        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.put(tableWrapper, item)
                .delete(tableWrapper, "pk-to-delete");

        // Update and conditionCheck are omitted here; they require item serialization
        // or sort-key metadata that is only resolvable with a real TableSchema.

        builder.execute();

        verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ Update with Expression ============

    @Test
    @DisplayName("updateWithExpression delegates to low-level dynamoDbClient")
    void updateWithExpression_delegatesToLowLevel() throws Exception {
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.update(tableWrapper, item, expr -> expr.set("status", "active"));
        builder.execute();

        verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        verify(enhancedClient, never()).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("mixed enhanced and expression operations use low-level path")
    void mixedEnhancedAndExpressionOperations() throws Exception {
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item1 = new TestItem("item-1");
        TestItem item2 = new TestItem("item-2");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.put(tableWrapper, item1);
        builder.update(tableWrapper, item2, expr -> expr.remove("old-field"));
        builder.execute();

        verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        verify(enhancedClient, never()).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
    }

    // ============ TransactionCanceledException handling ============

    @Test
    @DisplayName("executeEnhanced wraps TransactionCanceledException with reasons")
    void executeEnhanced_wrapsTransactionCanceledException() throws Exception {
        doThrow(TransactionCanceledException.builder()
                .cancellationReasons(List.of(
                        CancellationReason.builder().code("ConditionalCheckFailed").message("Item exists").build(),
                        CancellationReason.builder().code("None").message("").build()))
                .build())
                .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.put(tableWrapper, item);

        var ex = assertThrows(TransactionFailedException.class, builder::execute);
        assertEquals(2, ex.getCancellationReasons().size());
        assertTrue(ex.getCancellationReason(0).contains("ConditionalCheckFailed"));
        assertNull(ex.getCancellationReason(1));
    }

    @Test
    @DisplayName("executeLowLevel wraps TransactionCanceledException from low-level client")
    void executeLowLevel_wrapsTransactionCanceledException() throws Exception {
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .cancellationReasons(List.of(
                                CancellationReason.builder().code("None").message("").build()))
                        .build());
        when(table.tableName()).thenReturn("test-table");
        when(table.tableSchema()).thenReturn(mock(TableSchema.class));

        TestItem item = new TestItem("item-1");
        Table<TestItem> tableWrapper = createTable(table);
        TransactWriteBuilder builder = new TransactWriteBuilder(enhancedClient, dynamoDbClient);

        builder.update(tableWrapper, item, expr -> expr.set("status", "active"));

        var ex = assertThrows(TransactionFailedException.class, builder::execute);
        assertEquals(1, ex.getCancellationReasons().size());
        assertNull(ex.getCancellationReason(0));
        verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
    }
}
