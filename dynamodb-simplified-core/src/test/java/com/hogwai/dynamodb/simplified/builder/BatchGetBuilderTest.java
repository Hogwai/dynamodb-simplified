package com.hogwai.dynamodb.simplified.builder;

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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
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

    // ============ addKey ============

    @Test
    @DisplayName("addKey with partition key returns self")
    void addKey_withPartitionKey() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);

        BatchGetBuilder<TestItem> result = builder.addKey("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("addKey with partition and sort key returns self")
    void addKey_withPartitionAndSortKey() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);

        BatchGetBuilder<TestItem> result = builder.addKey("pk", "sk");

        assertSame(builder, result);
    }

    // ============ addKeys ============

    @Test
    @DisplayName("addKeys with collection returns self")
    void addKeys_collection() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);
        List<Key> keys = List.of(
                Key.builder().partitionValue(AttributeValue.builder().s("k").build()).build()
        );

        BatchGetBuilder<TestItem> result = builder.addKeys(keys);

        assertSame(builder, result);
    }

    // ============ execute, empty keys ============

    @Test
    @DisplayName("execute with empty keys returns empty list and does not call batchGetItem")
    void execute_emptyKeys_returnsEmptyList() {
        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);

        List<TestItem> result = builder.execute();

        assertTrue(result.isEmpty());
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    // ============ execute, with keys ============

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

            BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);
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
        // The SDK's generateKeysAndAttributes lambda passes TableMetadata.primaryIndexName()
        // (which returns "$PRIMARY_INDEX") as the index name to Key.keyMap(), so
        // indexPartitionKey is called (not primaryPartitionKey).
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.indexPartitionKey(anyString())).thenReturn("id");

        // Mock the batch get result page iterable -> SdkIterable -> stream
        BatchGetResultPageIterable resultPageIterable = mock(BatchGetResultPageIterable.class);
        SdkIterable<TestItem> sdkIterable = mock(SdkIterable.class);
        TestItem expectedItem = new TestItem("item1");
        when(sdkIterable.stream()).thenReturn(Stream.of(expectedItem));
        when(resultPageIterable.resultsForTable(table)).thenReturn(sdkIterable);
        when(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).thenReturn(resultPageIterable);

        BatchGetBuilder<TestItem> builder = new BatchGetBuilder<>(enhancedClient, table);
        builder.addKey("pk");

        List<TestItem> result = builder.execute();

        assertEquals(1, result.size());
        assertSame(expectedItem, result.getFirst());
        verify(enhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }
}
