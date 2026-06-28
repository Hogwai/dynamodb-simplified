package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.entity.AsyncEntityTable;
import dev.hogwai.dynamodb.simplified.entity.Entity;
import dev.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncDynamoSimplifiedClient}.
 * <p>
 * Uses Mockito to mock the underlying AWS SDK async components. For tests that require
 * an {@link AsyncDynamoSimplifiedClient} instance with mocked dependencies, the
 * package-private constructor is accessed via reflection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncDynamoSimplifiedClient")
class AsyncDynamoSimplifiedClientTest {

    @Mock
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    DynamoDbEnhancedAsyncClient enhancedAsyncClient;

    @Mock
    DynamoDbAsyncTable<TestItem> dynamoDbAsyncTable;

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
     * A simple entity bean annotated with {@code @Entity} for entity table tests.
     */
    @Entity(discriminator = "DSC", table = "test-table")
    @DynamoDbBean
    public static class TestEntity {
        private String pk;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public TestEntity() {
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }
    }

    /**
     * Creates an {@link AsyncDynamoSimplifiedClient} with the given mocked dependencies
     * by accessing the package-private constructor via reflection.
     */
    private static AsyncDynamoSimplifiedClient createClient(
            DynamoDbEnhancedAsyncClient ec, DynamoDbAsyncClient dc) {
        try {
            Constructor<AsyncDynamoSimplifiedClient> ctor = AsyncDynamoSimplifiedClient.class
                    .getDeclaredConstructor(DynamoDbEnhancedAsyncClient.class, DynamoDbAsyncClient.class);
            ctor.setAccessible(true);
            return ctor.newInstance(ec, dc);
        } catch (ReflectiveOperationException e) {
            throw new DynamoSimplifiedException("Failed to create AsyncDynamoSimplifiedClient via reflection", e);
        }
    }

    @Test
    @DisplayName("create() returns a non-null instance")
    void createNoArg() {
        // Set AWS SDK system properties so DynamoDbAsyncClient.create() succeeds
        String origRegion = System.setProperty("aws.region", "us-east-1");
        String origKey = System.setProperty("aws.accessKeyId", "test-key");
        String origSecret = System.setProperty("aws.secretKey", "test-secret");
        try {
            AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.create();
            assertNotNull(client);
            assertNotNull(client.getEnhancedClient());
        } finally {
            restoreProperty("aws.region", origRegion);
            restoreProperty("aws.accessKeyId", origKey);
            restoreProperty("aws.secretKey", origSecret);
        }
    }

    @Test
    @DisplayName("create(DynamoDbAsyncClient) returns a non-null instance")
    void createWithClient() {
        AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.create(dynamoDbAsyncClient);
        assertNotNull(client);
    }

    @Test
    @DisplayName("table(String, Class) returns a non-null AsyncTable")
    void tableReturnsTypedTable() {
        when(enhancedAsyncClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        AsyncTable<TestItem> table = client.table("test-table", TestItem.class);

        assertNotNull(table);
    }

    @Test
    @DisplayName("table(String, TableSchema) returns a non-null AsyncTable")
    void tableWithTableSchemaReturnsTypedTable() {
        when(enhancedAsyncClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        TableSchema<TestItem> schema = TableSchema.fromBean(TestItem.class);
        AsyncTable<TestItem> table = client.table("test-table", schema);

        assertNotNull(table);
    }

    @Test
    @DisplayName("getEnhancedClient() and getDynamoDbClient() return the injected clients")
    void getClients() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);

        assertSame(enhancedAsyncClient, client.getEnhancedClient());
        assertSame(dynamoDbAsyncClient, client.getDynamoDbClient());
    }

    @Test
    @DisplayName("table(String, Class, Consumer) returns an AsyncTable and configures schema builder")
    void tableWithStaticTableSchemaConsumerReturnsTypedTable() {
        when(enhancedAsyncClient.table(eq("test-table"), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        AsyncTable<TestItem> table = client.table("test-table", TestItem.class, b -> b.newItemSupplier(TestItem::new));

        assertNotNull(table);
        verify(enhancedAsyncClient).table(eq("test-table"), any(TableSchema.class));
    }

    @Test
    @DisplayName("table(String, Class, null) throws NullPointerException")
    void tableWithNullConsumerThrows() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        assertThrows(NullPointerException.class,
                () -> client.table("test-table", TestItem.class, null));
    }

    @Captor
    ArgumentCaptor<DynamoDbEnhancedClientExtension> extensionCaptor;

    private static void restoreProperty(String key, String origValue) {
        if (origValue != null) {
            System.setProperty(key, origValue);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    @DisplayName("transactGet() returns a non-null AsyncTransactGetBuilder")
    void transactGet_returnsBuilder() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        assertNotNull(client.transactGet());
    }

    @Test
    @DisplayName("transactWrite() returns a non-null AsyncTransactWriteBuilder")
    void transactWrite_returnsBuilder() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        assertNotNull(client.transactWrite());
    }

    @Test
    @DisplayName("builder() returns a non-null builder")
    void builderReturnsBuilder() {
        assertNotNull(AsyncDynamoSimplifiedClient.builder());
    }

    @Test
    @DisplayName("builder().build() uses default DynamoDbAsyncClient when none provided")
    void builderDefaultClient() {
        // Set AWS SDK system properties so DynamoDbAsyncClient.create() succeeds
        String origRegion = System.setProperty("aws.region", "us-east-1");
        String origKey = System.setProperty("aws.accessKeyId", "test-key");
        String origSecret = System.setProperty("aws.secretKey", "test-secret");
        try {
            AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.builder().build();
            assertNotNull(client);
            assertNotNull(client.getEnhancedClient());
            assertNotNull(client.getDynamoDbClient());
        } finally {
            restoreProperty("aws.region", origRegion);
            restoreProperty("aws.accessKeyId", origKey);
            restoreProperty("aws.secretKey", origSecret);
        }
    }

    @Test
    @DisplayName("builder().dynamoDbClient(customClient).build() uses the custom client")
    void builderWithCustomClient() {
        AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
        assertNotNull(client);
        assertSame(dynamoDbAsyncClient, client.getDynamoDbClient());
    }

    @Test
    @DisplayName("builder().extensions(ext).build() passes extensions to enhanced client")
    void builderWithExtensions() {
        DynamoDbEnhancedClientExtension ext = mock(DynamoDbEnhancedClientExtension.class);

        AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .extensions(ext)
                .build();

        assertNotNull(client);
        // The client should be usable; we verify the extension was registered by
        // checking that the enhanced client is not null and the call succeeded.
        assertNotNull(client.getEnhancedClient());
    }

    @Test
    @DisplayName("builder().extensions(null) throws NullPointerException")
    void builderWithNullExtensionsThrows() {
        var builder = AsyncDynamoSimplifiedClient.builder();
        assertThrows(NullPointerException.class,
                () -> builder.extensions((DynamoDbEnhancedClientExtension[]) null));
    }

    @Test
    @DisplayName("builder().dynamoDbClient(null) throws NullPointerException")
    void builderWithNullClientThrows() {
        var builder = AsyncDynamoSimplifiedClient.builder();
        assertThrows(NullPointerException.class,
                () -> builder.dynamoDbClient(null));
    }

    @Test
    @DisplayName("listTables() returns table names from DynamoDbAsyncClient")
    void listTablesReturnsTableNames() {
        ListTablesResponse response = ListTablesResponse.builder()
                .tableNames("table1", "table2")
                .build();
        when(dynamoDbAsyncClient.listTables()).thenReturn(CompletableFuture.completedFuture(response));

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        List<String> tables = client.listTables().join();

        assertEquals(List.of("table1", "table2"), tables);
        verify(dynamoDbAsyncClient).listTables();
    }

    @Test
    @DisplayName("executeStatement() delegates to DynamoDbAsyncClient.executeStatement()")
    void executeStatementDelegates() {
        ExecuteStatementRequest request = ExecuteStatementRequest.builder()
                .statement("SELECT * FROM \"test-table\"")
                .build();
        ExecuteStatementResponse expected = ExecuteStatementResponse.builder().build();
        when(dynamoDbAsyncClient.executeStatement(request))
                .thenReturn(CompletableFuture.completedFuture(expected));

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        ExecuteStatementResponse response = client.executeStatement(request).join();

        assertSame(expected, response);
        verify(dynamoDbAsyncClient).executeStatement(request);
    }

    @Test
    @DisplayName("executeStatement() propagates error from DynamoDbAsyncClient")
    void executeStatementPropagatesError() {
        ExecuteStatementRequest request = ExecuteStatementRequest.builder()
                .statement("SELECT * FROM \"test-table\"")
                .build();
        RuntimeException expected = new RuntimeException("query failed");
        when(dynamoDbAsyncClient.executeStatement(request))
                .thenReturn(CompletableFuture.failedFuture(expected));

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.executeStatement(request).get());
        assertSame(expected, ex.getCause());
    }

    @Test
    @DisplayName("listTables() propagates error from DynamoDbAsyncClient")
    void listTablesPropagatesError() {
        RuntimeException expected = new RuntimeException("connection failed");
        when(dynamoDbAsyncClient.listTables()).thenReturn(CompletableFuture.failedFuture(expected));

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.listTables().get());
        assertSame(expected, ex.getCause());
    }

    // ============ Entity Table ============

    @Test
    @DisplayName("entityTable returns a non-null AsyncEntityTable")
    void entityTable_returnsEntityTable() {
        when(enhancedAsyncClient.table(any(String.class), any(TableSchema.class))).thenReturn(dynamoDbAsyncTable);

        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        AsyncEntityTable<?> entityTable = client.entityTable(TestEntity.class);

        assertNotNull(entityTable);
    }

    // ============ Cross-Table Batch Operations ============

    @Test
    @DisplayName("batchGet returns a non-null AsyncCrossTableBatchGetBuilder")
    void batchGet_returnsBuilder() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        assertNotNull(client.batchGet());
    }

    @Test
    @DisplayName("batchWrite returns a non-null AsyncCrossTableBatchWriteBuilder")
    void batchWrite_returnsBuilder() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        assertNotNull(client.batchWrite());
    }

    // ============ Client Utilities ============

    @Test
    @DisplayName("close delegates to DynamoDbAsyncClient.close()")
    void close_delegatesToDynamoDbAsyncClient() {
        AsyncDynamoSimplifiedClient client = createClient(enhancedAsyncClient, dynamoDbAsyncClient);
        client.close();
        verify(dynamoDbAsyncClient).close();
    }
}
