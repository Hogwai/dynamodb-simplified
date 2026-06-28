# DynamoDB Simplified

[![Maven Central](https://img.shields.io/maven-central/v/dev.hogwai/dynamodb-simplified-core)](https://central.sonatype.com/artifact/dev.hogwai/dynamodb-simplified-core)
[![CI](https://github.com/hogwai/dynamodb-simplified/actions/workflows/ci.yml/badge.svg)](https://github.com/hogwai/dynamodb-simplified/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)
[![Javadoc](https://img.shields.io/badge/docs-javadoc-blue)](https://hogwai.github.io/dynamodb-simplified/javadoc/)
[![License](https://img.shields.io/github/license/hogwai/dynamodb-simplified)](LICENSE)

A fluent wrapper for the AWS DynamoDB Enhanced Client that dramatically reduces boilerplate code and improves developer experience.

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

**80% less code. 100% more readable.**

---

## Features

| Feature                  | Description                                                                                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| **Fluent API**           | Chain methods naturally with IntelliSense support                                                                           |
| **Filter Expressions**   | Simple methods for all DynamoDB operators                                                                                   |
| **Update Expressions**   | SET, REMOVE, ADD, DELETE operations with a fluent API                                                                       |
| **Server-side `size()`** | Filter by collection/string size without fetching data                                                                      |
| **Projections**          | Select only the attributes you need                                                                                         |
| **Pagination**           | Built-in cursor-based pagination support                                                                                    |
| **Conditional Writes**   | Put, Update, Delete with conditions                                                                                         |
| **Transactions**         | TransactGet and TransactWrite with expression-based partial updates                                                         |
| **Batch Operations**     | BatchGet and BatchWrite across tables                                                                                       |
| **Async API**            | Complete async counterpart (CompletableFuture-based)                                                                        |
| **DDL Operations**       | Create, delete, describe, and check existence of tables                                                                     |
| **PartiQL**              | Passthrough PartiQL executeStatement for ad-hoc queries                                                                     |
| **GSI/LSI Support**      | Query and scan through secondary indexes                                                                                    |
| **Type Safety**          | Leverages DynamoDB Enhanced Client's bean mapping                                                                           |
| **Zero framework deps**  | Pure Java, no Spring/Micronaut dependency in the core                                                                       |
| **Single-Table Design**  | Entity annotations (`@Entity`, `@KeyComponent`, `@KeyPrefix`) with auto-computed composite keys and discriminator filtering |
| **Optimistic Locking**   | `@Version` annotation with automatic version checking on put/update                                                         |
| **TTL Management**       | `enableTtl()`, `disableTtl()`, `describeTtl()` and `UpdateExpression.ttl(Duration)`                                         |
| **Consumed Capacity**    | Every result type exposes consumed capacity via the `Consumed` interface                                                    |
| **Batch Retry**          | Automatic retry of unprocessed keys with exponential backoff (batch get + write)                                            |
| **Async Streaming**      | `executeStream()` returning `CompletableFuture<SdkPublisher<T>>` for reactive async iteration                               |

---

## Quick Example

```java
// Create client
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
Table<Post> posts = client.table("posts", Post.class);

// Put
posts.put(post).execute();

// Get by partition + sort key
Optional<Post> result = posts.getItem("java", "post-123");

// Query with filter
List<Post> results = posts.query()
    .partitionKey("java")
    .filter(f -> f.gt("views", 100))
    .executeAll();

// Partial update
Post updated = posts.update(post, expr -> expr.set("title", "New Title"))
    .execute();

// Transaction with condition check
client.transactWrite()
    .put(posts, newPost)
    .conditionCheck(posts, "java", c -> c.eq("status", "ACTIVE"))
    .execute();
```

---

## Single-Table Design

DynamoDB Simplified is the first Java library with **first-class single-table design support**.

```java
@Entity(discriminator = "USER", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
public class User {
    private String pk;                   // auto-computed: "USER#user123"
    private String userId;

    @KeyComponent(component = "PK", position = 0)
    public String getUserId() { return userId; }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }
}

// Entity-aware table: keys auto-computed, discriminator auto-filtered
EntityTable<User> users = client.entityTable(User.class);
users.put(new User("user123"));  // pk auto-set to "USER#user123"

// Cross-entity queries
CrossEntityResult result = client.entityQuery("myapp")
    .partitionKey("USER#user123")
    .includeEntity(User.class)
    .includeEntity(Post.class)
    .execute();
```

See the [Single-Table Design Guide](docs/guides/single-table-design.md) for full documentation.

---

## Documentation

Full documentation is available at the [project site](https://hogwai.github.io/dynamodb-simplified/), including a [Quickstart guide](https://hogwai.github.io/dynamodb-simplified/quickstart/) with complete API coverage (CRUD, query, scan, batch, transactions, indexes, DDL, PartiQL, async).

The [API reference](https://hogwai.github.io/dynamodb-simplified/javadoc/index.html) is generated from Javadoc comments.

---

## Requirements

- Java 21+

---

## Project Structure

```
dynamodb-simplified/
├── dynamodb-simplified-core/    # the library (pure Java, zero framework deps)
│   ├── build.gradle.kts
│   └── src/main/java/com/hogwai/dynamodb/simplified/
│       ├── DynamoSimplifiedClient.java    # entry point, factory methods
│       ├── Table.java                     # fluent table operations
│       ├── async/                         # AsyncDynamoSimplifiedClient, AsyncTable, async builders
│       ├── builder/                       # QueryBuilder, PutBuilder, TransactWriteBuilder, etc.
│       ├── exception/                     # DynamoSimplifiedException, ConditionFailedException
│       ├── entity/                        # @Entity, @KeyComponent, EntityTable, EntityQueryBuilder
│       ├── expression/                    # FilterExpression, UpdateExpression, ProjectionExpression
│       ├── internal/                      # AttributeValueConverter
│       └── result/                        # PagedResult, TransactGetResults, BatchGetResults
│
├── build.gradle.kts              # root project
└── settings.gradle.kts
```

---

## Demo applications

Example applications using the library are available in a separate repository:

[dynamodb-simplified-demo](https://github.com/Hogwai/dynamodb-simplified-demo)

---

## License

This project is licensed under the [MIT license](LICENSE).