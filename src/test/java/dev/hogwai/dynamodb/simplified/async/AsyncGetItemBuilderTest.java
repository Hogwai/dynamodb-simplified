package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
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
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncGetItemBuilder")
class AsyncGetItemBuilderTest {

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
    private DynamoDbAsyncTable<TestItem> table;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    // endregion

    // region Builders

    private AsyncGetItemBuilder<TestItem> pkOnlyBuilder;
    private AsyncGetItemBuilder<TestItem> pkSkBuilder;

    @BeforeEach
    void setUp() {
        pkOnlyBuilder = new AsyncGetItemBuilder<>(table, "pk-value", null, dynamoDbAsyncClient);
        pkSkBuilder = new AsyncGetItemBuilder<>(table, "pk-value", "sk-value", dynamoDbAsyncClient);
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
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GetItemResponse.builder().item(itemMap).build()));
        when(tableSchema.mapToItem(itemMap)).thenReturn(item);
    }

    private void mockGetItemReturnsEmpty() {
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));
    }

    // endregion

    // region execute(), Simple GetItem

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

    // endregion

    // region project(Consumer)

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

    // endregion

    // region consistentRead

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

    // endregion

    // region projection with sort key

    @Test
    @DisplayName("execute() with projection and sort key includes sort key in low-level key map")
    void execute_withProjectionAndSortKey_includesSortKey() {
        TestItem expected = new TestItem("pk-value", "proj-sk-item");
        Map<String, AttributeValue> itemMap = Map.of(
                "id", AttributeValue.builder().s("pk-value").build(),
                "sk", AttributeValue.builder().s("sk-value").build(),
                "name", AttributeValue.builder().s("proj-sk-item").build()
        );
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("id");
        when(tableMetadata.primarySortKey()).thenReturn(Optional.of("sk"));
        when(table.tableName()).thenReturn("test-table");
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GetItemResponse.builder().item(itemMap).build()));
        when(tableSchema.mapToItem(itemMap)).thenReturn(expected);

        Optional<TestItem> result = pkSkBuilder
                .project("name")
                .execute()
                .join();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbAsyncClient).getItem(captor.capture());
        GetItemRequest request = captor.getValue();

        assertNotNull(request.key());
        assertEquals(2, request.key().size());
        assertEquals(AttributeValue.builder().s("pk-value").build(), request.key().get("id"));
        assertEquals(AttributeValue.builder().s("sk-value").build(), request.key().get("sk"));
    }

    // endregion

    // region error handling - simple path

    @Test
    @DisplayName("execute() wraps DynamoDbException from simple path in OperationFailedException")
    void execute_withSimplePath_whenDynamoDbException() {
        DynamoDbException dde = mock(DynamoDbException.class);
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(dde));

        CompletableFuture<Optional<TestItem>> future = pkOnlyBuilder.execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(OperationFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("execute() rethrows generic exception from simple path as-is")
    void execute_withSimplePath_whenGenericException() {
        when(table.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("generic error")));

        CompletableFuture<Optional<TestItem>> future = pkOnlyBuilder.execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }

    // endregion

    // region error handling - projection path

    @Test
    @DisplayName("execute() with projection wraps DynamoDbException in OperationFailedException")
    void execute_withProjection_whenDynamoDbException() {
        mockTableSchema("id", null);
        DynamoDbException dde = mock(DynamoDbException.class);
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(dde));

        CompletableFuture<Optional<TestItem>> future = pkOnlyBuilder
                .project("name")
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(OperationFailedException.class, ex.getCause());
    }

    @Test
    @DisplayName("execute() with projection rethrows generic exception as-is")
    void execute_withProjection_whenGenericException() {
        mockTableSchema("id", null);
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("generic error")));

        CompletableFuture<Optional<TestItem>> future = pkOnlyBuilder
                .project("name")
                .execute();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }
}
// endregion
