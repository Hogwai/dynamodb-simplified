package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncGetItemBuilder")
class AsyncGetItemBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;
        public String name;

        TestItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    // ============ Builders ============

    private AsyncGetItemBuilder<TestItem> pkOnlyBuilder;
    private AsyncGetItemBuilder<TestItem> pkSkBuilder;

    @BeforeEach
    void setUp() {
        pkOnlyBuilder = new AsyncGetItemBuilder<>(table, "pk-value", null, dynamoDbAsyncClient);
        pkSkBuilder = new AsyncGetItemBuilder<>(table, "pk-value", "sk-value", dynamoDbAsyncClient);
    }

    // ============ Helpers ============

    private void mockTableSchema(String pkName, String skName) {
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn(pkName);
        when(tableMetadata.primarySortKey()).thenReturn(skName != null ? Optional.of(skName) : Optional.empty());
    }

    private void mockGetItemReturns(Map<String, AttributeValue> itemMap, TestItem item) {
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GetItemResponse.builder().item(itemMap).build()));
        when(tableSchema.mapToItem(itemMap)).thenReturn(item);
    }

    private void mockGetItemReturnsEmpty() {
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));
    }

    // ============ execute(), Simple GetItem ============

    @Test
    @DisplayName("execute() with only partition key calls getItem() with correct key")
    void execute_withPartitionKeyOnly() {
        TestItem expected = new TestItem("pk-value", "item1");
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(expected));

        Optional<TestItem> result = pkOnlyBuilder.execute().join();

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
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(expected));

        Optional<TestItem> result = pkSkBuilder.execute().join();

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
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Optional<TestItem> result = pkOnlyBuilder.execute().join();

        assertTrue(result.isEmpty());
        verify(table).getItem(any(GetItemEnhancedRequest.class));
    }

    // ============ execute(), With Projection ============

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
                .execute()
                .join();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemRequest> captor =
                ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbAsyncClient).getItem(captor.capture());
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
                .execute()
                .join();

        assertTrue(result.isEmpty());
        verify(dynamoDbAsyncClient).getItem(any(GetItemRequest.class));
        verify(table, never()).getItem(any(GetItemEnhancedRequest.class));
    }

    // ============ project(Consumer) ============

    @Test
    @DisplayName("project(Consumer) applies projection expression via consumer")
    void projectWithConsumer() {
        TestItem expected = new TestItem("pk-value", "consumer-proj");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "name", AttributeValue.builder().s("consumer-proj").build()
        );
        mockTableSchema("id", null);
        mockGetItemReturns(itemMap, expected);

        Optional<TestItem> result = pkOnlyBuilder
                .project(p -> p.include("name"))
                .execute()
                .join();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemRequest> captor =
                ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbAsyncClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        assertEquals("#p0", request.projectionExpression());
        assertNotNull(request.expressionAttributeNames());
        assertEquals("name", request.expressionAttributeNames().get("#p0"));
    }

    // ============ consistentRead ============

    @Test
    @DisplayName("consistentRead(true) sets consistentRead on the request")
    void consistentRead() {
        TestItem expected = new TestItem("pk-value", "item-cr");
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(expected));

        pkOnlyBuilder
                .consistentRead(true)
                .execute()
                .join();

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }
}
