# DynamoDB Simplified

[![Maven Central](https://img.shields.io/maven-central/v/dev.hogwai/dynamodb-simplified-core)](https://central.sonatype.com/artifact/dev.hogwai/dynamodb-simplified-core)

A fluent wrapper for the AWS DynamoDB Enhanced Client with typed builders for common DynamoDB operations.

## Why?

The AWS DynamoDB SDK exposes request and expression details explicitly, including expression attribute names and values, which can make query setup verbose.

**DynamoDB Simplified** provides fluent builders for common DynamoDB operations while keeping the underlying AWS SDK model visible.

### dynamodb-enhanced

```java
Map<String, String> expressionNames = new HashMap<>();
expressionNames.put("#status", "status");
Map<String, AttributeValue> expressionValues = new HashMap<>();
expressionValues.put(":statusVal", AttributeValue.builder().s("ACTIVE").build());

QueryEnhancedRequest request = QueryEnhancedRequest.builder()
    .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue("pk1")))
    .filterExpression(Expression.builder()
        .expression("#status = :statusVal")
        .expressionNames(expressionNames)
        .expressionValues(expressionValues)
        .build())
    .build();

table.query(request).items().forEach(item -> { ... });
```

### dynamodb-simplified

```java
table.query()
    .partitionKey("pk1")
    .filter(f -> f.eq("status", "ACTIVE"))
    .executeAll()
    .forEach(item -> { ... });
```

## Features

- **Fluent builder API**: chain operation-specific methods before each terminal
- **Expression helpers**: `f.eq("status", "active")` instead of manually assembling expression maps
- **Sync + Async**: related builders in `DynamoSimplifiedClient` and `AsyncDynamoSimplifiedClient`, with different terminal types where needed
- **Transactions**: `transactWrite()` with put, update, delete, conditionCheck, and expression-based partial updates
- **Batch operations**: `batchGet()` supports optional consistent reads; `batchWrite()` handles puts and deletes
- **DDL**: `create()`, `delete()`, `describe()`, `exists()`
- **GSI / LSI**: `table.index("name").query()` with the query builder options supported for indexes
- **Single-table design**: `@Entity`/`@KeyComponent` annotations, auto-computed composite keys, cross-entity queries
- **Version support**: `@Version` fields can be detected and incremented on the Java object; strict compare-and-set requires an explicit condition
- **TTL management**: table configuration with `enableTtl("expiresAt")`, `disableTtl("expiresAt")`, `describeTtl()`; update expressions write expiration values with `update(item, u -> u.ttl("expiresAt", Duration.ofDays(90))).execute()`
- **Batch get behavior**: same-table `execute()` without projection uses the AWS Enhanced paginator; bounded retry applies to sync batch-write and synchronous low-level batch-get paths, while async low-level batch-get paths are direct requests
- **PartiQL**: `client.executeStatement()` for raw SQL-like queries
- **Low-level fallback**: when the Enhanced Client lacks a feature (update expressions, returnValues), the library delegates to the low-level DynamoDB client for that operation
- **No framework dependencies**: pure Java, works with any stack

## Server-side `size()`

```java
List<Post> posts = table.query()
        .partitionKey("java")
        .filter(f -> f.sizeGt("keywords", 3))
        .executeAll();
```

DynamoDB applies the filter using `size(attribute)`, without client-side size calculation.
The filter is evaluated after reading items.

## Quick example

```java
// Create client
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();
Table<MyItem> table = client.table("my-table", MyItem.class);

// Put
table.put(myItem).execute();

// Get
MyItem found = table.getItem("pk1", "sk1").orElse(null);

// Query with filter
List<MyItem> results = table.query()
    .partitionKey("pk1")
    .filter(f -> f.gt("views", 100))
    .executeAll();

// Transaction with partial update
client.transactWrite()
    .put(table, newItem)
    .update(table, existingItem, expr -> expr.set("views", 150))
    .execute();
```

See the [Quickstart](quickstart.md) guide for an overview of the main operations.

Looking for single-table design?
See the [Single-Table Design Guide](guides/single-table-design.md).

## API Reference

The [full API documentation](javadoc/index.html) is generated from Javadoc comments.
