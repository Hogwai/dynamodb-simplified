package dev.hogwai.dynamodb.simplified.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncEntityTableBuilder")
class AsyncEntityTableBuilderTest {

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedAsyncClient;

    @Mock
    private DynamoDbAsyncTable<TestEntity> dynamoDbAsyncTable;

    @Entity(discriminator = "TEST", table = "test-table")
    @DynamoDbBean
    public static class TestEntity {
        private String pk;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public TestEntity() {
            // Default constructor
        }

        @DynamoDbPartitionKey
        public String getPk() { return pk; }
        public void setPk(String pk) { this.pk = pk; }
    }

    @Test
    @DisplayName("dynamoDbAsyncClient returns self for chaining")
    void dynamoDbAsyncClient_returnsSelf() {
        AsyncEntityTableBuilder<TestEntity> builder = new AsyncEntityTableBuilder<>(TestEntity.class);
        assertSame(builder, builder.dynamoDbAsyncClient(dynamoDbAsyncClient));
    }

    @Test
    @DisplayName("enhancedAsyncClient returns self for chaining")
    void enhancedAsyncClient_returnsSelf() {
        AsyncEntityTableBuilder<TestEntity> builder = new AsyncEntityTableBuilder<>(TestEntity.class);
        assertSame(builder, builder.enhancedAsyncClient(enhancedAsyncClient));
    }

    @Test
    @DisplayName("build returns a non-null AsyncEntityTable when both clients provided")
    void build_withBothClients_returnsEntityTable() {
        when(enhancedAsyncClient.table(any(String.class), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);

        AsyncEntityTableBuilder<TestEntity> builder = new AsyncEntityTableBuilder<>(TestEntity.class)
                .dynamoDbAsyncClient(dynamoDbAsyncClient)
                .enhancedAsyncClient(enhancedAsyncClient);
        AsyncEntityTable<TestEntity> result = builder.build();

        assertNotNull(result);
        assertEquals("test-table", result.tableName());
    }

    @Test
    @DisplayName("build passes a schema that persists the discriminator")
    void build_passesDecoratedSchema() {
        when(enhancedAsyncClient.table(any(String.class), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);
        AsyncEntityTableBuilder<TestEntity> builder = new AsyncEntityTableBuilder<>(TestEntity.class)
                .dynamoDbAsyncClient(dynamoDbAsyncClient)
                .enhancedAsyncClient(enhancedAsyncClient);

        builder.build();

        var schemaCaptor = org.mockito.ArgumentCaptor.forClass(TableSchema.class);
        verify(enhancedAsyncClient).table(eq("test-table"), schemaCaptor.capture());
        Map<String, AttributeValue> itemMap = schemaCaptor.getValue().itemToMap(new TestEntity(), false);
        assertEquals("TEST", itemMap.get("_type").s());
    }
}
