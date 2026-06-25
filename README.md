# DynamoDB Simplified

> **⚠ Pre-release (v0.1.0)** — Not yet available on Maven Central. Clone and build locally to try it out.

A fluent wrapper for the AWS DynamoDB Enhanced Client that dramatically reduces boilerplate code and improves developer experience.

---

## Before & After

### Vanilla AWS SDK

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

### With DynamoDB Simplified

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

| Feature | Description |
|---------|-------------|
| **Fluent API** | Chain methods naturally with IntelliSense support |
| **Filter Expressions** | Simple methods for all DynamoDB operators |
| **Update Expressions** | SET, REMOVE, ADD, DELETE operations with a fluent API |
| **Server-side `size()`** | Filter by collection/string size without fetching data |
| **Projections** | Select only the attributes you need |
| **Pagination** | Built-in cursor-based pagination support |
| **Conditional Writes** | Put, Update, Delete with conditions |
| **Transactions** | TransactGet and TransactWrite with expression-based partial updates |
| **Batch Operations** | BatchGet and BatchWrite across tables |
| **Async API** | Complete async counterpart (CompletableFuture-based) |
| **DDL Operations** | Create, delete, describe, and check existence of tables |
| **PartiQL** | Passthrough PartiQL executeStatement for ad-hoc queries |
| **GSI/LSI Support** | Query and scan through secondary indexes |
| **Type Safety** | Leverages DynamoDB Enhanced Client's bean mapping |
| **Zero framework deps** | Pure Java, no Spring/Micronaut dependency in the core |

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

## Documentation

Full documentation is available at the [project site](https://hogwai.github.io/dynamodb-simplified/), including a [Quickstart guide](https://hogwai.github.io/dynamodb-simplified/quickstart/) with complete API coverage (CRUD, query, scan, batch, transactions, indexes, DDL, PartiQL, async).

The [API reference](javadoc/index.html) is generated from Javadoc comments.

---

## Requirements

- Java 17+ (tested with Java 25)
- AWS SDK v2 (2.29.52+)

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
│       ├── expression/                    # FilterExpression, UpdateExpression, ProjectionExpression
│       ├── internal/                      # AttributeValueConverter
│       └── result/                        # PagedResult, TransactGetResults, BatchGetResults
│
├── dynamodb-simplified-demo/     # example Micronaut application
│   └── build.gradle.kts
│
├── build.gradle.kts              # root project
└── settings.gradle.kts
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

Built on top of the excellent [AWS SDK for Java v2](https://github.com/aws/aws-sdk-java-v2).
