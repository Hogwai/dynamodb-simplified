package dev.hogwai.dynamodb.simplified.async;

import dev.hogwai.dynamodb.simplified.bean.TestPost;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbAsyncWaiter;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("integration")
class AsyncDynamoSimplifiedClientIT {

    static final String TABLE_NAME = "async_test_posts";

    @Container
    @SuppressWarnings({"resource", "PMD.FieldNamingConventions"})
    static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000)
            .withStartupTimeout(Duration.ofSeconds(30));

    private static AsyncDynamoSimplifiedClient client;
    private static AsyncTable<TestPost> table;
    private static DynamoDbAsyncClient asyncClient;

    @BeforeAll
    static void setup() {
        String endpoint = "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000);

        asyncClient = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        // Use sync client for table creation to ensure GSI (by_status) is properly created
        try (var syncClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build()) {

            var enhancedSyncClient = software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(syncClient)
                    .build();

            var enhancedTable = enhancedSyncClient.table(TABLE_NAME, TableSchema.fromBean(TestPost.class));
            enhancedTable.createTable();

            // Wait for table creation
            try (DynamoDbWaiter waiter = syncClient.waiter()) {
                waiter.waitUntilTableExists(b -> b.tableName(TABLE_NAME));
            }

            client = AsyncDynamoSimplifiedClient.create(asyncClient);
            table = client.table(TABLE_NAME, TestPost.class);
        }
    }

    @AfterAll
    static void tearDown() {
        asyncClient.deleteTable(b -> b.tableName(TABLE_NAME)).join();
    }

    @AfterEach
    void cleanItems() {
        table.scan().executeAll().join().forEach(p -> table.deleteItem(p.getId(), p.getCreatedAt()).join());
    }

    @Test
    void putAndGetItem() {
        var post = new TestPost("ap1", 100L, "active", "AsyncPut", "Content", 42, Set.of("x"));
        table.putItem(post).join();

        // TestPost has a sort key, so getItem requires both partition and sort key
        var found = table.getItem("ap1", 100L).join();
        assertTrue(found.isPresent());
        assertEquals("AsyncPut", found.get().getTitle());
    }

    @Test
    void getItemReturnsEmptyWhenNotFound() {
        // TestPost has a sort key, so getItem requires both partition and sort key
        var found = table.getItem("nonexistent", 0L).join();
        assertFalse(found.isPresent());
    }

    @Test
    void queryByPartitionKey() {
        table.putItem(new TestPost("aq1", 1L, "active", "Q1", null, 0, null)).join();
        table.putItem(new TestPost("aq2", 1L, "active", "Q2", null, 0, null)).join();

        List<TestPost> results = table.query()
                .partitionKey("aq1")
                .executeAll()
                .join();
        assertEquals(1, results.size());
        assertEquals("Q1", results.getFirst().getTitle());
    }

    @Test
    void queryWithSortKeyBetween() {
        table.putItem(new TestPost("ab", 1L, "active", "A", null, 0, null)).join();
        table.putItem(new TestPost("ab", 2L, "active", "B", null, 0, null)).join();
        table.putItem(new TestPost("ab", 3L, "active", "C", null, 0, null)).join();

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyBetween("ab", 1L, 2L)
                .executeAll()
                .join();
        assertEquals(2, results.size());
    }

    @Test
    void scanAllItems() {
        table.putItem(new TestPost("as1", 1L, "active", "S1", null, 0, null)).join();
        table.putItem(new TestPost("as2", 1L, "active", "S2", null, 0, null)).join();

        List<TestPost> results = table.scan().executeAll().join();
        assertEquals(2, results.size());
    }

    @Test
    void scanWithFilter() {
        table.putItem(new TestPost("af1", 1L, "active", "Active", null, 10, null)).join();
        table.putItem(new TestPost("af2", 1L, "inactive", "Inactive", null, 20, null)).join();

        List<TestPost> results = table.scan()
                .filter(f -> f.eq("status", "active"))
                .executeAll()
                .join();
        assertEquals(1, results.size());
    }

    @Test
    void indexQueryByGsi() {
        table.putItem(new TestPost("ai1", 1L, "published", "Pub1", null, 0, null)).join();
        table.putItem(new TestPost("ai2", 1L, "published", "Pub2", null, 0, null)).join();
        table.putItem(new TestPost("ai3", 1L, "draft", "Draft", null, 0, null)).join();

        List<TestPost> results = table.index("by_status")
                .query()
                .partitionKey("published")
                .executeAll()
                .join();
        assertEquals(2, results.size());
    }

    @Test
    void batchGetItems() {
        var p1 = new TestPost("abg1", 1L, "active", "BG1", null, 0, null);
        var p2 = new TestPost("abg2", 1L, "active", "BG2", null, 0, null);
        table.putItem(p1).join();
        table.putItem(p2).join();

        // TestPost has a sort key, so addKey requires both partition and sort key
        var results = table.batchGet()
                .addKey("abg1", 1L)
                .addKey("abg2", 1L)
                .execute()
                .join();
        assertEquals(2, results.items().size());
    }

    @Test
    void batchWrite() {
        var b1 = new TestPost("abw1", 1L, "active", "BW1", null, 0, null);
        table.putItem(b1).join();

        // TestPost has a sort key, so delete requires both partition and sort key
        table.batchWrite()
                .put(new TestPost("abw2", 1L, "active", "BW2", null, 0, null))
                .delete("abw1", 1L)
                .execute()
                .join();

        assertFalse(table.getItem("abw1", 1L).join().isPresent());
        assertTrue(table.getItem("abw2", 1L).join().isPresent());
    }

    @Test
    void transactGet() {
        var p1 = new TestPost("atg1", 1L, "active", "TG1", null, 0, null);
        table.putItem(p1).join();

        // TestPost has a sort key, so addGetItem requires both partition and sort key
        var results = client.transactGet()
                .addGetItem(table, "atg1", 1L)
                .execute()
                .join();

        assertEquals(1, results.size());
        assertNotNull(results.get(0));
    }

    @Test
    void transactWritePut() {
        var p1 = new TestPost("atw1", 1L, "active", "TW1", null, 0, null);

        client.transactWrite()
                .put(table, p1)
                .execute()
                .join();

        // TestPost has a sort key, so getItem requires both partition and sort key
        assertTrue(table.getItem("atw1", 1L).join().isPresent());
    }

    @Test
    void createAndDeleteTable() {
        String tempName = TABLE_NAME + "_tmp_" + System.currentTimeMillis();
        var tempTable = client.table(tempName, TestPost.class);

        tempTable.create().join();
        // Use the async waiter to wait for table creation
        DynamoDbAsyncWaiter.builder().client(asyncClient).build()
                .waitUntilTableExists(b -> b.tableName(tempName))
                .join();
        assertTrue(tempTable.exists().join());

        tempTable.delete().join();
        DynamoDbAsyncWaiter.builder().client(asyncClient).build()
                .waitUntilTableNotExists(b -> b.tableName(tempName))
                .join();
        assertFalse(tempTable.exists().join());
    }

    @Test
    void listTables() {
        var tables = client.listTables().join();
        assertTrue(tables.contains(TABLE_NAME));
    }

    @Test
    void conditionalPutFails() {
        var post = new TestPost("acp1", 1L, "active", "First", null, 0, null);
        table.put(post).onlyIfNotExists("id").execute().join();

        CompletableFuture<Void> dup = table.put(post).onlyIfNotExists("id").execute();
        assertThrows(Exception.class, dup::join);
    }

    @Test
    void deleteItemDirect() {
        table.putItem(new TestPost("adel1", 1L, "active", "Del", null, 0, null)).join();
        // TestPost has a sort key, so getItem/deleteItem requires both partition and sort key
        assertTrue(table.getItem("adel1", 1L).join().isPresent());
        table.deleteItem("adel1", 1L).join();
        assertFalse(table.getItem("adel1", 1L).join().isPresent());
    }

    @Test
    void updateItemDirect() {
        var post = new TestPost("aup1", 1L, "active", "Before", null, 0, null);
        table.putItem(post).join();

        post.setTitle("After");
        var updated = table.updateItem(post).join();
        assertEquals("After", updated.getTitle());

        // TestPost has a sort key, so getItem requires both partition and sort key
        var found = table.getItem("aup1", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("After", found.get().getTitle());
    }

    @Test
    void partialUpdateWithExpression() {
        var post = new TestPost("ape1", 1L, "active", "Original", null, 0, null);
        table.putItem(post).join();

        table.update(post)
                .update(u -> u.set("title", "Updated"))
                .execute()
                .join();

        // TestPost has a sort key, so getItem requires both partition and sort key
        var found = table.getItem("ape1", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().getTitle());
    }

    @Test
    void tableDescribeReturnsInfo() {
        var desc = table.describe().join();
        assertNotNull(desc);
        assertEquals(TABLE_NAME, desc.table().tableName());
    }

    @Test
    void getBuilderViaBuilder() {
        // Test that the builder creates a usable client
        var builtClient = AsyncDynamoSimplifiedClient.builder()
                .dynamoDbClient(asyncClient)
                .build();
        assertNotNull(builtClient);
    }

    // ============ Conditional Operations ============

    @Test
    void conditionalPutWithConditionExpression() {
        var post = new TestPost("acpe1", 1L, "draft", "Original", null, 0, null);
        table.putItem(post).join();

        // Condition on existing item's status should pass
        table.put(post).condition(c -> c.eq("status", "draft")).execute().join();

        var found = table.getItem("acpe1", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("Original", found.get().getTitle());
    }

    @Test
    void conditionalPutWithNonMatchingCondition() {
        var post = new TestPost("acpe2", 1L, "draft", "Original", null, 0, null);
        table.putItem(post).join();

        var putOp = table.put(post).condition(c -> c.eq("status", "published"));
        assertThrows(Exception.class, () -> putOp.execute().join());

        // Verify item unchanged
        var found = table.getItem("acpe2", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("draft", found.get().getStatus());
    }

    @Test
    void conditionalUpdateWithMatchingCondition() {
        var post = new TestPost("acup1", 1L, "active", "MatchMe", null, 0, null);
        table.putItem(post).join();

        post.setTitle("Matched");
        var result = table.update(post)
                .condition(c -> c.eq("status", "active"))
                .execute()
                .join();

        assertTrue(result.isPresent());
        assertEquals("Matched", result.orElseThrow().getTitle());
    }

    @Test
    void conditionalUpdateWithNonMatchingCondition() {
        var post = new TestPost("acup2", 1L, "active", "KeepMe", null, 0, null);
        table.putItem(post).join();

        post.setTitle("ShouldNotApply");
        var updateOp = table.update(post)
                .condition(c -> c.eq("status", "nonexistent"));
        assertThrows(Exception.class, () -> updateOp.execute().join());

        var found = table.getItem("acup2", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("KeepMe", found.get().getTitle());
    }

    @Test
    void conditionalDeleteWithMatchingCondition() {
        var post = new TestPost("acdel1", 1L, "active", "DeleteMe", null, 0, null);
        table.putItem(post).join();

        table.delete("acdel1", 1L)
                .condition(c -> c.eq("status", "active"))
                .execute()
                .join();

        assertFalse(table.getItem("acdel1", 1L).join().isPresent());
    }

    @Test
    void conditionalDeleteWithNonMatchingCondition() {
        var post = new TestPost("acdel2", 1L, "active", "KeepMeAlive", null, 0, null);
        table.putItem(post).join();

        var deleteOp = table.delete("acdel2", 1L)
                .condition(c -> c.eq("status", "nonexistent"));
        assertThrows(Exception.class, () -> deleteOp.execute().join());

        assertTrue(table.getItem("acdel2", 1L).join().isPresent());
    }

    // ============ Query Variants ============

    @Test
    void queryDescending() {
        table.putItem(new TestPost("adesc", 10L, "active", "Low", null, 0, null)).join();
        table.putItem(new TestPost("adesc", 20L, "active", "Mid", null, 0, null)).join();
        table.putItem(new TestPost("adesc", 30L, "active", "High", null, 0, null)).join();

        List<TestPost> results = table.query()
                .partitionKey("adesc")
                .descending()
                .executeAll()
                .join();

        assertEquals(3, results.size());
        assertEquals("High", results.get(0).getTitle());
        assertEquals("Low", results.get(2).getTitle());
    }

    @Test
    void queryWithPagination() {
        for (int i = 0; i < 5; i++) {
            table.putItem(new TestPost("apag", (long) i, "active", "Item" + i, null, i, null)).join();
        }

        PagedResult<TestPost> page = table.query()
                .partitionKey("apag")
                .limit(3)
                .executeWithPagination()
                .join();

        assertEquals(3, page.items().size());
        assertTrue(page.hasMorePages());
    }

    @Test
    void queryCount() {
        table.putItem(new TestPost("acnt", 1L, "active", "A", null, 0, null)).join();
        table.putItem(new TestPost("acnt", 2L, "active", "B", null, 0, null)).join();

        long count = table.query().partitionKey("acnt").count().join();
        assertEquals(2, count);
    }

    @Test
    void queryExecuteAndGetFirst() {
        table.putItem(new TestPost("aqegf", 1L, "active", "First", null, 0, null)).join();

        Optional<TestPost> result = table.query()
                .partitionKey("aqegf")
                .executeAndGetFirst()
                .join();

        assertTrue(result.isPresent());
        assertEquals("First", result.get().getTitle());
    }

    @Test
    void queryExecuteAndGetFirstOnEmptyPartition() {
        Optional<TestPost> result = table.query()
                .partitionKey("nonexistent-aqegf")
                .executeAndGetFirst()
                .join();

        assertFalse(result.isPresent());
    }

    @Test
    void queryWithFilter() {
        table.putItem(new TestPost("aqf", 1L, "active", "Visible", null, 10, null)).join();
        table.putItem(new TestPost("aqf", 2L, "active", "Hidden", null, 0, null)).join();

        List<TestPost> results = table.query()
                .partitionKey("aqf")
                .filter(f -> f.gt("views", 0))
                .executeAll()
                .join();

        assertEquals(1, results.size());
        assertEquals("Visible", results.getFirst().getTitle());
    }

    // ============ Scan Variant ============

    @Test
    void scanWithProjection() {
        table.putItem(new TestPost("asp1", 1L, "active", "Projected", null, 99, null)).join();

        List<TestPost> results = table.scan()
                .project("id", "title")
                .executeAll()
                .join();

        assertFalse(results.isEmpty());
        var item = results.stream().filter(p -> "asp1".equals(p.getId())).findFirst();
        assertTrue(item.isPresent());
        assertEquals("Projected", item.get().getTitle());
        // views should be null (not projected)
        assertNull(item.get().getViews());
    }

    // ============ Delete with ReturnValues (low-level path) ============

    @Test
    void deleteItemWithReturnValues() {
        var post = new TestPost("arv1", 1L, "active", "ReturnValueTest", null, 0, null);
        table.putItem(post).join();

        var deleted = table.delete("arv1", 1L)
                .returnValues(ReturnValue.ALL_OLD)
                .execute()
                .join();

        assertTrue(deleted.isPresent());
        assertEquals("ReturnValueTest", deleted.orElseThrow().getTitle());
        assertFalse(table.getItem("arv1", 1L).join().isPresent());
    }

    @Test
    void deleteItemWithReturnValuesOnNonExistentKey() {
        var result = table.delete("nonexistent-arv", 0L)
                .returnValues(ReturnValue.ALL_OLD)
                .execute()
                .join();

        assertTrue(result.isEmpty());
    }

    // ============ TransactWrite with UpdateExpression (low-level path) ============

    @Test
    void transactWriteWithUpdateExpression() {
        var post = new TestPost("atwexp", 1L, "active", "Before", "Old", 0, null);
        table.putItem(post).join();

        client.transactWrite()
                .update(table, post, u -> u.set("title", "After").set("content", "New"))
                .execute()
                .join();

        var found = table.getItem("atwexp", 1L).join();
        assertTrue(found.isPresent());
        assertEquals("After", found.get().getTitle());
        assertEquals("New", found.get().getContent());
    }

    @Test
    void transactWriteWithUpdateExpressionAndConditionCheck() {
        // Update and condition check must target different items (DynamoDB
        // transaction constraint: each action must operate on a distinct item).
        var updateTarget = new TestPost("atwexp2", 1L, "active", "CondBefore", null, 5, null);
        var checkTarget = new TestPost("atwexp2ck", 1L, "active", "CheckItem", null, 0, null);
        table.putItem(updateTarget).join();
        table.putItem(checkTarget).join();

        client.transactWrite()
                .update(table, updateTarget, u -> u.set("views", 10))
                .conditionCheck(table, "atwexp2ck", 1L, c -> c.eq("views", 0))
                .execute()
                .join();

        var found = table.getItem("atwexp2", 1L).join();
        assertTrue(found.isPresent());
        assertEquals(10, found.get().getViews());

        // Verify condition check target was not modified
        var checkFound = table.getItem("atwexp2ck", 1L).join();
        assertTrue(checkFound.isPresent());
        assertEquals("CheckItem", checkFound.get().getTitle());
    }
}
