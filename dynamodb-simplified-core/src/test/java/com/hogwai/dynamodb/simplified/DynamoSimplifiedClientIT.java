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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("integration")
class DynamoSimplifiedClientIT {

    static final String TABLE_NAME = "products";

    @Container
    @SuppressWarnings({"resource", "PMD.FieldNamingConventions"})
    static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000)
            .withStartupTimeout(Duration.ofSeconds(30));

    private static DynamoSimplifiedClient client;
    private static Table<Product> table;
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

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(lowLevelClient)
                .build();

        // Create the table via enhanced client
        var enhancedTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Product.class));
        enhancedTable.createTable();

        // Wait for table to become active
        try (DynamoDbWaiter waiter = lowLevelClient.waiter()) {
            waiter.waitUntilTableExists(b -> b.tableName(TABLE_NAME));
        }

        client = DynamoSimplifiedClient.create(lowLevelClient);
        table = client.table(TABLE_NAME, Product.class);
    }

    @AfterAll
    static void tearDown() {
        lowLevelClient.deleteTable(b -> b.tableName(TABLE_NAME));
    }

    @AfterEach
    void cleanItems() {
        // Remove all items between tests for isolation
        table.scan().execute().forEach(p -> table.deleteItem(p.getId()));
    }

    // ============ Factory & Table creation ============

    @Test
    void createClientAndTable() {
        assertNotNull(client);
        assertNotNull(table);
        var listed = lowLevelClient.listTables().tableNames();
        assertTrue(listed.contains(TABLE_NAME), "Table " + TABLE_NAME + " should exist");
    }

    // ============ Put & Get ============

    @Test
    void putAndGetItem() {
        var product = new Product("p1", "Widget", 9.99, true, Set.of("gadget"), 1000L);
        table.putItem(product);

        Optional<Product> found = table.getItem("p1");
        assertTrue(found.isPresent());
        assertEquals("Widget", found.get().getName());
        assertEquals(9.99, found.get().getPrice(), 0.001);
        assertTrue(found.get().getInStock());
        assertEquals(Set.of("gadget"), found.get().getTags());
    }

    @Test
    void getItemReturnsEmptyWhenNotFound() {
        Optional<Product> found = table.getItem("nonexistent");
        assertFalse(found.isPresent());
    }

    // ============ Builder-based Put ============

    @Test
    void putWithBuilder() {
        var product = new Product("p2", "Gadget", 5.99, true, Set.of("tech"), 2000L);
        table.put(product).execute();

        Optional<Product> found = table.getItem("p2");
        assertTrue(found.isPresent());
        assertEquals("Gadget", found.get().getName());
    }

    // ============ Update (full replacement) ============

    @Test
    void updateItemFullReplacement() {
        var product = new Product("p3", "Old Name", 1.00, false, Set.of("old"), 3000L);
        table.putItem(product);

        var updated = new Product("p3", "New Name", 2.00, true, Set.of("new"), 4000L);
        table.updateItem(updated);

        Optional<Product> found = table.getItem("p3");
        assertTrue(found.isPresent());
        assertEquals("New Name", found.get().getName());
        assertEquals(2.00, found.get().getPrice(), 0.001);
        assertTrue(found.get().getInStock());
    }

    // ============ Partial update with UpdateExpression ============

    @Test
    void partialUpdateWithExpression() {
        var product = new Product("p4", "Original", 10.0, true, Set.of("a"), 5000L);
        table.putItem(product);

        Product result = table.update(product)
                .update(u -> u.set("price", 15.99).set("name", "Updated"))
                .execute();

        // The low-level client returns ALL_NEW
        assertEquals(15.99, result.getPrice(), 0.001);
        assertEquals("Updated", result.getName());

        // Verify via get
        Optional<Product> found = table.getItem("p4");
        assertTrue(found.isPresent());
        assertEquals(15.99, found.get().getPrice(), 0.001);
    }

    @Test
    void partialUpdateIncrement() {
        var product = new Product("p5", "Counter", 100.0, true, null, 6000L);
        table.putItem(product);

        table.update(product).update(u -> u.increment("price", 50)).execute();

        Optional<Product> found = table.getItem("p5");
        assertTrue(found.isPresent());
        assertEquals(150.0, found.get().getPrice(), 0.001);
    }

    // ============ Conditional put ============

    @Test
    void conditionalPutOnlyIfNotExists() {
        var product = new Product("p6", "First", 1.0, true, null, 7000L);
        table.put(product).onlyIfNotExists("id").execute();

        // Verify it was created
        assertTrue(table.getItem("p6").isPresent());

        // Try again — should throw ConditionalCheckFailedException when item already exists
        var product2 = new Product("p6", "Second", 2.0, true, null, 8000L);
        assertThrows(ConditionFailedException.class,
                () -> table.put(product2).onlyIfNotExists("id").execute());

        // Original item should still be there (condition prevented overwrite)
        Optional<Product> found = table.getItem("p6");
        assertTrue(found.isPresent());
        assertEquals("First", found.get().getName());
    }

    @Test
    void conditionalPutWithCondition() {
        // Condition on a non-existent attribute should fail
        table.putItem(new Product("p7", "Original", 1.0, true, null, 9000L));

        var replacement = new Product("p7", "Replaced", 2.0, true, null, 10000L);
        table.put(replacement).condition(c -> c.eq("name", "Original")).execute();

        // Condition matched, so replacement should succeed
        Optional<Product> found = table.getItem("p7");
        assertTrue(found.isPresent());
        assertEquals("Replaced", found.get().getName());
    }

    // ============ Conditional update ============

    @Test
    void conditionalUpdateWithMatchingCondition() {
        table.putItem(new Product("p8", "MatchMe", 1.0, true, null, 11000L));

        Product result = table.update(new Product("p8", "Matched", 2.0, true, null, 12000L))
                .condition(c -> c.eq("name", "MatchMe"))
                .execute();

        assertEquals("Matched", result.getName());
    }

    @Test
    void conditionalUpdateWithNonMatchingCondition() {
        table.putItem(new Product("p9", "KeepMe", 1.0, true, null, 13000L));

        assertThrows(ConditionFailedException.class, () ->
                table.update(new Product("p9", "ShouldNotApply", 2.0, true, null, 14000L))
                        .condition(c -> c.eq("name", "NonExistent"))
                        .execute()
        );

        // Verify item was NOT changed
        Optional<Product> found = table.getItem("p9");
        assertTrue(found.isPresent());
        assertEquals("KeepMe", found.get().getName());
    }

    // ============ Conditional delete ============

    @Test
    void conditionalDeleteWithMatchingCondition() {
        table.putItem(new Product("p10", "DeleteMe", 1.0, true, null, 15000L));

        table.delete("p10").condition(c -> c.eq("name", "DeleteMe")).execute();

        assertFalse(table.getItem("p10").isPresent());
    }

    @Test
    void conditionalDeleteWithNonMatchingCondition() {
        table.putItem(new Product("p11", "KeepMeAlive", 1.0, true, null, 16000L));

        assertThrows(ConditionFailedException.class, () ->
                table.delete("p11").condition(c -> c.eq("name", "NonExistent")).execute()
        );

        assertTrue(table.getItem("p11").isPresent());
    }

    // ============ Query ============

    @Test
    void queryByPartitionKey() {
        table.putItem(new Product("q1", "Query1", 1.0, true, null, 20000L));
        table.putItem(new Product("q2", "Query2", 2.0, true, null, 21000L));

        List<Product> results = table.query().partitionKey("q1").execute();
        assertEquals(1, results.size());
        assertEquals("Query1", results.getFirst().getName());
    }

    @Test
    void queryReturnsEmptyForNonExistentKey() {
        List<Product> results = table.query().partitionKey("nonexistent").execute();
        assertTrue(results.isEmpty());
    }

    // ============ Scan ============

    @Test
    void scanAllItems() {
        table.putItem(new Product("s1", "Scan1", 1.0, true, null, 30000L));
        table.putItem(new Product("s2", "Scan2", 2.0, false, null, 31000L));

        List<Product> results = table.scan().execute();
        assertEquals(2, results.size());
    }

    @Test
    void scanWithFilter() {
        table.putItem(new Product("sf1", "FilterA", 10.0, true, Set.of("x"), 32000L));
        table.putItem(new Product("sf2", "FilterB", 20.0, false, Set.of("y"), 33000L));

        List<Product> results = table.scan()
                .filter(f -> f.eq("inStock", false))
                .execute();

        assertEquals(1, results.size());
        assertEquals("FilterB", results.getFirst().getName());
    }

    @Test
    void scanWithProjection() {
        table.putItem(new Product("sp1", "Projected", 99.0, true, Set.of("z"), 34000L));

        List<Product> results = table.scan()
                .project("id", "name")
                .execute();

        assertEquals(1, results.size());
        assertEquals("Projected", results.getFirst().getName());
        // price should be null (not projected)
        assertNull(results.getFirst().getPrice());
    }

    // ============ Pagination ============

    @Test
    void scanWithPagination() {
        // Insert enough items to force pagination with a small limit
        for (int i = 0; i < 5; i++) {
            table.putItem(new Product("pag" + i, "PageItem" + i, (double) i, true, null, 40000L + i));
        }

        PagedResult<Product> firstPage = table.scan().limit(2).executeWithPagination();
        assertEquals(2, firstPage.getItems().size());
        assertTrue(firstPage.hasMorePages());

        // Fetch second page
        PagedResult<Product> secondPage = table.scan()
                .limit(2)
                .startFrom(firstPage.getLastEvaluatedKey())
                .executeWithPagination();

        assertEquals(2, secondPage.getItems().size());
    }

    // ============ Count ============

    @Test
    void scanCount() {
        table.putItem(new Product("c1", "Count1", 1.0, true, null, 50000L));
        table.putItem(new Product("c2", "Count2", 2.0, true, null, 51000L));

        long count = table.scan().count();
        assertEquals(2, count);
    }

    @Test
    void queryCount() {
        table.putItem(new Product("cq1", "CQ1", 1.0, true, null, 52000L));

        long count = table.query().partitionKey("cq1").count();
        assertEquals(1, count);
    }

    // ============ Batch operations ============

    @Test
    void batchWritePutAndDelete() {
        var b1 = new Product("bw1", "Batch1", 1.0, true, null, 60000L);
        var b2 = new Product("bw2", "Batch2", 2.0, true, null, 61000L);
        table.putItem(b1);

        table.batchWrite()
                .put(b2)
                .delete("bw1")
                .execute();

        // Give DynamoDB Local a moment
        assertFalse(table.getItem("bw1").isPresent());
        assertTrue(table.getItem("bw2").isPresent());
    }

    @Test
    void batchGetItems() {
        var bg1 = new Product("bg1", "BatchGet1", 1.0, true, null, 62000L);
        var bg2 = new Product("bg2", "BatchGet2", 2.0, true, null, 63000L);
        table.putItem(bg1);
        table.putItem(bg2);

        List<Product> results = table.batchGet()
                .addKey("bg1")
                .addKey("bg2")
                .execute();

        assertEquals(2, results.size());
    }

    // ============ Transaction operations ============

    @Test
    void transactWritePutAndConditionCheck() {
        var t1 = new Product("tw1", "Transact1", 1.0, true, null, 70000L);
        table.putItem(t1);

        // Condition check that Transact1 has price=1.0, and put Transact2
        var t2 = new Product("tw2", "Transact2", 2.0, true, null, 71000L);

        client.transactWrite()
                .put(table, t2)
                .conditionCheck(table, "tw1", c -> c.eq("price", 1.0))
                .execute();

        assertTrue(table.getItem("tw2").isPresent());
    }

    @Test
    void transactGetItems() {
        var tg1 = new Product("tg1", "TransactGet1", 1.0, true, null, 72000L);
        var tg2 = new Product("tg2", "TransactGet2", 2.0, true, null, 73000L);
        table.putItem(tg1);
        table.putItem(tg2);

        var results = client.transactGet()
                .addGetItem(table, "tg1")
                .addGetItem(table, "tg2")
                .execute();

        assertEquals(2, results.size());
        assertEquals("TransactGet1", ((Product) Objects.requireNonNull(results.get(0))).getName());
    }

    @Test
    void transactGetNonExistentItemReturnsNull() {
        // Request an item that does not exist — should return null, not NPE
        var results = client.transactGet()
                .addGetItem(table, "nonexistent-transact")
                .execute();

        assertEquals(1, results.size());
        assertNull(results.get(0));
    }

    // ============ Projection via GetItemBuilder ============

    @Test
    void getItemWithProjection() {
        table.putItem(new Product("prj1", "ProjectedGet", 42.0, true, Set.of("demo"), 80000L));

        Optional<Product> result = table.get("prj1")
                .project("id", "name")
                .execute();

        assertTrue(result.isPresent());
        assertEquals("ProjectedGet", result.get().getName());
        assertNull(result.get().getPrice()); // not projected
    }
}
