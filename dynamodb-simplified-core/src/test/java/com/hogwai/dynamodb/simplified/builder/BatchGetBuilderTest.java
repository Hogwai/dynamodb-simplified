package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.result.BatchGetResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchGetBuilder")
class BatchGetBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<TestItem> table;

    @Mock
    private TableSchema<TestItem> tableSchema;

    @Mock
    private TableMetadata tableMetadata;

    @Mock
    private DynamoDbClient dynamoDbClient;

    // ============ addKey ============

    @Test
    @DisplayName("addKey with partition key returns self")
    void addKey_withPartitionKey() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);

        BatchGetBuilder<TestItem> result = builder.addKey("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKey with partition and sort key returns self")
    void addKey_withPartitionAndSortKey() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);

        BatchGetBuilder<TestItem> result = builder.addKey("pk", "sk");

        assertSame(builder, result);
    }

    // ============ addKeys ============

    @Test
    @DisplayName("addKeys with collection returns self")
    void addKeys_collection() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
        List<Key> keys = List.of(
                Key.builder().partitionValue(AttributeValue.builder().s("k").build()).build()
        );

        BatchGetBuilder<TestItem> result = builder.addKeys(keys);

        assertSame(builder, result);
    }

    // ============ execute, empty keys ============

    @Test
    @DisplayName("execute with empty keys returns empty result and does not call batchGetItem")
    void execute_emptyKeys_returnsEmptyList() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);

        BatchGetResult<TestItem> result = builder.execute();

        assertTrue(result.items().isEmpty());
        assertFalse(result.hasUnprocessed());
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ consistentRead ============

    @Test
    @DisplayName("consistentRead(true) configures per-item strong consistency")
    @SuppressWarnings("unchecked")
    void consistentRead_true_usesConsistentRead() {
        // Mock table schema chain: table.tableSchema().itemType().rawClass()
        EnhancedType<TestItem> enhancedType = mock(EnhancedType.class);
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);

        // Mock the batch get result
        BatchGetResultPageIterable resultPageIterable = mock(BatchGetResultPageIterable.class);
        SdkIterable<TestItem> sdkIterable = mock(SdkIterable.class);
        when(sdkIterable.stream()).thenReturn(Stream.of());
        when(resultPageIterable.resultsForTable(table)).thenReturn(sdkIterable);
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).thenReturn(resultPageIterable);

        // Intercept ReadBatch.builder() to capture the consumer passed to addGetItem
        try (var _ = mockStatic(ReadBatch.class)) {
            ReadBatch.Builder<TestItem> batchBuilder = mock(ReadBatch.Builder.class);
            ReadBatch readBatch = mock(ReadBatch.class);

            when(ReadBatch.builder(TestItem.class)).thenReturn(batchBuilder);
            when(batchBuilder.mappedTableResource(table)).thenReturn(batchBuilder);
            when(batchBuilder.build()).thenReturn(readBatch);

            BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
            builder.consistentRead(true);
            builder.addKey("pk");
            builder.execute();

            // Capture the consumer and verify it sets consistentRead
            ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> consumerCaptor =
                    ArgumentCaptor.forClass(Consumer.class);
            verify(batchBuilder).addGetItem(consumerCaptor.capture());

            GetItemEnhancedRequest.Builder getItemBuilder = GetItemEnhancedRequest.builder();
            consumerCaptor.getValue().accept(getItemBuilder);
            assertTrue(getItemBuilder.build().consistentRead());
        }
    }

    // ============ execute, with keys ============

    @Test
    @DisplayName("execute with keys returns results from enhanced client")
    @SuppressWarnings("unchecked")
    void execute_withKeys_returnsResults() {
        // Mock table schema chain: table.tableSchema().itemType().rawClass()
        EnhancedType<TestItem> enhancedType = mock(EnhancedType.class);
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);

        // Mock table metadata (needed by ReadBatch.build() internally).
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // Mock the batch get result page iterable -> SdkIterable -> stream
        BatchGetResultPageIterable resultPageIterable = mock(BatchGetResultPageIterable.class);
        SdkIterable<TestItem> sdkIterable = mock(SdkIterable.class);
        TestItem expectedItem = new TestItem("item1");
        when(sdkIterable.stream()).thenReturn(Stream.of(expectedItem));
        when(resultPageIterable.resultsForTable(table)).thenReturn(sdkIterable);
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).thenReturn(resultPageIterable);

        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
        builder.addKey("pk");

        BatchGetResult<TestItem> result = builder.execute();

        assertEquals(1, result.items().size());
        assertSame(expectedItem, result.items().getFirst());
        assertFalse(result.hasUnprocessed());
        verify(enhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ project(String...) ============

    @Test
    @DisplayName("project(String...) returns self for chaining")
    void project_varargs_returnsSelf() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);

        BatchGetBuilder<TestItem> result = builder.project("attr1", "attr2");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("project(String...) causes execute to route to low-level client")
    @SuppressWarnings("unchecked")
    void project_varargs_routesToLowLevel() {
        // Mock the table schema so addKey works (index partition key lookup)
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("test_table");

        // Mock low-level response
        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));
        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);

        TestItem expectedItem = new TestItem("result1");
        when(tableSchema.mapToItem(any(Map.class))).thenReturn(expectedItem);

        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
        builder.addKey("pk1");
        builder.project("attr1", "attr2");
        builder.execute();

        verify(dynamoDbClient).batchGetItem(any(BatchGetItemRequest.class));
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ project(Consumer) ============

    @Test
    @DisplayName("project(Consumer<ProjectionExpression>) returns self for chaining")
    void project_consumer_returnsSelf() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);

        BatchGetBuilder<TestItem> result = builder.project(pb -> pb.include("attr1"));

        assertSame(builder, result);
    }

    @Test
    @DisplayName("project(Consumer) causes execute to route to low-level with consumer-derived expression")
    @SuppressWarnings("unchecked")
    void project_consumer_routesToLowLevel() {
        // Mock table schema chain
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");
        when(table.tableName()).thenReturn("test_table");

        // Mock low-level response
        Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        responses.put("test_table", List.of(Map.of("id", AttributeValue.builder().s("pk1").build())));
        BatchGetItemResponse mockResponse = mock(BatchGetItemResponse.class);
        when(mockResponse.responses()).thenReturn(responses);
        when(mockResponse.unprocessedKeys()).thenReturn(Map.of());
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);

        TestItem expectedItem = new TestItem("consumer-result");
        when(tableSchema.mapToItem(any(Map.class))).thenReturn(expectedItem);

        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
        builder.addKey("pk1");
        builder.project(pb -> pb.include("consumerAttr"));
        builder.execute();

        verify(dynamoDbClient).batchGetItem(any(BatchGetItemRequest.class));
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ limit validation ============

    @Test
    @DisplayName("execute with more than 100 keys throws IllegalArgumentException")
    void execute_exceedsKeyLimit_throws() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table, dynamoDbClient);
        // Add 101 keys
        for (int i = 0; i < 101; i++) {
            builder.addKey("pk" + i);
        }

        assertThrows(IllegalArgumentException.class, builder::execute);
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }
}
