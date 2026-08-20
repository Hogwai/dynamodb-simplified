# Single-Table Design

DynamoDB Simplified provides entity-oriented single-table access through annotations, computed keys, discriminator
filtering and cross-entity queries. This guide covers the documented API areas for annotations, entity tables,
cross-entity queries and best practices.

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
- [Limitations](#limitations)
- [Best Practices](#best-practices)
- [Complete Example](#complete-example)

---

## What is Single-Table Design?

Single-table design stores multiple entity types (users, posts, comments, etc.) in one DynamoDB table. Composite keys
(`PK`/`SK`) and discriminator attributes distinguish entity types and enable efficient access patterns:

| Entity  | PK prefix      | SK value | `_type`   |
|---------|----------------|----------|-----------|
| User    | `USER#user123` | N/A      | `USER`    |
| Post    | `POST#post456` | N/A      | `POST`    |
| Comment | `COMMENT#c789` | N/A      | `COMMENT` |

The library automates this pattern: it computes composite keys from your entity fields, writes the discriminator
automatically through the entity table schema, and filters by entity type on every entity query.
`_type` is the default discriminator attribute; `discriminatorAttribute` selects a custom attribute name.

An entity does not need a mapped discriminator property: `EntityTable` adds the configured discriminator attribute when
it writes the item, and applies the same attribute as a query filter.

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
| `discriminator`          | Yes      | N/A       | Unique value identifying this entity type (e.g. `"USER"`, `"POST"`) |
| `discriminatorAttribute` | No       | `"_type"` | DynamoDB attribute name storing the discriminator value             |
| `table`                  | Yes      | N/A       | DynamoDB table name where this entity is stored                     |

### @KeyComponent

Marks a field or getter as a component of a composite key. Multiple `@KeyComponent` annotations on the same component
name are joined with `#` in position order.

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
| `component` | Yes      | N/A     | Composite key component name (e.g. `"PK"`, `"SK"`, `"GSI1PK"`)             |
| `position`  | No       | `0`     | Ordering within the composite key; components joined in ascending position |

#### Field and method forms

Fields are extracted directly by name, including private fields. Methods must be public, non-static, no-argument
getters. Both forms contribute the value to the named component in position order.

```java

@KeyComponent(component = "PK", position = 0)
private String tenantId; // field value is read directly

private String createdAtKey;

@KeyComponent(component = "SK", position = 0)
public String getCreatedAtKey() { // public, non-static getter
  return createdAtKey;
}
```

The `PK` and `SK` component names identify the primary partition and sort key values. Other component names can
represent mapped secondary-index key attributes or additional computed attributes. The entity bean must map `PK` and
`SK` to `@DynamoDbPartitionKey` and `@DynamoDbSortKey` properties, with public setters so the computed values can be
written back before a put or update. For another component, provide the corresponding mapped attribute and setter (for
example, `setGSI1PK(...)`) when the computed value must be injected into the entity before it is written.

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
| `component` | Yes      | N/A     | The composite key component this prefix applies to |
| `value`     | Yes      | N/A     | The prefix string (e.g. `"USER"`, `"POST"`)        |

### @Version

Marks a field used by the version helpers. It can participate in optimistic locking when a direct put/update builder
opts in; the annotation alone is not a reliable automatic lock. Apply it to the field; leave its ordinary bean getter
and setter unannotated.

```java

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Version {
}
```

The annotated field must be `Integer` or `int`.
`@Version` is detected by the version helper and can be incremented on the entity object. On direct `Table` put/update
builders, call `.withOptimisticLocking()` to activate the built-in version detection condition. For full and partial
writes, this increments the Java object after a successful write; neither the annotation nor this object-side increment
alone guarantees that the intended version was persisted.
`EntityTable` does not enable this CAS automatically, so strict compare-and-set writes should load the current item and
explicitly write the next version with a condition.

```java

@Version
private int version;

public int getVersion() {
  return version;
}

public void setVersion(int version) {
  this.version = version;
}
```

```java
// With Table<VersionedItem> table and an existing item:
VersionedItem item = table.getItem("item-1").orElseThrow();
int expectedVersion = item.getVersion();
Optional<VersionedItem> updated = table.update(item, expression -> expression
                .set("version", expectedVersion + 1))
        .condition(condition -> condition.eq("version", expectedVersion))
        .execute();
if(updated.

isPresent()){
        item.

setVersion(expectedVersion +1);
}

// Direct full-item put/update builders require this opt-in for built-in detection:
        table.

update(item).

withOptimisticLocking().

execute();
```

---

## Entity Schema

The `EntitySchema` is the runtime representation of an entity's annotations. It is produced by
`EntitySchemaReader.read(YourEntity.class)` and provides:

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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Entity(discriminator = "USER", table = "users")
@KeyPrefix(component = "PK", value = "USER")
class User {
    private String pk;
    private String userId;
    private String name;

    public User() {}

    public User(String userId, String name) {
      this.pk = userId;
        this.userId = userId;
        this.name = name;
    }

    @DynamoDbPartitionKey
    @KeyComponent(component = "PK", position = 0)
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

When you `put(new User("abc123", "Alice"))` the library automatically:

1. Computes the partition key from the raw `pk` component plus `@KeyPrefix(component = "PK", value = "USER")` →
   `"USER#abc123"`
2. Sets `pk = "USER#abc123"` on the entity via the `@DynamoDbPartitionKey` setter

### Entity with Partition and Sort Keys

```java
@DynamoDbBean
@Entity(discriminator = "ITEM", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
public class EntityWithSk {
    private String pk;
    private String sk;

    public EntityWithSk() {}

  public EntityWithSk(String pkComponent, String skComponent) {
    this.pk = pkComponent;
    this.sk = skComponent;
  }

  @DynamoDbPartitionKey
  @KeyComponent(component = "PK")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

  @DynamoDbSortKey
  @KeyComponent(component = "SK")
  public String getSk() {
    return sk;
  }

  public void setSk(String sk) {
    this.sk = sk;
  }
}
```

For a cross-entity query with `EntityWithSk`, use another entity with the same PK/SK attribute names and types. This
profile entity shares the `USER#...` partition convention and has a distinct sort-key value:

```java

@DynamoDbBean
@Entity(discriminator = "PROFILE", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
@KeyPrefix(component = "SK", value = "PROFILE")
class ProfileWithSk {
  private String pk;
  private String sk;
  private String name;

  public ProfileWithSk() {
  }

  public ProfileWithSk(String userId, String name) {
    this.pk = userId;
    this.sk = "profile";
    this.name = name;
  }

  @DynamoDbPartitionKey
    @KeyComponent(component = "PK")
  public String getPk() {
    return pk;
  }

  public void setPk(String pk) {
    this.pk = pk;
  }

    @DynamoDbSortKey
    @KeyComponent(component = "SK")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
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

Key computation mutates the mapped key properties. Use a fresh entity with raw key components before every `put` or
`update`; do not reuse an instance after its key has already been prefixed, or the prefix can be applied again.

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
User updatedUser = new User("abc123", "Alice Updated");
users.

update(updatedUser);
// updatedUser.getPk() is now "USER#abc123"
```

---

## Async Operations (AsyncEntityTable)

The async variant is broadly symmetrical with the sync API, with asynchronous methods returning `CompletableFuture`:

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
User userForDelete = new User("abc123", "Alice");
users.

deleteEntity(userForDelete).

join();

// Delete by key
users.delete("USER#abc123").join();
users.delete("USER#abc123", "POST#def456").join();

// Update
User userForUpdate = new User("abc123", "Alice Updated");
CompletableFuture<User> updated = users.update(userForUpdate);
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

Cross-entity queries retrieve multiple entity types from the same partition key in a single DynamoDB query. The library
builds an `OR`-based filter expression for the discriminator attribute and maps each result to its entity type.

### Using EntityQueryBuilder

```java
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.entity.EntityQueryBuilder;

DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
EntityQueryBuilder query = client.entityQuery("myapp");
```

`client.entityQuery("myapp")` is the public factory and uses `_type` as the discriminator attribute. For entities that
declare a custom `discriminatorAttribute`, use the overload `client.entityQuery("myapp", "__entity")`.

### Basic Cross-Entity Query

```java
CrossEntityResult result = query
        .partitionKey("USER#abc")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
    .execute();

List<ProfileWithSk> profiles = result.get(ProfileWithSk.class);
List<EntityWithSk> items = result.get(EntityWithSk.class);
```

Both classes in this example use the `myapp` table, `pk`/`sk` key attributes, the same key types, and the same
`USER#abc` partition. A PK-only entity such as the basic `User` above must not be mixed with this PK/SK query.

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

`execute()` sends one DynamoDB query request and maps only that response page. Use `executeAll()` to follow continuation
keys and return one `CrossEntityResult` per page.
`executeAndGetFirst()` examines only the first response page and returns its `CrossEntityResult` when that page is
non-empty; it returns empty when the first response is empty, even if a continuation key is present. It does not inspect
later pages or return one item.

```java
// Pagination
CrossEntityResultWithPagination page = query
    .partitionKey("USER#abc")
                .includeEntity(ProfileWithSk.class)
                .includeEntity(EntityWithSk.class)
    .executeWithPagination();

if (page.hasMore()) {
Map<String, AttributeValue> lastEvaluatedKey = page.getLastEvaluatedKey();

CrossEntityResultWithPagination nextPage = client.entityQuery("myapp")
        .partitionKey("USER#abc")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
        .startFrom(lastEvaluatedKey)
        .executeWithPagination();
}

// All pages
List<CrossEntityResult> allPages = query
    .partitionKey("USER#abc")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
    .executeAll();

// First response page only
Optional<CrossEntityResult> first = query
    .partitionKey("USER#abc")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
    .executeAndGetFirst();

// Count matches for this entity query, not a table-wide count
long count = query
    .partitionKey("USER#abc")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
    .count();

// Consistent read
query.consistentRead(true);

// Sort order
query.scanIndexForward(false);  // descending

// Projection
query.

project("pk","sk","_type");

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

## Limitations

- Every entity class included in a cross-entity query must point to the same DynamoDB table through
  `@Entity(table = "...")`.
- Included entity classes must use compatible primary key attribute names and compatible key types. A query cannot
  combine entities whose mapped PK/SK schema cannot be read from the same table.
- `EntityQueryBuilder` currently constructs the partition-key condition as a DynamoDB string value. Do not present
  numeric partition keys as supported by this cross-entity query path.
- During cross-entity result mapping, an item without the configured discriminator attribute is ignored.
- An item whose discriminator value is not one of the included entity classes is also ignored. It is not deserialized
  into an arbitrary entity type.

## Best Practices

### 1. Naming Convention

Use `UPPER_SNAKE_CASE` for:
- Discriminator values (e.g., `"USER"`, `"POST"`, `"COMMENT"`)
- Key component names (e.g., `"PK"`, `"SK"`, `"GSI1PK"`)
- Key prefix values (e.g., `"USER"`, `"POST"`)

### 2. Consistent Table Name

All entities sharing a DynamoDB table must use the same `table` value in `@Entity`. This is your single-table.

### 3. Unique Discriminators

Each entity type in a table must have a unique discriminator. Duplicate discriminators cause incorrect cross-entity
query results.

### 4. Default Constructor

DynamoDB Enhanced Client requires a public no-arg constructor on all entity beans.

### 5. @DynamoDbPartitionKey / @DynamoDbSortKey

You must annotate the PK/SK getters with `@DynamoDbPartitionKey` and (optionally) `@DynamoDbSortKey` so the Enhanced
Client knows the table key schema. The library computes the values into these fields before writes.

### 6. Setter Requirement

When a computed partition-key component is injected, the `@DynamoDbPartitionKey` property must have a public setter. The
same applies to the `@DynamoDbSortKey` property when a computed sort-key component is injected. If a computed index
component is injected, its mapped index-key property must also expose a public setter (for example, the setter for a
`GSI1PK` property).

### 7. Async deleteEntity()

The async `deleteEntity(T)` method computes keys from the passed entity instance. Use it when you have an entity object
but not the raw key values.

### 8. EntityQueryBuilder Construction

Use the public client factory rather than constructing the builder directly:

```java
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
EntityQueryBuilder query = client.entityQuery("myapp");
EntityQueryBuilder customQuery = client.entityQuery("myapp", "__entity");
```

### 9. Query Discriminator Filtering

`EntityTable.query()` and `EntityQueryBuilder` both filter by discriminator.
`EntityTable.query()` filters automatically using `WHERE <discriminatorAttribute> = '<discriminator>'`; the default
attribute is `_type`.
`EntityQueryBuilder` builds an `OR` filter for all included entity types.

---

## Complete Example

The entity definitions above can be combined with the following end-to-end flow. This keeps the usage example focused on
the unique sync, cross-entity, and async operations rather than repeating the bean definitions.

```java
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
EntityTable<User> users = client.entityTable(User.class);
EntityTable<ProfileWithSk> profiles = client.entityTable(ProfileWithSk.class);
EntityTable<EntityWithSk> items = client.entityTable(EntityWithSk.class);

User alice = new User("alice1", "Alice");
users.

put(alice);                    // pk becomes "USER#alice1"

EntityWithSk item = new EntityWithSk("alice1", "item001");
items.

put(item);                     // pk is prefixed; sk remains "item001"
profiles.

put(new ProfileWithSk("alice1", "Alice"));

User found = users.get("USER#alice1");
List<User> aliceUsers = users.query("USER#alice1");

User updatedAlice = new User("alice1", "Alice Updated");
users.

update(updatedAlice);          // use raw key components on a fresh object
users.

delete("USER#alice1");

CrossEntityResult result = client.entityQuery("myapp")
        .partitionKey("USER#alice1")
        .includeEntity(ProfileWithSk.class)
        .includeEntity(EntityWithSk.class)
        .execute();
List<ProfileWithSk> profilesFound = result.get(ProfileWithSk.class);
List<EntityWithSk> itemsFound = result.get(EntityWithSk.class);

try(
var asyncClient = AsyncDynamoSimplifiedClient.create()){
var asyncUsers = asyncClient.entityTable(User.class);
    asyncUsers.

put(new User("async1", "Async User")).

join();
}
```
