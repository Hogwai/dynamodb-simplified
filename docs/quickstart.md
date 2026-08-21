# Quickstart

## Table of Contents

- [Add the dependency](#add-the-dependency)
- [Prerequisites](#prerequisites)
- [Create a client](#create-a-client)
- [Define an item](#define-an-item)
- [Get a table reference](#get-a-table-reference)
- [CRUD operations](#crud-operations)
  - [Create and update](#create-and-update)
  - [Read](#read)
  - [Delete](#delete)
- [Time To Live (TTL)](#time-to-live-ttl)
- [Versioning and compare-and-set writes](#versioning-and-compare-and-set-writes)
- [Query](#query)
- [Scan](#scan)
- [Transactions](#transactions)
- [Batch operations](#batch-operations)
- [Batch Put](#batch-put)
- [Secondary indexes (GSI and LSI)](#secondary-indexes-gsi-and-lsi)
- [DDL operations](#ddl-operations)
- [PartiQL](#partiql)
- [Expressions](#expressions)
- [Async API](#async-api)
- [Single-Table Design](#single-table-design)

## Add the dependency

DynamoDB Simplified is published on [Maven Central](https://central.sonatype.com/artifact/dev.hogwai/dynamodb-simplified-core).

```kotlin
// Gradle (Kotlin DSL)
implementation("dev.hogwai:dynamodb-simplified-core:0.1.0")
```

```groovy
// Gradle (Groovy)
implementation 'dev.hogwai:dynamodb-simplified-core:0.1.0'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>dev.hogwai</groupId>
    <artifactId>dynamodb-simplified-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Prerequisites

- Java 21+
- A running DynamoDB instance (local or AWS)

## Create a client

```java
import java.net.URI;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

// Default: uses the default AWS credentials chain and region
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();

        // Custom: configure the AWS SDK client, then wrap it
DynamoDbClient rawClient = DynamoDbClient.builder()
    .region(Region.EU_WEST_1)
    .endpointOverride(URI.create("http://localhost:8000"))
    .build();
        DynamoSimplifiedClient customClient = DynamoSimplifiedClient.create(rawClient);
```

## Define an item

```java
@DynamoDbBean
public class Post {
    private String id;
    private String title;
    private String content;
    private String status;
    private long statusUpdatedAt;
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

    @DynamoDbSecondaryPartitionKey(indexNames = "by_status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondarySortKey(indexNames = "by_status")
    public long getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(long statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }
}
```

## Get a table reference

```java
Table<Post> table = client.table("posts", Post.class);
```

## CRUD operations

### Create and update

Conditions and update expressions are covered in the [expressions guide](guides/expressions.md).

```java
import java.util.Optional;

// Full item put (insert or replace)
table.put(post).execute();

// Conditional put: only succeeds if the item doesn't already exist
table.put(post).onlyIfNotExists("id").execute();

// Put with condition expression
table.put(post).condition(c -> c.eq("status", "draft")).execute();

// Partial update with expression
Optional<Post> updated = table.update(post, expr -> expr.set("title", "New Title")
        .addNumber("views", 1))
        .execute();
```

`update(...).execute()` returns an [`Optional<Post>`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html), containing the updated item when DynamoDB returns one.

### Read

```java
import java.util.List;
import java.util.Optional;

import dev.hogwai.dynamodb.simplified.result.BatchGetResult;

// Get by partition key (tables without sort key)
Optional<Post> postById = table.getItem("post-1");

// Get by partition + sort key
Optional<Post> postByKey = table.getItem("post-1", 12345L);

// Batch get multiple items
BatchGetResult<Post> batch = table.batchGet()
    .addKey("post-1", 12345L)
    .addKey("post-2", 67890L)
    .consistentRead(true)
    .execute();
        List<Post> results = batch.items();
```

`batchGet().execute()` returns a [`BatchGetResult<Post>`](https://hogwai.github.io/dynamodb-simplified/javadoc/dev/hogwai/dynamodb/simplified/result/BatchGetResult.html).
Without a projection, the same-table sync builder uses the AWS Enhanced paginator, which normally completes without remaining unprocessed keys.
See the [batch and results guide](guides/batch-and-results.md) for projection and cross-table paths that can expose `unprocessedKeys`.

### Delete

```java
import java.util.Optional;

import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

// Delete by partition key
table.deleteItem("post-1");

// Delete by partition + sort key
table.deleteItem("post-1", 12345L);

// Conditional delete with returned values
Optional<Post> deleted = table.delete("post-1", 12345L)
    .condition(c -> c.eq("status", "draft"))
    .returnValues(ReturnValue.ALL_OLD)
    .execute();
```

`delete(...).execute()` returns an [`Optional<Post>`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html): it is empty when no item matched the key.

## Time To Live (TTL)

```java
// Enable TTL on an attribute
table.enableTtl("expiresAt");

// Set TTL on an update
table.update(post, expr -> expr.set("status", "archived")
                                .ttl("expiresAt", Duration.ofDays(90)))
    .execute();
```

## Versioning and compare-and-set writes

```java
@DynamoDbBean
public class VersionedItem {
    private String id;
    private String title;
    @Version
    private int version;

    @DynamoDbPartitionKey
    public String getId() { return id; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public int getVersion() { return version; }

    public void setId(String id) {
        this.id = id;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}

Table<VersionedItem> versionedTable = client.table("versioned-items", VersionedItem.class);
VersionedItem item = versionedTable.getItem("item-1").orElseThrow();
item.setTitle("Updated");

// Direct full-item builders require withOptimisticLocking() to activate version detection.
Optional<VersionedItem> updated = versionedTable.update(item)
    .withOptimisticLocking()
    .execute();

// For a partial update, load the current item and write the next version explicitly.
VersionedItem current = versionedTable.getItem("item-1").orElseThrow();
int expectedVersion = current.getVersion();
Optional<VersionedItem> partial = versionedTable.update(current, expr -> expr
                .set("title", "Updated again")
                .set("version", expectedVersion + 1))
        .condition(c -> c.eq("version", expectedVersion))
        .execute();
if (partial.isPresent()) {
    current =partial.get();
}
```

`@Version` is detected and can be incremented on the Java object.
On direct `Table` put/update builders, `.withOptimisticLocking()` is required to activate the built-in detection condition.
For full and partial writes, the builders increment the Java object after a successful write; neither the annotation nor this object-side increment alone guarantees that the intended version was persisted.
For a strict compare-and-set, explicitly write the next version with a condition.
`EntityTable` does not provide this CAS automatically.

## Query

```java
// Simple partition key query
List<Post> posts = table.query()
    .partitionKey("post-1")
    .executeAll();

// Sort key conditions
List<Post> range = table.query()
    .partitionKeyAndSortKeyBetween("post-1", 1000L, 2000L)
    .executeAll();

// Descending order
List<Post> descending = table.query()
    .partitionKey("post-1")
    .descending()
    .executeAll();

// With filter expression
List<Post> filtered = table.query()
        .partitionKey("post-1")
        .filter(f -> f.eq("status", "published")
                .and()
                .gt("views", 100))
        .executeAll();

// Server-side size() filter
List<Post> results = table.query()
    .partitionKey("post-1")
        .filter(f -> f.sizeGe("tags", 2)
                .and()
                .sizeLe("tags", 10))
    .executeAll();

// Project only specific attributes
List<Post> projected = table.query()
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
        .startFrom(page.lastEvaluatedKey())
    .executeWithPagination();

// Count reported for this query response/page; not a table-wide global count.
// When pagination is involved, do not treat one count as the full table total.
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

`page.lastEvaluatedKey()` provides the continuation key.
A `null` or empty key means there is no next page; otherwise, pass it to `startFrom(...)` to load the next page.
`limit(10)` limits the items evaluated before a filter is applied.
A filtered page can therefore contain fewer than 10 items, or be empty while still returning a continuation key.
The same filter builder works with `query()` and `scan`.
The available operators are `sizeEq`, `sizeLt`, `sizeLe`, `sizeGt`, `sizeGe`, and `sizeBetween`.
`size()` is evaluated server-side by DynamoDB after items are read; it does not reduce the consumed read capacity.

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
import dev.hogwai.dynamodb.simplified.result.TransactGetResults;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

// Transactional write (all or nothing)
client.transactWrite()
    .put(table, newPost)
    .update(table, existingPost)          // full item replacement
    .update(table, existingPost, expr -> expr.set("title", "Updated"))  // partial update
    .delete(table, "post-2", 67890L)
    .conditionCheck(table, "post-3", 67890L, c -> c.exists("id"))
    .execute();

// Transactional get
TransactGetResults<DynamoDbTable<?>> items = client.transactGet()
    .addGetItem(table, "post-1", 12345L)
    .addGetItem(table, "post-2", 67890L)
    .execute();
```

## Batch operations

See the [batch and results guide](guides/batch-and-results.md) for result objects, unprocessed items, and DynamoDB limits.
See the [errors and retries guide](guides/errors-and-retries.md) for retry behavior.

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

## Secondary indexes (GSI and LSI)

The `Post` bean above declares the `by_status` secondary partition and sort keys.
The DynamoDB table must also be created with the matching `by_status` index.

```java
// Query a global secondary index
List<Post> indexResults = table.index("by_status")
    .query()
    .partitionKey("published")
    .executeAll();

// Query with sort key on index
List<Post> descendingIndexResults = table.index("by_status")
    .query()
    .partitionKey("published")
    .descending()
    .executeAll();
```

## DDL operations

```java
// Create table
table.create();

// Delete table
table.delete();

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

See the [expressions guide](guides/expressions.md) for filter operators, conditions, updates, and projections.

Every operation that accepts expressions supports the same fluent API:

```java
// Filter expressions (query/scan)
table.query().partitionKey("pk")
    .filter(f -> f.eq("status", "active")
    .and()
    .gt("views", 100))
    .executeAll();

// Condition expressions (put/update/delete)
table.put(post).condition(c -> c.eq("status", "draft")).execute();

// Update expressions (partial update)
table.update(post, expr -> expr.set("title", "New Title")
    .remove("oldField")
    .addNumber("views", 1)
    .set("tags", Set.of("java", "aws"))).execute();

// Projection expressions (read specific attributes)
table.query().partitionKey("pk")
    .project(p -> p.include("id", "title"))
    .executeAll();
```

## Async API

See the [async API guide](guides/async.md) for `CompletableFuture` composition, streaming, and asynchronous batch operations.

The async builders are broadly symmetrical with the synchronous builders.
Their terminal results are exposed through `CompletableFuture`; streaming also follows the underlying builder: async query uses `streamResults()`, while async scan uses `executeStream()` and returns a future publisher.

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

See the [single-table design guide](guides/single-table-design.md) for key components, discriminators, and versioning.

Define entities with annotations:

```java

@DynamoDbBean
@Entity(discriminator = "POST", table = "myapp")
@KeyPrefix(component = "PK", value = "POST")
@KeyPrefix(component = "SK", value = "POST")
public class PostEntity {
    private String pk;
    private String sk;
    private String postId;

    public PostEntity() {
    }

    public PostEntity(String postId) {
        this.pk = postId; // raw component before the POST# prefix
        this.sk = "meta"; // raw component before the POST# prefix
        this.postId = postId;
    }

    @DynamoDbPartitionKey
    @KeyComponent(component = "PK", position = 0)
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @KeyComponent(component = "SK", position = 0)
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getPostId() { return postId; }

    public void setPostId(String postId) {
        this.postId = postId;
    }
}

@DynamoDbBean
@Entity(discriminator = "COMMENT", table = "myapp")
@KeyPrefix(component = "PK", value = "POST")
@KeyPrefix(component = "SK", value = "COMMENT")
class CommentEntity {
    private String pk;
    private String sk;

    public CommentEntity() {
    }

    public CommentEntity(String postId, String commentId) {
        this.pk = postId;
        this.sk = commentId;
    }

    @DynamoDbPartitionKey
    @KeyComponent(component = "PK", position = 0)
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @KeyComponent(component = "SK", position = 0)
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }
}
```

Use the entity-aware table:

```java
EntityTable<PostEntity> posts = client.entityTable(PostEntity.class);
PostEntity post = new PostEntity("post-123");
posts.put(post);  // pk auto-computed to "POST#post-123"

EntityTable<CommentEntity> comments = client.entityTable(CommentEntity.class);
comments.put(new CommentEntity("post-123", "comment-1"));

// Read: auto-filters by discriminator
List<PostEntity> results = posts.query("POST#post-123");
```

`@Entity` uses `_type` as the default `discriminatorAttribute`.
`EntityTable` automatically persists the discriminator value in that attribute and filters `query()` by it.
For another name, use for example `@Entity(discriminator = "POST", discriminatorAttribute = "__entity", table = "myapp")`.

For cross-entity mapping, `@KeyComponent` is placed on the actual mapped `pk` and `sk` properties used as the DynamoDB keys.
All included entity beans must use the same table, compatible key property names/types, and a partition that is actually shared by the requested items.

Cross-entity queries:

```java
// Comment is another @Entity class stored in "myapp"
CrossEntityResult result = client.entityQuery("myapp")
    .partitionKey("POST#post-123")
                .includeEntity(PostEntity.class)
                .includeEntity(CommentEntity.class)
    .execute();
```

`client.entityQuery("myapp")` uses `_type`; use `client.entityQuery("myapp", "__entity")` when the entities use a custom `discriminatorAttribute`.

Async variant:

```java
AsyncEntityTable<PostEntity> asyncPosts = asyncClient.entityTable(PostEntity.class);
PostEntity asyncPost = new PostEntity("post-456");
asyncPosts.put(asyncPost).join();
```
