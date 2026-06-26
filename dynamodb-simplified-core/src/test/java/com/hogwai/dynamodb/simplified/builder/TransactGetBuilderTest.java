package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.Table;
import com.hogwai.dynamodb.simplified.result.TransactGetResults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsResponse;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactGetBuilder}.
 * <p>
 * Tests confirm that {@code addGetItem} registers entries and that
 * {@code execute()} delegates to the enhanced client. The sort-key variant
 * verifies the fluent API only, because the SDK's {@code build()} step
 * validates sort keys against the table metadata, which requires a real
 * {@link software.amazon.awssdk.enhanced.dynamodb.TableSchema}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactGetBuilder")
class TransactGetBuilderTest {

    // ============ Mocks ============

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DynamoDbTable<?> table;

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

    // ============ addGetItem ============

    @Test
    @DisplayName("addGetItem with partition key adds entry and execute returns results")
    void addGetItem_withPartitionKey() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(List.of());
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        assertDoesNotThrow(() -> builder.addGetItem(tableWrapper, "pk-value"));

        TransactGetResults results = builder.execute();
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("addGetItem with partition and sort key adds entry without throwing")
    void addGetItem_withPartitionAndSortKey() throws Exception {
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        assertDoesNotThrow(() -> builder.addGetItem(tableWrapper, "pk-value", "sk-value"));
        // execute() not called because SDK's build() validates sort keys against
        // table metadata, which requires a real TableSchema.
    }

    // ============ Execute ============

    @Test
    @DisplayName("execute returns a non-null TransactGetResults instance")
    void execute_returnsTransactGetResults() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(List.of());
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk-value");
        TransactGetResults results = builder.execute();

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("execute with multiple entries calls transactGetItems once")
    void execute_multipleEntries() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(List.of());
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk1");
        builder.addGetItem(tableWrapper, "pk2");
        builder.execute();

        verify(enhancedClient, times(1)).transactGetItems(any(TransactGetItemsEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute with no entries returns empty TransactGetResults")
    void execute_empty_noEntries() {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(List.of());
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        TransactGetResults results = builder.execute();

        assertNotNull(results);
        assertEquals(0, results.size());
        assertTrue(results.isEmpty());
    }

    // ============ Projection ============

    @Test
    @DisplayName("project without entries throws IllegalStateException")
    void project_withoutEntries_throwsIllegalStateException() {
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);
        assertThrows(IllegalStateException.class, () -> builder.project("attr1"));
    }

    @Test
    @DisplayName("project with attributes uses low-level client")
    void project_withAttributes_usesLowLevelClient() throws Exception {
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk-value");
        builder.project("attr1", "attr2");

        when(dynamoDbClient.transactGetItems(any(TransactGetItemsRequest.class)))
                .thenReturn(TransactGetItemsResponse.builder()
                        .responses(ItemResponse.builder()
                                .item(Map.of("attr1", AttributeValue.fromS("val1")))
                                .build())
                        .build());

        TransactGetResults results = builder.execute();
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(enhancedClient, never()).transactGetItems(any(TransactGetItemsEnhancedRequest.class));
        verify(dynamoDbClient, times(1)).transactGetItems(any(TransactGetItemsRequest.class));
    }

    @Test
    @DisplayName("project with consumer uses low-level client")
    void project_withConsumer_usesLowLevelClient() throws Exception {
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk-value");
        builder.project(p -> p.include("attr1", "attr2"));

        when(dynamoDbClient.transactGetItems(any(TransactGetItemsRequest.class)))
                .thenReturn(TransactGetItemsResponse.builder()
                        .responses(ItemResponse.builder()
                                .item(Map.of("attr1", AttributeValue.fromS("val1")))
                                .build())
                        .build());

        TransactGetResults results = builder.execute();
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(enhancedClient, never()).transactGetItems(any(TransactGetItemsEnhancedRequest.class));
        verify(dynamoDbClient, times(1)).transactGetItems(any(TransactGetItemsRequest.class));
    }

    @Test
    @DisplayName("execute without projection uses enhanced client")
    void execute_withoutProjection_usesEnhancedClient() throws Exception {
        when(enhancedClient.transactGetItems(any(TransactGetItemsEnhancedRequest.class)))
                .thenReturn(List.of());
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk-value");
        builder.execute();

        verify(enhancedClient, times(1)).transactGetItems(any(TransactGetItemsEnhancedRequest.class));
        verify(dynamoDbClient, never()).transactGetItems(any(TransactGetItemsRequest.class));
    }

    @Test
    @DisplayName("execute with null item response returns null item")
    void execute_withNullItemResponse_returnsNullItem() throws Exception {
        Table<?> tableWrapper = createTable(table);
        TransactGetBuilder builder = new TransactGetBuilder(enhancedClient, dynamoDbClient);

        builder.addGetItem(tableWrapper, "pk-value");
        builder.project("attr1");

        when(dynamoDbClient.transactGetItems(any(TransactGetItemsRequest.class)))
                .thenReturn(TransactGetItemsResponse.builder()
                        .responses(ItemResponse.builder().build())
                        .build());

        TransactGetResults results = builder.execute();
        assertNotNull(results);
        assertEquals(1, results.size());
        assertNull(results.get(0));
    }

}
