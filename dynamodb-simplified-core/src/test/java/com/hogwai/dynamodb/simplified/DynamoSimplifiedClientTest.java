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
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        @SuppressWarnings("checkstyle:RedundantModifier")
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

    @Test
    @DisplayName("transactGet() returns a non-null TransactGetBuilder")
    void transactGetReturnsBuilder() {
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);

        assertNotNull(client.transactGet());
    }

    @Test
    @DisplayName("transactWrite() returns a non-null TransactWriteBuilder")
    void transactWriteReturnsBuilder() {
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);

        assertNotNull(client.transactWrite());
    }

    @Test
    @DisplayName("table(String, TableSchema) returns a typed Table instance")
    void tableWithTableSchemaReturnsTypedTable() {
        when(enhancedClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbTable);
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        TableSchema<TestItem> schema = TableSchema.fromBean(TestItem.class);
        Table<TestItem> table = client.table("test-table", schema);
        assertNotNull(table);
    }

    @Test
    @DisplayName("create() no-arg returns a non-null instance")
    void createNoArg() {
        // Set AWS SDK system properties so DynamoDbClient.create() succeeds
        // (default provider chain reads these). Restore originals in finally.
        String origRegion = System.setProperty("aws.region", "us-east-1");
        String origKey = System.setProperty("aws.accessKeyId", "test-key");
        String origSecret = System.setProperty("aws.secretKey", "test-secret");
        try {
            DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
            assertNotNull(client);
            assertNotNull(client.getEnhancedClient());
        } finally {
            restoreProperty("aws.region", origRegion);
            restoreProperty("aws.accessKeyId", origKey);
            restoreProperty("aws.secretKey", origSecret);
        }
    }

    private static void restoreProperty(String key, String origValue) {
        if (origValue != null) {
            System.setProperty(key, origValue);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    @DisplayName("listTables() delegates to DynamoDbClient.listTables()")
    void listTables() {
        when(dynamoDbClient.listTables()).thenReturn(ListTablesResponse.builder().tableNames("table1", "table2").build());
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        List<String> tables = client.listTables();
        assertEquals(List.of("table1", "table2"), tables);
    }

    @Test
    @DisplayName("table(String, Class, Consumer) returns a Table and configures schema builder")
    void tableWithStaticTableSchemaConsumerReturnsTypedTable() {
        when(enhancedClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbTable);

        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        Table<TestItem> table =
                client.table("test-table", TestItem.class, b -> b.newItemSupplier(TestItem::new));

        assertNotNull(table);
        verify(enhancedClient).table(eq("test-table"), any(TableSchema.class));
    }

    @Test
    @DisplayName("executeStatement() delegates to DynamoDbClient.executeStatement()")
    void executeStatementDelegates() {
        ExecuteStatementRequest request = ExecuteStatementRequest.builder()
                .statement("SELECT * FROM \"test-table\"")
                .build();
        ExecuteStatementResponse expected = ExecuteStatementResponse.builder().build();
        when(dynamoDbClient.executeStatement(request)).thenReturn(expected);

        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        ExecuteStatementResponse response = client.executeStatement(request);

        assertSame(expected, response);
        verify(dynamoDbClient).executeStatement(request);
    }

    @Test
    @DisplayName("table(String, Class, null) throws NullPointerException")
    void tableWithNullConsumerThrows() {
        DynamoSimplifiedClient client = createClient(enhancedClient, dynamoDbClient);
        assertThrows(NullPointerException.class,
                () -> client.table("test-table", TestItem.class, null));
    }
}
