package com.hogwai.dynamodb.simplified.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchWriteBuilder")
class BatchWriteBuilderTest {

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

    // ============ put ============

    @Test
    @DisplayName("put adds item and returns self")
    void put_addsItem() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(enhancedClient, table);
        TestItem item = new TestItem("item1");

        BatchWriteBuilder<TestItem> result = builder.put(item);

        assertSame(builder, result);
    }

    // ============ delete ============

    @Test
    @DisplayName("delete with partition key returns self")
    void delete_withPartitionKey() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(enhancedClient, table);

        BatchWriteBuilder<TestItem> result = builder.delete("pk");

        assertSame(builder, result);
    }

    @Test
    @DisplayName("delete with partition and sort key returns self")
    void delete_withPartitionAndSortKey() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(enhancedClient, table);

        BatchWriteBuilder<TestItem> result = builder.delete("pk", "sk");

        assertSame(builder, result);
    }

    // ============ execute — empty queues ============

    @Test
    @DisplayName("execute with empty queues returns without calling batchWriteItem")
    void execute_emptyQueues_doesNothing() {
        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(enhancedClient, table);

        builder.execute();

        verify(enhancedClient, never()).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    // ============ execute — with put ============

    @Test
    @DisplayName("execute with put delegates to enhanced client")
    @SuppressWarnings("unchecked")
    void execute_withPut_delegatesToEnhancedClient() {
        // Mock table schema chain: table.tableSchema().itemType().rawClass()
        EnhancedType<TestItem> enhancedType = mock(EnhancedType.class);
        when(table.tableSchema()).thenReturn(tableSchema);
        when(tableSchema.itemType()).thenReturn(enhancedType);
        when(enhancedType.rawClass()).thenReturn(TestItem.class);

        // Mock table metadata (needed by WriteBatch.build() internally)
        when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        when(tableMetadata.primaryPartitionKey()).thenReturn("id");

        // Mock itemToMap (needed by PutItemOperation.generateRequest to build the PutRequest)
        when(tableSchema.itemToMap(any(TestItem.class), anyBoolean()))
                .thenReturn(Map.of("id", AttributeValue.builder().s("item1").build()));

        BatchWriteBuilder<TestItem> builder = new BatchWriteBuilder<>(enhancedClient, table);
        builder.put(new TestItem("item1"));

        assertDoesNotThrow(builder::execute);
        verify(enhancedClient).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }
}
