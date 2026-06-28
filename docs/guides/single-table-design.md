# Single-Table Design Guide

DynamoDB Simplified is the first Java library with **first-class single-table design support**. This guide covers the complete API: annotations, entity tables, cross-entity queries, and best practices.

---

## Table of Contents

- [What is Single-Table Design?](#what-is-single-table-design)
- [Annotations](#annotations)
  - [@Entity](#entity)
  - [@KeyComponent](#keycomponent)
  - [@KeyPrefix](#keyprefix)
  - [@Version](#version)
- [Entity Schema](#entity-schema)
- [Defining Entities](#defining-entities)
- [Sync Operations (EntityTable)](#sync-operations-entitytable)
- [Async Operations (AsyncEntityTable)](#async-operations-asyncentitytable)
- [Cross-Entity Queries](#cross-entity-queries)
- [Best Practices](#best-practices)
- [Complete Example](#complete-example)

---

## What is Single-Table Design?

Single-table design stores multiple entity types (users, posts, comments, etc.) in one DynamoDB table. Composite keys (`PK`/`SK`) and discriminator attributes distinguish entity types and enable efficient access patterns:

| Entity  | PK prefix      | SK value | `_type`   |
|---------|----------------|----------|-----------|
| User    | `USER#user123` |n/a| `USER`    |
| Post    | `POST#post456` |n/a| `POST`    |
| Comment | `COMMENT#c789` |n/a| `COMMENT` |

The library automates this pattern: it computes composite keys from your entity fields, sets discriminator values automatically, and filters by entity type on every query.

---

## Annotations

### @Entity

Marks a class as a single-table entity. Required on every entity class.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
    String discriminator();
    String discriminatorAttribute() default "_type";
    String table();
}
```

| Attribute                | Required | Default   | Description                                                         |
|--------------------------|----------|-----------|---------------------------------------------------------------------|
| `discriminator`          | Yes      |n/a| Unique value identifying this entity type (e.g. `"USER"`, `"POST"`) |
| `discriminatorAttribute` | No       | `"_type"` | DynamoDB attribute name storing the discriminator value             |
| `table`                  | Yes      |n/a| DynamoDB table name where this entity is stored                     |

### @KeyComponent

Marks a field or getter as a component of a composite key. Multiple `@KeyComponent` annotations on the same component name are joined with `#` in position order.

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface KeyComponent {
    String component();
    int position() default 0;
}
```

| Attribute   | Required | Default | Description                                                                |
|-------------|----------|---------|----------------------------------------------------------------------------|
| `component` | Yes      |n/a| Composite key component name (e.g. `"PK"`, `"SK"`, `"GSI1PK"`)             |
| `position`  | No       | `0`     | Ordering within the composite key; components joined in ascending position |

### @KeyPrefix

Specifies a prefix prepended to a composite key component, followed by `#`. Repeatable for multiple key components.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(KeyPrefix.Container.class)
public @interface KeyPrefix {
    String component();
    String value();
}
```

| Attribute   | Required | Default | Description                                        |
|-------------|----------|---------|----------------------------------------------------|
| `component` | Yes      | n/a     | The composite key component this prefix applies to |
| `value`     | Yes      | n/a     | The prefix string (e.g. `"USER"`, `"POST"`)        |

### @Version

Marks an attribute as the version field for optimistic locking.

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Version {
}
```

The annotated field must be `Integer` or `int`. When optimistic locking is enabled, the library automatically adds a condition expression that checks the version hasn't changed and increments it on successful write.

---

## Entity Schema

The `EntitySchema` is the runtime representation of an entity's annotations. It is produced by `EntitySchemaReader.read(YourEntity.class)` and provides:

- `entityClass()`: the Java class
- `discriminator()` — the discriminator value
- `discriminatorAttribute()` — the DynamoDB attribute storing the discriminator
- `tableName()` — the DynamoDB table
- `computeKey(component, entity)` — computes a composite key value by extracting `@KeyComponent` fields and prepending any `@KeyPrefix`

You rarely interact with `EntitySchema` directly — `EntityTable` and `EntityQueryBuilder` handle it internally.

---

## Defining Entities

### Basic Entity (Partition Key Only)

```java
import dev.hogwai.dynamodb.simplified.entity.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@Entity(discriminator = "USER", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
public class User {
    private String pk;
    private String userId;
    private String name;

    public User() {}

    public User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @KeyComponent(component = "PK", position = 0)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

When you `put(new User("abc123", "Alice"))` the library automatically:

1. Computes the partition key from `@KeyComponent(component = "PK")` + `@KeyPrefix(component = "PK", value = "USER")` → `"USER#abc123"`
2. Sets `pk = "USER#abc123"` on the entity via the `@DynamoDbPartitionKey` setter

### Entity with Partition and Sort Keys

```java
@DynamoDbBean
@Entity(discriminator = "ITEM", table = "myapp")
@KeyPrefix(component = "PK", value = "PARENT")
public class EntityWithSk {
    private String pk;
    private String sk;

    public EntityWithSk() {}

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @KeyComponent(component = "PK")
    public String getPkComponent() { return pk; }
    public void setPkComponent(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @KeyComponent(component = "SK")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }
}
```

### Multiple Key Prefixes (GSI key components)

```java
@DynamoDbBean
@Entity(discriminator = "MULTI_PREFIX", table = "myapp")
@KeyPrefix(component = "PK", value = "PARENT")
@KeyPrefix(component = "SK", value = "CHILD")
public class MultiPrefixEntity {
    // ...
}
```

---

## Sync Operations (EntityTable)

Obtain an `EntityTable` from the client:

```java
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
EntityTable<User> users = client.entityTable(User.class);
```

### Put

Automatically computes composite keys and sets them on the entity before writing:

```java
User user = new User("abc123", "Alice");
users.put(user);
// user.getPk() is now "USER#abc123"
```

### Get

```java
// By partition key only
User alice = users.get("USER#abc123");

// By partition and sort key
User item = users.get("USER#abc123", "POST#def456");
```

Returns `null` if the item is not found.

### Query

Queries all items sharing a partition key, **automatically filtered by the entity's discriminator**:

```java
List<User> results = users.query("USER#abc123");
// Only items with _type = "USER" are returned
```

### Delete

```java
// By partition key
users.delete("USER#abc123");

// By partition and sort key
users.delete("USER#abc123", "POST#def456");
```

### Update

Computes composite keys then performs an update:

```java
User user = new User("abc123", "Alice Updated");
users.update(user);
// user.getPk() is now "USER#abc123"
```

---

## Async Operations (AsyncEntityTable)

The async variant mirrors the sync API exactly, with all methods returning `CompletableFuture`:

```java
AsyncDynamoSimplifiedClient asyncClient = AsyncDynamoSimplifiedClient.create();
AsyncEntityTable<User> users = asyncClient.entityTable(User.class);

// Put
users.put(new User("abc123", "Alice")).join();

// Get
CompletableFuture<User> future = users.get("USER#abc123");
User alice = future.join();

// Query
CompletableFuture<List<User>> queryFuture = users.query("USER#abc123");

// Delete by entity (extracts keys from the entity)
User user = new User("abc123", "Alice");
users.deleteEntity(user).join();

// Delete by key
users.delete("USER#abc123").join();
users.delete("USER#abc123", "POST#def456").join();

// Update
CompletableFuture<User> updated = users.update(user);
```

### Method reference

| Operation | Sync | Async |
|-----------|------|-------|
| Put | `void put(T)` | `CompletableFuture<Void> put(T)` |
| Get (PK) | `T get(Object)` | `CompletableFuture<T> get(Object)` |
| Get (PK+SK) | `T get(Object, Object)` | `CompletableFuture<T> get(Object, Object)` |
| Query | `List<T> query(Object)` | `CompletableFuture<List<T>> query(Object)` |
| Delete (entity) | — | `CompletableFuture<Void> deleteEntity(T)` |
| Delete (PK) | `void delete(Object)` | `CompletableFuture<Void> delete(Object)` |
| Delete (PK+SK) | `void delete(Object, Object)` | `CompletableFuture<Void> delete(Object, Object)` |
| Update | `void update(T)` | `CompletableFuture<T> update(T)` |

---

## Cross-Entity Queries

Cross-entity queries retrieve multiple entity types from the same partition key in a single DynamoDB query. The library builds an `OR`-based filter expression for the discriminator attribute and maps each result to its entity type.

### Using EntityQueryBuilder

```java
import dev.hogwai.dynamodb.simplified.entity.*;

EntityQueryBuilder query = new EntityQueryBuilder(
    dynamoDbClient,     // the low-level DynamoDbClient
    "myapp",            // table name
    "_type"             // discriminator attribute name
);
```

> Note: `EntityQueryBuilder` is constructed directly from the low-level `DynamoDbClient`. There is currently no facade method on `DynamoSimplifiedClient` — use `client.getDynamoDbClient()` to access it.

### Basic Cross-Entity Query

```java
CrossEntityResult result = query
    .partitionKey("PART#abc")
    .includeEntity(UserEntity.class)
    .includeEntity(PostEntity.class)
    .execute();

List<UserEntity> users = result.get(UserEntity.class);
List<PostEntity> posts = result.get(PostEntity.class);
```

### Sort Key Conditions

```java
// begins_with
query.partitionKey("USER#abc")
     .includeEntity(EntityWithSk.class)
     .sortKeyBeginsWith("PREFIX");

// equals
query.sortKeyEquals("EXACT");

// between
query.sortKeyBetween("2024-01-01", "2024-12-31");

// greater than / less than
query.sortKeyGreaterThan("2024-06-01");
query.sortKeyLessThanOrEqual("2024-06-30");
```

### Options

```java
// Pagination
CrossEntityResultWithPagination page = query
    .partitionKey("USER#abc")
    .includeEntity(UserEntity.class)
    .executeWithPagination();

if (page.hasMore()) {
    Map<String, AttributeValue> lastKey = page.getLastEvaluatedKey();
}

// All pages
List<CrossEntityResult> allPages = query
    .partitionKey("USER#abc")
    .includeEntity(UserEntity.class)
    .executeAll();

// First result only
Optional<CrossEntityResult> first = query
    .partitionKey("USER#abc")
    .includeEntity(UserEntity.class)
    .executeAndGetFirst();

// Count (uses Select.COUNT internally)
long count = query
    .partitionKey("USER#abc")
    .includeEntity(UserEntity.class)
    .count();

// Consistent read
query.consistentRead(true);

// Sort order
query.scanIndexForward(false);  // descending

// Projection
query.project("pk", "userId", "name");

// Limit
query.limit(50);
```

### Result Types

**CrossEntityResult** — maps entity classes to their typed lists:

```java
public final class CrossEntityResult {
    <T> List<T> get(Class<T> entityClass);     // typed accessor
    Map<Class<?>, List<?>> getAll();            // full result map
    boolean isEmpty();
    int size();                                  // total items across all types
}
```

**CrossEntityResultWithPagination** — single page with pagination metadata:

```java
public final class CrossEntityResultWithPagination {
    CrossEntityResult getResult();
    Map<String, AttributeValue> getLastEvaluatedKey();
    boolean hasMore();  // true if lastEvaluatedKey is non-empty
}
```

---

## Best Practices

### 1. Naming Convention

Use `UPPER_SNAKE_CASE` for:
- Discriminator values (e.g., `"USER"`, `"POST"`, `"COMMENT"`)
- Key component names (e.g., `"PK"`, `"SK"`, `"GSI1PK"`)
- Key prefix values (e.g., `"USER"`, `"POST"`)

### 2. Consistent Table Name

All entities sharing a DynamoDB table must use the same `table` value in `@Entity`. This is your single-table.

### 3. Unique Discriminators

Each entity type in a table must have a unique discriminator. Duplicate discriminators cause incorrect cross-entity query results.

### 4. Default Constructor

DynamoDB Enhanced Client requires a public no-arg constructor on all entity beans.

### 5. @DynamoDbPartitionKey / @DynamoDbSortKey

You must annotate the PK/SK getters with `@DynamoDbPartitionKey` and (optionally) `@DynamoDbSortKey` so the Enhanced Client knows the table key schema. The library computes the values into these fields before writes.

### 6. Setter Requirement

The library requires a public setter for the `@DynamoDbPartitionKey` field. Without it, the library cannot inject computed composite key values.

### 7. Async deleteEntity()

The async `deleteEntity(T)` method computes keys from the passed entity instance. Use it when you have an entity object but not the raw key values.

### 8. EntityQueryBuilder Construction

`EntityQueryBuilder` requires the low-level `DynamoDbClient`. Access it from your `DynamoSimplifiedClient`:

```java
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
EntityQueryBuilder query = new EntityQueryBuilder(
    client.getDynamoDbClient(),
    "myapp",
    "_type"
);
```

### 9. Query Discriminator Filtering

`EntityTable.query()` and `EntityQueryBuilder` both filter by discriminator. `EntityTable.query()` filters automatically using `WHERE _type = '<discriminator>'`. `EntityQueryBuilder` builds an `OR` filter for all included entity types.

---

## Complete Example

Below is a full example using single-table design with User and Post entities, sync and async operations, and a cross-entity query.

```java
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.EntityTable;
import dev.hogwai.dynamodb.simplified.entity.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

// ---- Entity Definitions ----

@DynamoDbBean
@Entity(discriminator = "USER", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
public class User {
    private String pk;
    private String userId;
    private String name;

    public User() {}
    public User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @KeyComponent(component = "PK", position = 0)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@DynamoDbBean
@Entity(discriminator = "POST", table = "myapp")
@KeyPrefix(component = "PK", value = "POST")
public class Post {
    private String pk;
    private String postId;
    private String title;

    public Post() {}
    public Post(String postId, String title) {
        this.postId = postId;
        this.title = title;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @KeyComponent(component = "PK", position = 0)
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

// ---- Usage ----

public class SingleTableExample {
    public static void main(String[] args) {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.create();

        // Obtain entity-aware tables
        EntityTable<User> users = client.entityTable(User.class);
        EntityTable<Post> posts = client.entityTable(Post.class);

        // Put — keys auto-computed
        User alice = new User("alice1", "Alice");
        users.put(alice);                    // pk auto-set to "USER#alice1"

        Post post = new Post("post001", "Hello World");
        posts.put(post);                     // pk auto-set to "POST#post001"

        // Get
        User found = users.get("USER#alice1");
        System.out.println(found.getName()); // "Alice"

        // Query — auto-filtered by discriminator
        var aliceUsers = users.query("USER#alice1");
        var alicePosts = posts.query("POST#post001");

        // Update
        alice.setName("Alice Updated");
        users.update(alice);

        // Delete
        users.delete("USER#alice1");
        posts.delete("POST#post001");

        // Cross-entity query
        EntityQueryBuilder query = new EntityQueryBuilder(
            client.getDynamoDbClient(), "myapp", "_type"
        );
        CrossEntityResult result = query
            .partitionKey("POST#post001")
            .includeEntity(User.class)
            .includeEntity(Post.class)
            .execute();

        List<User> usersFound = result.get(User.class);
        List<Post> postsFound = result.get(Post.class);

        // Async variant
        try (var asyncClient = AsyncDynamoSimplifiedClient.create()) {
            var asyncUsers = asyncClient.entityTable(User.class);
            asyncUsers.put(new User("async1", "Async User")).join();
        }
    }
}
```
