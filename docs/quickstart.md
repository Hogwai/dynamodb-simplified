# Quickstart

## Prerequisites

- Java 17+
- AWS SDK DynamoDB 2.29.52+
- A running DynamoDB instance (local or AWS)

## Create a client

```java
// Default: uses the default AWS credentials chain and region
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();

// Custom: with an existing DynamoDbClient
DynamoDbClient rawClient = DynamoDbClient.builder()
    .region(Region.EU_WEST_1)
    .build();
DynamoSimplifiedClient client = DynamoSimplifiedClient.create(rawClient);

// Builder: fluent configuration
DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
    .region(Region.US_EAST_1)
    .endpointOverride(URI.create("http://localhost:8000"))
    .build();
```

## Define an item

```java
@DynamoDbBean
public class Post {
    private String id;
    private String title;
    private String content;
    private long createdAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDbSortKey
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

## Get a table reference

```java
Table<Post> table = client.table("posts", Post.class);
```

## CRUD operations

### Create / Update

```java
// Full item put (insert or replace)
table.put(post).execute();

// Conditional put: only succeeds if the item doesn't already exist
table.put(post).onlyIfNotExists("id").execute();

// Put with condition expression
table.put(post).condition(c -> c.eq("status", "draft")).execute();

// Partial update with expression
table.update(post, expr -> expr.set("title", "New Title")
                                 .add("views", 1)).execute();
```

### Read

```java
// Get by partition key (tables without sort key)
Optional<Post> post = table.getItem("post-1");

// Get by partition + sort key
Optional<Post> post = table.getItem("post-1", 12345L);

// Batch get multiple items
List<Post> results = table.batchGet()
    .addKey("post-1", 12345L)
    .addKey("post-2", 67890L)
    .consistentRead(true)
    .execute();
```

### Delete

```java
// Delete by partition key
table.deleteItem("post-1");

// Delete by partition + sort key
table.deleteItem("post-1", 12345L);

// Conditional delete with returned values
Post deleted = table.delete()
    .key("post-1", 12345L)
    .condition(c -> c.eq("status", "draft"))
    .returnValues(ReturnValue.ALL_OLD)
    .execute();
```

## Time To Live (TTL)

```java
// Enable TTL on an attribute
table.enableTtl("expiresAt");

// Set TTL on an update
table.update(post, expr -> expr.set("status", "archived")
                                .ttl("expiresAt", Duration.ofDays(90)))
    .execute();
```

## Optimistic Locking

```java
@DynamoDbBean
public class VersionedItem {
    private String id;
    private int version;  // @Version field

    @DynamoDbPartitionKey
    public String getId() { return id; }

    @Version
    public int getVersion() { return version; }
}

// Auto-checks version on write, increments on success
table.put(item).withOptimisticLocking().execute();
table.update(item, expr -> expr.set("title", "Updated"))
    .withOptimisticLocking()
    .execute();
```

## Query

```java
// Simple partition key query
List<Post> results = table.query()
    .partitionKey("post-1")
    .executeAll();

// Sort key conditions
List<Post> results = table.query()
    .partitionKeyAndSortKeyBetween("post-1", 1000L, 2000L)
    .executeAll();

// Descending order
List<Post> results = table.query()
    .partitionKey("post-1")
    .descending()
    .executeAll();

// With filter expression
List<Post> results = table.query()
    .partitionKey("post-1")
    .filter(f -> f.eq("status", "published").gt("views", 100))
    .executeAll();

// Project only specific attributes
List<Post> results = table.query()
    .partitionKey("post-1")
    .project("id", "title")
    .executeAll();

// Paginated query
PagedResult<Post> page = table.query()
    .partitionKey("post-1")
    .limit(10)
    .executeWithPagination();

// Continue from last page
PagedResult<Post> nextPage = table.query()
    .partitionKey("post-1")
    .limit(10)
    .startFrom(page.getLastEvaluatedKey())
    .executeWithPagination();

// Check consumed capacity
PagedResult<Post> page = ...;
ConsumedCapacity capacity = page.consumedCapacity();  // may be null

// Count items (no data transferred)
long count = table.query()
    .partitionKey("post-1")
    .count();

// Stream results lazily
table.query()
    .partitionKey("post-1")
    .executeStream()
    .forEach(item -> { ... });

// Get first result only
Optional<Post> first = table.query()
    .partitionKey("post-1")
    .executeAndGetFirst();
```

## Scan

```java
// Full table scan
List<Post> all = table.scan().executeAll();

// Scan with filter
List<Post> results = table.scan()
    .filter(f -> f.gt("views", 100))
    .executeAll();

// Scan with limit
PagedResult<Post> page = table.scan()
    .limit(50)
    .executeWithPagination();
```

## Transactions

```java
// Transactional write (all or nothing)
client.transactWrite()
    .put(table, newPost)
    .update(table, existingPost)          // full item replacement
    .update(table, existingPost, expr -> expr.set("title", "Updated"))  // partial update
    .delete(table, "post-2", 67890L)
    .conditionCheck(table, "post-3", c -> c.exists("id"))
    .execute();

// Transactional get
TransactGetResults items = client.transactGet()
    .addGetItem(table, "post-1", 12345L)
    .addGetItem(table, "post-2", 67890L)
    .execute();
```

## Batch operations

```java
// Batch write (mix of puts and deletes)
table.batchWrite()
    .put(post1)
    .put(post2)
    .delete("post-3", 11111L)
    .execute();
```

## Batch Put

```java
// Insert multiple items in one batch
table.putAll(List.of(post1, post2, post3));
```

## Secondary indexes (GSI / LSI)

```java
// Query a global secondary index
List<Post> results = table.index("by_status")
    .query()
    .partitionKey("published")
    .executeAll();

// Query with sort key on index
List<Post> results = table.index("by_status")
    .query()
    .partitionKey("published")
    .descending()
    .executeAll();
```

## DDL operations

```java
// Create table
table.createTable();

// Delete table
table.deleteTable();

// Check if table exists
boolean exists = table.exists();
```

## PartiQL

```java
// Execute raw PartiQL statement
ExecuteStatementResponse response = client.executeStatement(
    ExecuteStatementRequest.builder()
        .statement("SELECT * FROM posts WHERE status = ?")
        .parameters(AttributeValue.builder().s("published").build())
        .build());
```

## Expressions

Every operation that accepts expressions supports the same fluent API:

```java
// Filter expressions (query/scan)
table.query().partitionKey("pk")
    .filter(f -> f.eq("status", "active").gt("views", 100))
    .executeAll();

// Condition expressions (put/update/delete)
table.put(post).condition(c -> c.eq("status", "draft")).execute();

// Update expressions (partial update)
table.update(post, expr -> expr.set("title", "New Title")
                                 .remove("oldField")
                                 .add("views", 1)
                                 .set("tags", Set.of("java", "aws"))).execute();

// Projection expressions (read specific attributes)
table.query().partitionKey("pk")
    .project(p -> p.include("id", "title"))
    .executeAll();
```

## Async API

All operations have an async counterpart with identical method signatures. Return types change from `T` to `CompletableFuture<T>`.

```java
AsyncDynamoSimplifiedClient asyncClient = AsyncDynamoSimplifiedClient.create();
AsyncTable<Post> asyncTable = asyncClient.table("posts", Post.class);

asyncTable.query()
    .partitionKey("post-1")
    .executeAll()
    .thenAccept(results -> System.out.println("Found " + results.size()));

// Async transaction
asyncClient.transactWrite()
    .put(asyncTable, newPost)
    .update(asyncTable, existingPost, expr -> expr.set("title", "Updated"))
    .execute()
    .thenRun(() -> System.out.println("Transaction complete"));
```

## Single-Table Design

Define entities with annotations:

```java
@Entity(discriminator = "POST", table = "myapp")
@KeyPrefix(component = "PK", value = "POST")
public class Post {
    private String pk;
    private String postId;

    @KeyComponent(component = "PK", position = 0)
    public String getPostId() { return postId; }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }
}
```

Use the entity-aware table:

```java
EntityTable<Post> posts = client.entityTable(Post.class);
posts.put(new Post("post-123"));  // pk auto-computed to "POST#post-123"

// Read: auto-filters by discriminator
List<Post> results = posts.query("POST#post-123").executeAll();
```

Cross-entity queries:

```java
CrossEntityResult result = client.entityQuery("myapp")
    .partitionKey("POST#post-123")
    .includeEntity(Post.class)
    .includeEntity(Comment.class)
    .execute();
```

Async variant:

```java
AsyncEntityTable<Post> asyncPosts = asyncClient.entityTable(Post.class);
asyncPosts.put(new Post("post-456")).join();
```
