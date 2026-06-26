# DynamoDB Simplified

A fluent wrapper for the AWS DynamoDB Enhanced Client that dramatically reduces boilerplate code and improves developer experience.

## Why?

The AWS DynamoDB SDK is powerful but verbose. Simple operations require dozens of lines of code with manual expression attribute names and values.

**DynamoDB Simplified** provides a fluent, intuitive API that lets you focus on *what* you want to do, not *how* to do it.

### Vanilla SDK (40+ lines)

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

### With DynamoDB Simplified (5 lines)

```java
table.query()
    .partitionKey("pk1")
    .filter(f -> f.eq("status", "ACTIVE"))
    .executeAll()
    .forEach(item -> { ... });
```

## Features

- **Fluent builder API**: every operation reads as a sentence
- **Zero-boilerplate expressions**: `f.eq("status", "active")` instead of `Expression` builders
- **Sync + Async**: `DynamoSimplifiedClient` and `AsyncDynamoSimplifiedClient`, same API
- **Transactions**: `transactWrite()` with put, update, delete, conditionCheck, and expression-based partial updates
- **Batch operations**: `batchGet()` and `batchWrite()` with consistent reads
- **DDL**: `createTable()`, `deleteTable()`, `describeTable()`, `tableExists()`
- **GSI / LSI**: `table.index("name").query()` with full fluent API
- **Single-table design**: `@Entity`/`@KeyComponent` annotations, auto-computed composite keys, cross-entity queries
- **Optimistic locking**: `@Version` annotation with automatic version checking
- **TTL management**: enable/disable/describe TTL with `UpdateExpression.ttl(Duration)`
- **Consumed capacity**: every result type exposes consumed capacity
- **Automatic batch retry**: unprocessed keys automatically retried with exponential backoff
- **PartiQL**: `client.executeStatement()` for raw SQL-like queries
- **Low-level fallback**: when the Enhanced Client lacks a feature (update expressions, returnValues), the library delegates to the low-level DynamoDB client transparently
- **No framework dependencies**: pure Java, works with any stack

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

See the [Quickstart](quickstart.md) guide for a complete walkthrough.

Looking for single-table design? See the [Single-Table Design Guide](guides/single-table-design.md).

## API Reference

The [full API documentation](javadoc/index.html) is generated from Javadoc comments.
