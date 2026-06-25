package com.hogwai.dynamodb.simplified;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("integration")
class DynamoSimplifiedClientRichIT {

    static final String TABLE_NAME = "test_posts";

    @Container
    @SuppressWarnings({"resource", "PMD.FieldNamingConventions"})
    static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000)
            .withStartupTimeout(Duration.ofSeconds(30));

    private static DynamoSimplifiedClient client;
    private static Table<TestPost> table;
    private static DynamoDbClient lowLevelClient;

    @BeforeAll
    static void setup() {
        String endpoint = "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000);

        lowLevelClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        var enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(lowLevelClient)
                .build();

        var enhancedTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(TestPost.class));
        enhancedTable.createTable();

        try (DynamoDbWaiter waiter = lowLevelClient.waiter()) {
            waiter.waitUntilTableExists(b -> b.tableName(TABLE_NAME));
        }

        client = DynamoSimplifiedClient.create(lowLevelClient);
        table = client.table(TABLE_NAME, TestPost.class);
    }

    @AfterAll
    static void tearDown() {
        lowLevelClient.deleteTable(b -> b.tableName(TABLE_NAME));
    }

    @AfterEach
    void cleanItems() {
        // TestPost has a sort key (createdAt), so delete by both partition and sort key
        table.scan().executeAll().forEach(this::deleteTestPost);
    }

    private void deleteTestPost(TestPost p) {
        table.deleteItem(p.getId(), p.getCreatedAt());
    }

    // ============ Sort key equality query ============

    @Test
    void queryBySortKeyEquals() {
        var post = new TestPost("sk1", 1000L, "active", "Title", "Content", 10, Set.of("a"));
        table.putItem(post);

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyEquals("sk1", 1000L)
                .executeAll();
        assertEquals(1, results.size());
        assertEquals("Title", results.getFirst().getTitle());
    }

    // ============ Sort key less-than query ============

    @Test
    void queryBySortKeyLt() {
        table.putItem(new TestPost("sk2", 100L, "active", "Old", "C1", 1, null));
        table.putItem(new TestPost("sk2", 200L, "active", "Mid", "C2", 2, null));
        table.putItem(new TestPost("sk2", 300L, "active", "New", "C3", 3, null));

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyLessThan("sk2", 250L)
                .executeAll();
        assertEquals(2, results.size());
    }

    // ============ Sort key greater-than-or-equal query ============

    @Test
    void queryBySortKeyGe() {
        table.putItem(new TestPost("sk3", 100L, "active", "A", "C1", 1, null));
        table.putItem(new TestPost("sk3", 200L, "active", "B", "C2", 2, null));

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyGreaterThanOrEqual("sk3", 200L)
                .executeAll();
        assertEquals(1, results.size());
        assertEquals("B", results.getFirst().getTitle());
    }

    // ============ Sort key between query ============

    @Test
    void queryBySortKeyBetween() {
        table.putItem(new TestPost("sk4", 1L, "active", "First", null, 0, null));
        table.putItem(new TestPost("sk4", 2L, "active", "Second", null, 0, null));
        table.putItem(new TestPost("sk4", 3L, "active", "Third", null, 0, null));
        table.putItem(new TestPost("sk4", 10L, "active", "Tenth", null, 0, null));

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyBetween("sk4", 2L, 3L)
                .executeAll();
        assertEquals(2, results.size());
    }

    // ============ Query with filter ============

    @Test
    void queryWithFilter() {
        table.putItem(new TestPost("qf1", 1L, "active", "Visible", "C1", 5, null));
        table.putItem(new TestPost("qf1", 2L, "active", "Hidden", "C2", 0, null));
        table.putItem(new TestPost("qf1", 3L, "draft", "Draft", "C3", 0, null));

        List<TestPost> results = table.query()
                .partitionKey("qf1")
                .filter(f -> f.gt("views", 0))
                .executeAll();
        assertEquals(1, results.size());
        assertEquals("Visible", results.getFirst().getTitle());
    }

    // ============ Query with descending order ============

    @Test
    void queryDescending() {
        table.putItem(new TestPost("desc", 10L, "active", "Low", null, 0, null));
        table.putItem(new TestPost("desc", 20L, "active", "Mid", null, 0, null));
        table.putItem(new TestPost("desc", 30L, "active", "High", null, 0, null));

        List<TestPost> results = table.query()
                .partitionKeyAndSortKeyGreaterThanOrEqual("desc", 10L)
                .descending()
                .executeAll();
        assertEquals(3, results.size());
        assertEquals("High", results.get(0).getTitle());
        assertEquals("Low", results.get(2).getTitle());
    }

    // ============ Query with limit ============

    @Test
    void queryWithLimit() {
        for (int i = 0; i < 5; i++) {
            table.putItem(new TestPost("lim", (long) i, "active", "Item" + i, null, i, null));
        }

        // limit applies per-page; executeWithPagination returns only the first page
        PagedResult<TestPost> page = table.query()
                .partitionKey("lim")
                .limit(3)
                .executeWithPagination();
        assertEquals(3, page.getItems().size());
        assertTrue(page.hasMorePages());
    }

    // ============ Query count ============

    @Test
    void queryCount() {
        table.putItem(new TestPost("cnt", 1L, "active", "A", null, 0, null));
        table.putItem(new TestPost("cnt", 2L, "active", "B", null, 0, null));

        long count = table.query().partitionKey("cnt").count();
        assertEquals(2, count);
    }

    // ============ Index query by GSI ============

    @Test
    void indexQueryByStatus() {
        table.putItem(new TestPost("i1", 1L, "published", "Pub1", "C1", 10, null));
        table.putItem(new TestPost("i2", 1L, "published", "Pub2", "C2", 20, null));
        table.putItem(new TestPost("i3", 1L, "draft", "Draft", "C3", 5, null));

        List<TestPost> results = table.index("by_status")
                .query()
                .partitionKey("published")
                .executeAll();
        assertEquals(2, results.size());
    }

    @Test
    void indexQueryWithSortKey() {
        table.putItem(new TestPost("idx1", 100L, "archived", "Old", null, 0, null));
        table.putItem(new TestPost("idx2", 200L, "archived", "Newer", null, 0, null));

        List<TestPost> results = table.index("by_status")
                .query()
                .partitionKey("archived")
                .executeAll();
        assertEquals(2, results.size());
    }

    // ============ Scan with filter ============

    @Test
    void scanWithFilterOnRichTable() {
        table.putItem(new TestPost("sf1", 1L, "active", "A", null, 10, null));
        table.putItem(new TestPost("sf2", 1L, "inactive", "B", null, 20, null));

        List<TestPost> results = table.scan()
                .filter(f -> f.eq("status", "active"))
                .executeAll();
        assertEquals(1, results.size());
    }

    // ============ Conditional put with ConditionExpression ============

    @Test
    void conditionalPutWithConditionExpression() {
        // First insert the item directly, then conditionally replace it
        var post = new TestPost("cp1", 1L, "draft", "Original", null, 0, null);
        table.putItem(post);

        // Condition on the existing item's status attribute should pass
        table.put(post).condition(c -> c.eq("status", "draft")).execute();

        // TestPost has a sort key, so getItem requires both partition and sort key
        assertTrue(table.getItem("cp1", 1L).isPresent());
    }

    // ============ Batch get with consistent read ============

    @Test
    void batchGetWithConsistentRead() {
        var p1 = new TestPost("bg1", 1L, "active", "BG1", null, 0, null);
        var p2 = new TestPost("bg2", 1L, "active", "BG2", null, 0, null);
        table.putItem(p1);
        table.putItem(p2);

        // TestPost has a sort key, so addKey requires both partition and sort key
        List<TestPost> results = table.batchGet()
                .addKey("bg1", 1L)
                .addKey("bg2", 1L)
                .consistentRead(true)
                .execute();
        assertEquals(2, results.size());
    }

    // ============ TransactWrite with update ============

    @Test
    void transactWriteUpdate() {
        var post = new TestPost("twu1", 1L, "active", "Before", null, 0, null);
        table.putItem(post);

        post.setTitle("After");
        client.transactWrite()
                .update(table, post)
                .execute();

        // TestPost has a sort key, so getItem requires both partition and sort key
        var found = table.getItem("twu1", 1L);
        assertTrue(found.isPresent());
        assertEquals("After", found.get().getTitle());
    }

    // ============ TransactWrite with delete ============

    @Test
    void transactWriteDelete() {
        var post = new TestPost("twd1", 1L, "active", "ToDelete", null, 0, null);
        table.putItem(post);

        // TestPost has a sort key, so delete requires both partition and sort key
        client.transactWrite()
                .delete(table, "twd1", 1L)
                .execute();

        assertFalse(table.getItem("twd1", 1L).isPresent());
    }

    // ============ TransactGet with sort key ============

    @Test
    void transactGetWithSortKey() {
        var p1 = new TestPost("tgsk1", 1L, "active", "TGSK1", null, 0, null);
        table.putItem(p1);

        var results = client.transactGet()
                .addGetItem(table, "tgsk1", 1L)
                .execute();

        assertEquals(1, results.size());
        assertNotNull(results.get(0));
    }

    // ============ exists() / not found ============

    @Test
    void tableExists() {
        assertTrue(table.exists());
    }

    @Test
    void itemNotFoundReturnsEmpty() {
        // TestPost has a sort key, so getItem requires both partition and sort key
        assertFalse(table.getItem("nonexistent", 0L).isPresent());
    }

    // ============ executeWithPagination ============

    @Test
    void queryWithPagination() {
        for (int i = 0; i < 5; i++) {
            table.putItem(new TestPost("pagq", (long) i, "active", "Item" + i, null, i, null));
        }

        PagedResult<TestPost> page = table.query()
                .partitionKey("pagq")
                .limit(3)
                .executeWithPagination();
        assertEquals(3, page.getItems().size());
        assertTrue(page.hasMorePages());
    }

    // ============ deleteItem (direct convenience) ============

    @Test
    void deleteItemDirect() {
        table.putItem(new TestPost("del1", 1L, "active", "DeleteMe", null, 0, null));
        // TestPost has a sort key, so getItem/deleteItem requires both partition and sort key
        assertTrue(table.getItem("del1", 1L).isPresent());
        table.deleteItem("del1", 1L);
        assertFalse(table.getItem("del1", 1L).isPresent());
    }

    // ============ onlyIfNotExists ============

    @Test
    void putOnlyIfNotExists() {
        var post = new TestPost("oine1", 1L, "active", "First", null, 0, null);
        table.put(post).onlyIfNotExists("id").execute();
        // TestPost has a sort key, so getItem requires both partition and sort key
        assertTrue(table.getItem("oine1", 1L).isPresent());

        // Second attempt with the same full key should fail (attribute_not_exists("id") fails)
        var dup = new TestPost("oine1", 1L, "active", "Second", null, 0, null);
        var putOp = table.put(dup).onlyIfNotExists("id");
        assertThrows(ConditionFailedException.class, putOp::execute);
    }

    // ============ Query Execution Variants ============

    @Test
    void queryExecuteStream() {
        for (int i = 0; i < 3; i++) {
            table.putItem(new TestPost("qes", (long) i, "active", "Item" + i, null, i, null));
        }

        List<TestPost> collected;
        try (var stream = table.query()
                .partitionKey("qes")
                .executeStream()) {
            collected = stream.toList();
        }
        assertEquals(3, collected.size());
    }

    @Test
    void queryExecuteAndGetFirst() {
        table.putItem(new TestPost("qegf", 1L, "active", "FirstOne", null, 0, null));

        var result = table.query()
                .partitionKey("qegf")
                .executeAndGetFirst();
        assertTrue(result.isPresent());
        assertEquals("FirstOne", result.get().getTitle());
    }

    @Test
    void queryExecuteAndGetFirstOnEmptyPartition() {
        var result = table.query()
                .partitionKey("nonexistent-qegf")
                .executeAndGetFirst();
        assertFalse(result.isPresent());
    }

    @Test
    void batchGetWithProjection() {
        var p1 = new TestPost("bgpj1", 1L, "active", "ProjectedTitle1", "ContentHidden", 10, null);
        var p2 = new TestPost("bgpj2", 1L, "active", "ProjectedTitle2", "AlsoHidden", 20, null);
        table.putItem(p1);
        table.putItem(p2);

        List<TestPost> results = table.batchGet()
                .addKey("bgpj1", 1L)
                .addKey("bgpj2", 1L)
                .project("id", "createdAt", "title")
                .execute();

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> "ProjectedTitle1".equals(p.getTitle())));
        assertTrue(results.stream().anyMatch(p -> "ProjectedTitle2".equals(p.getTitle())));
        // Non-projected attributes should be null; projected ones should be set
        assertTrue(results.stream().allMatch(p -> p.getContent() == null));
        assertTrue(results.stream().allMatch(p -> p.getViews() == null)); // Integer, not in projection
        assertTrue(results.stream().allMatch(p -> p.getId() != null));
        assertTrue(results.stream().allMatch(p -> p.getCreatedAt() != null));
        assertTrue(results.stream().allMatch(p -> p.getTitle() != null));
    }

    @Test
    void transactWriteExpressionUpdateWithSortKey() {
        var post = new TestPost("twexp", 1L, "active", "BeforeExp", "OldContent", 5, null);
        table.putItem(post);

        // Expression-based update inside transact write (low-level fallback path)
        client.transactWrite()
                .update(table, post, u -> u.set("title", "AfterExp").set("content", "NewContent"))
                .execute();

        var found = table.getItem("twexp", 1L);
        assertTrue(found.isPresent());
        assertEquals("AfterExp", found.get().getTitle());
        assertEquals("NewContent", found.get().getContent());
    }
}
