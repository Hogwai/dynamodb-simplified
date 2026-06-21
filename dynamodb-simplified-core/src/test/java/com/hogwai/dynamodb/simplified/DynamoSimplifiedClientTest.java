package com.hogwai.dynamodb.simplified;

import com.hogwai.dynamodb.simplified.builder.QueryBuilder;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamoSimplifiedClient}.
 * <p>
 * Uses Mockito to mock the underlying AWS SDK components. For tests that require
 * a {@link DynamoSimplifiedClient} instance with mocked dependencies, the
 * private constructor is accessed via reflection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoSimplifiedClient")
class DynamoSimplifiedClientTest {

    @Mock
    DynamoDbClient dynamoDbClient;

    @Mock
    DynamoDbEnhancedClient enhancedClient;

    @Mock
    DynamoDbTable<TestItem> dynamoDbTable;

    /**
     * A simple DynamoDB bean used as the item type in tests.
     */
    @DynamoDbBean
    public static class TestItem {
        private String id;
        private String name;

        public TestItem() {
            // Test constructor
        }

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * Creates a {@link DynamoSimplifiedClient} with the given mocked dependencies
     * by accessing the private constructor via reflection.
     */
    private static DynamoSimplifiedClient createClient(DynamoDbEnhancedClient ec, DynamoDbClient dc) {
        try {
            Constructor<DynamoSimplifiedClient> ctor = DynamoSimplifiedClient.class
                    .getDeclaredConstructor(DynamoDbEnhancedClient.class, DynamoDbClient.class);
            ctor.setAccessible(true);
            return ctor.newInstance(ec, dc);
        } catch (ReflectiveOperationException e) {
            throw new DynamoSimplifiedException("Failed to create DynamoSimplifiedClient via reflection", e);
        }
    }

    @Test
    @DisplayName("create(DynamoDbClient) returns a non-null instance")
    void createWithClient() {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.create(dynamoDbClient);
        assertNotNull(client);
    }

    @Test
    @DisplayName("table() returns a typed Table instance")
    void tableReturnsTypedTable() {
        when(enhancedClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbTable);

        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        Table<TestItem> table = client.table("test-table", TestItem.class);

        assertNotNull(table);
    }

    @Test
    @DisplayName("table().getItem() delegates to DynamoDbTable.getItem(Key)")
    void getItemDelegatesToDynamoDbTable() {
        when(enhancedClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbTable);

        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        Table<TestItem> table = client.table("test-table", TestItem.class);

        table.getItem("pk");

        verify(dynamoDbTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("table().query() returns a QueryBuilder")
    void queryReturnsQueryBuilder() {
        when(enhancedClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbTable);

        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        Table<TestItem> table = client.table("test-table", TestItem.class);

        QueryBuilder<TestItem> queryBuilder = table.query();

        assertNotNull(queryBuilder);
    }

    @Test
    @DisplayName("getEnhancedClient() returns the underlying DynamoDbEnhancedClient")
    void getEnhancedClient() {
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);

        assertSame(enhancedClient, client.getEnhancedClient());
    }
}
