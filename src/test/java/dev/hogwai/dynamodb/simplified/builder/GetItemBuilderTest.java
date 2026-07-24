package dev.hogwai.dynamodb.simplified.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetItemBuilder")
class GetItemBuilderTest {

    // region Test Item

    static class TestItem {
        public String id;
        public String name;

        TestItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // endregion

    // region Mocks

    @Mock
    private DynamoDbTable<TestItem> table;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    // endregion

    // region Builders

    private GetItemBuilder<TestItem> pkOnlyBuilder;
    private GetItemBuilder<TestItem> pkSkBuilder;

    @BeforeEach
    void setUp() {
        pkOnlyBuilder = new GetItemBuilder<>(table, "pk-value", null, dynamoDbClient);
        pkSkBuilder = new GetItemBuilder<>(table, "pk-value", "sk-value", dynamoDbClient);
    }

    // endregion

    // region Helpers

    private void mockTableSchema(String pkName, String skName) {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn(pkName);
        when(tableMetadata.primarySortKey()).thenReturn(skName != null ? Optional.of(skName) : Optional.empty());
    }

    private void mockGetItemReturns(Map<String, AttributeValue> itemMap, TestItem item) {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(itemMap).build());
        when(tableSchema.mapToItem(itemMap)).thenReturn(item);
    }

    private void mockGetItemReturnsEmpty() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());
    }

    // endregion

    // region execute(), Simple GetItem

    @Test
    @DisplayName("execute() with only partition key calls getItem() with correct key")
    void execute_withPartitionKeyOnly() {
        TestItem expected = new TestItem("pk-value", "item1");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        Optional<TestItem> result = pkOnlyBuilder.execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        Key key = request.key();
        assertEquals(AttributeValue.builder().s("pk-value").build(), key.partitionKeyValue());
        assertTrue(key.sortKeyValue().isEmpty());
    }

    @Test
    @DisplayName("execute() with partition and sort key includes both in key")
    void execute_withPartitionAndSortKey() {
        TestItem expected = new TestItem("pk-value", "item2");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        Optional<TestItem> result = pkSkBuilder.execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        Key key = request.key();
        assertEquals(AttributeValue.builder().s("pk-value").build(), key.partitionKeyValue());
        assertTrue(key.sortKeyValue().isPresent());
        assertEquals(AttributeValue.builder().s("sk-value").build(), key.sortKeyValue().get());
    }

    @Test
    @DisplayName("execute() returns Optional.empty() when item not found")
    void execute_itemNotFound() {
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(null);

        Optional<TestItem> result = pkOnlyBuilder.execute();

        assertTrue(result.isEmpty());
        verify(table).getItem(any(GetItemEnhancedRequest.class));
    }

    // endregion

    // region execute(), With Projection

    @Test
    @DisplayName("execute() with projection uses low-level client with projection expression")
    void execute_withProjection() {
        TestItem expected = new TestItem("pk-value", "proj-item");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "name", AttributeValue.builder().s("proj-item").build()
        );
        mockTableSchema("id", null);
        mockGetItemReturns(itemMap, expected);

        Optional<TestItem> result = pkOnlyBuilder
                .project("name")
                .execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemRequest> captor =
                ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        assertEquals("#p0", request.projectionExpression());
        assertNotNull(request.expressionAttributeNames());
        assertEquals("name", request.expressionAttributeNames().get("#p0"));
    }

    @Test
    @DisplayName("execute() with projection but no match returns empty")
    void execute_withProjection_noMatch() {
        mockTableSchema("id", null);
        mockGetItemReturnsEmpty();

        Optional<TestItem> result = pkOnlyBuilder
                .project("name")
                .execute();

        assertTrue(result.isEmpty());
        verify(dynamoDbClient).getItem(any(GetItemRequest.class));
        verify(table, never()).getItem(any(GetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute() with projection and sort key includes sort key in low-level request")
    void execute_withProjectionAndSortKey() {
        TestItem expected = new TestItem("pk-value", "proj-sk-item");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "sk", AttributeValue.builder().s("sk-value").build(),
                "name", AttributeValue.builder().s("proj-sk-item").build()
        );
        // schema has BOTH partition and sort keys
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("id");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));

        when(table.tableName()).thenReturn("test-table");
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(itemMap).build());
        when(tableSchema.mapToItem(itemMap)).thenReturn(expected);

        GetItemBuilder<TestItem> builder = new GetItemBuilder<>(table, "pk-value", "sk-value", dynamoDbClient);
        Optional<TestItem> result = builder.project("name").execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        // Verify both keys in the request
        assertNotNull(request.key());
        assertEquals(2, request.key().size());
        assertEquals(AttributeValue.builder().s("pk-value").build(), request.key().get("id"));
        assertEquals(AttributeValue.builder().s("sk-value").build(), request.key().get("sk"));
        // Verify projection
        assertEquals("#p0", request.projectionExpression());
    }

    // endregion

    // region consistentRead

    @Test
    @DisplayName("consistentRead(true) sets consistentRead on the request")
    void consistentRead() {
        TestItem expected = new TestItem("pk-value", "item-cr");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        pkOnlyBuilder
                .consistentRead(true)
                .execute();

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    // endregion

    // region project(String...)

    @Test
    @DisplayName("project(\"attr1\", \"attr2\") passes projection expression to low-level client")
    void project_withVarargs() {
        TestItem expected = new TestItem("pk-value", "proj-varargs");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "name", AttributeValue.builder().s("proj-varargs").build()
        );
        mockTableSchema("id", null);
        mockGetItemReturns(itemMap, expected);

        pkOnlyBuilder
                .project("attr1", "attr2")
                .execute();

        ArgumentCaptor<GetItemRequest> captor =
                ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        assertNotNull(request.projectionExpression());
        assertTrue(request.projectionExpression().contains("#p0"));
        assertTrue(request.projectionExpression().contains("#p1"));
        assertNotNull(request.expressionAttributeNames());
        assertEquals("attr1", request.expressionAttributeNames().get("#p0"));
        assertEquals("attr2", request.expressionAttributeNames().get("#p1"));
    }

    // endregion

    // region project(Consumer)

    @Test
    @DisplayName("project(Consumer<ProjectionExpression>) includes attribute from consumer")
    void project_withConsumer() {
        TestItem expected = new TestItem("pk-value", "proj-consumer");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "name", AttributeValue.builder().s("proj-consumer").build()
        );
        mockTableSchema("id", null);
        mockGetItemReturns(itemMap, expected);

        pkOnlyBuilder
                .project(pb -> pb.include("consumerAttr"))
                .execute();

        ArgumentCaptor<GetItemRequest> captor =
                ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        assertNotNull(request.projectionExpression());
        assertTrue(request.projectionExpression().contains("#p0"));
        assertNotNull(request.expressionAttributeNames());
        assertEquals("consumerAttr", request.expressionAttributeNames().get("#p0"));
    }
}
// endregion
