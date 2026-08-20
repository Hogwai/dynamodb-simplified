# DynamoDB Simplified

[![Maven Central](https://img.shields.io/maven-central/v/dev.hogwai/dynamodb-simplified-core)](https://central.sonatype.com/artifact/dev.hogwai/dynamodb-simplified-core)
[![CI](https://github.com/hogwai/dynamodb-simplified/actions/workflows/ci.yml/badge.svg)](https://github.com/hogwai/dynamodb-simplified/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)
[![Javadoc](https://img.shields.io/badge/docs-javadoc-blue)](https://hogwai.github.io/dynamodb-simplified/javadoc/)
[![License](https://img.shields.io/github/license/hogwai/dynamodb-simplified)](LICENSE)

A fluent wrapper for the AWS DynamoDB Enhanced Client with typed builders for common DynamoDB operations.

---

## Installation

Add the dependency to your project:

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

---

## Comparison

### dynamodb-enhanced

```java
Map<String, String> expressionNames = new HashMap<>();
expressionNames.put("#status", "status");
expressionNames.put("#createdUtc", "createdUtc");
expressionNames.put("#keywords", "keywords");

Map<String, AttributeValue> expressionValues = new HashMap<>();
expressionValues.put(":statusVal", AttributeValue.builder().s("ACTIVE").build());
expressionValues.put(":dateVal", AttributeValue.builder().n("1700000000").build());
expressionValues.put(":sizeVal", AttributeValue.builder().n("3").build());

QueryEnhancedRequest request = QueryEnhancedRequest.builder()
    .queryConditional(QueryConditional.keyEqualTo(
        Key.builder().partitionValue("java").build()))
    .filterExpression(Expression.builder()
        .expression("#status = :statusVal AND #createdUtc > :dateVal AND size(#keywords) > :sizeVal")
        .expressionNames(expressionNames)
        .expressionValues(expressionValues)
        .build())
    .scanIndexForward(false)
    .limit(10)
    .build();

List<Post> posts = table.query(request)
    .stream()
    .flatMap(page -> page.items().stream())
    .collect(Collectors.toList());
```

### dynamodb-simplified

```java
List<Post> posts = table.query()
    .partitionKey("java")
    .filter(f -> f
        .eq("status", "ACTIVE")
        .and()
        .gt("createdUtc", 1700000000L)
        .and()
        .sizeGt("keywords", 3))
    .descending()
    .limit(10)
    .executeAll();
```

---

### Server-side `size()`

```java
List<Post> posts = table.query()
        .partitionKey("java")
        .filter(f -> f.sizeGt("keywords", 3))
        .executeAll();
```

DynamoDB applies the filter using `size(attribute)`, without client-side size calculation. The filter is evaluated after
reading items, so it does not reduce the read capacity consumed.

---

## Features

| Feature                  | Description                                                                                                                                                                  |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Fluent API**           | Chain methods naturally with IntelliSense support                                                                                                                            |
| **Filter Expressions**   | Simple methods for all DynamoDB operators                                                                                                                                    |
| **Update Expressions**   | SET, REMOVE, ADD, DELETE operations with a fluent API                                                                                                                        |
| **Server-side `size()`** | Filter using DynamoDB `size(attribute)` without client-side size calculation                                                                                                 |
| **Projections**          | Select only the attributes you need                                                                                                                                          |
| **Pagination**           | Built-in cursor-based pagination support                                                                                                                                     |
| **Conditional Writes**   | Put, Update, Delete with conditions                                                                                                                                          |
| **Transactions**         | TransactGet and TransactWrite with expression-based partial updates                                                                                                          |
| **Batch Operations**     | BatchGet and BatchWrite, including cross-table operations and typed per-table retrieval for batch-get results                                                                |
| **Error model**          | Mapped operation, condition, and transaction failures; some async paths may expose SDK exceptions                                                                            |
| **Async API**            | Broadly symmetrical async builders with `CompletableFuture` terminals and reactive query/scan streaming                                                                      |
| **DDL Operations**       | `create()`, `delete()`, `describe()`, and `exists()` for table management                                                                                                    |
| **PartiQL**              | Passthrough PartiQL executeStatement for ad-hoc queries                                                                                                                      |
| **GSI/LSI Support**      | Query and scan through secondary indexes                                                                                                                                     |
| **Type Safety**          | Leverages DynamoDB Enhanced Client's bean mapping                                                                                                                            |
| **Zero framework deps**  | Pure Java, no Spring/Micronaut dependency in the core                                                                                                                        |
| **Entity subsystem**     | Entity annotations, computed keys, discriminator filtering, and cross-entity queries                                                                                         |
| **Versioning**           | `@Version` field detection and object-side version support; use explicit conditions for reliable compare-and-set writes                                                      |
| **TTL Management**       | Table config: `enableTtl("expiresAt")`, `disableTtl("expiresAt")`, `describeTtl()`; update: `update(item, u -> u.ttl("expiresAt", Duration.ofDays(90))).execute()`           |
| **Batch Retry**          | Same-table batch-get `execute()` without projection uses the AWS Enhanced paginator; bounded retries cover sync batch-write, sync low-level batch-get, and async batch-write |
| **Async Streaming**      | `streamResults()` for async query and `executeStream()` for async scan; both expose reactive `SdkPublisher<T>` results                                                       |

---

## Quick Example

```java
// Create client
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
Table<Post> posts = client.table("posts", Post.class);

// Put
posts.put(post).execute();

// Get by partition key
Optional<Post> result = posts.getItem("java");

// Query with filter
List<Post> results = posts.query()
    .partitionKey("java")
    .filter(f -> f.gt("views", 100))
    .executeAll();

// Partial update
Optional<Post> updated = posts.update(post, expr -> expr.set("title", "New Title"))
    .execute();

// Transaction with condition check
client.transactWrite()
    .put(posts, newPost)
    .

conditionCheck(posts, "java",12345L,c ->c.

eq("status","ACTIVE"))
    .execute();
```

---

## Single-Table Design

DynamoDB Simplified supports entity-oriented single-table access through entity annotations, computed keys,
discriminator filtering, and cross-entity queries.

```java

@DynamoDbBean
@Entity(discriminator = "USER", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
@KeyPrefix(component = "SK", value = "PROFILE")
public class User {
    private String pk;                   // becomes "USER#user123" after the entity write
    private String sk;                   // becomes "PROFILE#profile" after the entity write
    private String userId;

    public User() {
    }

    public User(String userId) {
        this.pk = userId;                // raw component before prefixing
        this.sk = "profile";             // raw component before prefixing
        this.userId = userId;
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

    public String getUserId() { return userId; }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

// Another compatible entity bean in the same table
@DynamoDbBean
@Entity(discriminator = "POST", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
@KeyPrefix(component = "SK", value = "POST")
class Post {
    private String pk;
    private String sk;

    public Post() {
    }

    public Post(String userId, String postId) {
        this.pk = userId;
        this.sk = postId;
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

// Entity-aware table: keys auto-computed, discriminator auto-filtered
EntityTable<User> users = client.entityTable(User.class);
users.put(new User("user123"));  // pk auto-set to "USER#user123"
        client.

entityTable(Post .class).

put(new Post("user123", "post456"));

// Cross-entity queries
CrossEntityResult result = client.entityQuery("myapp")
    .partitionKey("USER#user123")
    .includeEntity(User.class)
    .includeEntity(Post.class)
    .execute();
List<User> matchingUsers = result.get(User.class);
```

`@Version` is detected on the object and can be incremented by supported version helpers. On direct `Table` put/update
builders, call
`.withOptimisticLocking()` to activate the built-in version detection condition. For full and partial writes, the
builders increment the Java object after a successful write; neither the annotation nor this object-side increment alone
guarantees that the intended version was persisted. For a strict compare-and-set, load the current item and explicitly
write the next version with a condition.
`EntityTable` does not enable that CAS automatically.

```java
// With a Table<VersionedItem> versionedTable and an existing item:
VersionedItem current = versionedTable.getItem("item-1").orElseThrow();
int expectedVersion = current.getVersion();

versionedTable.

update(current, expression ->expression
        .

set("title","Updated")
        .

set("version",expectedVersion +1))
        .

condition(c ->c.

eq("version",expectedVersion))
        .

execute();
```

See the [Single-Table Design Guide](docs/guides/single-table-design.md) for full documentation. See also
the [Expressions guide](docs/guides/expressions.md), [Async API guide](docs/guides/async.md), [Batch and Results guide](docs/guides/batch-and-results.md),
and [Errors and Retries guide](docs/guides/errors-and-retries.md).

---

## Documentation

Full documentation is available at the [project site](https://hogwai.github.io/dynamodb-simplified/), including
a [Quickstart guide](https://hogwai.github.io/dynamodb-simplified/quickstart/) with a core API walkthrough (CRUD, query,
scan, batch, transactions, indexes, DDL, PartiQL, async).

The [API reference](https://hogwai.github.io/dynamodb-simplified/javadoc/index.html) is generated from Javadoc comments.

---

## Requirements

- Java 21+

---

## Project Structure

```
dynamodb-simplified/
├── build.gradle.kts              # build config
├── settings.gradle.kts
├── src/main/java/dev/hogwai/dynamodb/simplified/
│   ├── DynamoSimplifiedClient.java    # entry point, factory methods
│   ├── Table.java                     # fluent table operations
│   ├── async/                         # AsyncDynamoSimplifiedClient, AsyncTable, async builders
│   ├── builder/                       # QueryBuilder, PutBuilder, TransactWriteBuilder, etc.
│   ├── exception/                     # DynamoSimplifiedException, OperationFailedException, ConditionFailedException, ResourceNotFoundException, TransactionFailedException
│   ├── entity/                        # @Entity, @KeyComponent, EntityTable, EntityQueryBuilder
│   ├── expression/                    # FilterExpression, UpdateExpression, ProjectionExpression
│   ├── internal/                      # AttributeValueConverter
│   └── result/                        # PagedResult, TransactGetResults, BatchGetResult
```

---

## Demo applications

Example applications using the library are available in a separate repository:

[dynamodb-simplified-demo](https://github.com/Hogwai/dynamodb-simplified-demo)

---

## License

This project is licensed under the [MIT license](LICENSE).
