# DynamoDB Simplified

A fluent wrapper for AWS DynamoDB Enhanced Client that dramatically reduces boilerplate code and improves developer experience.

---

## Purpose

The AWS DynamoDB SDK is powerful but notoriously verbose. Simple operations require dozens of lines of code with complex builder patterns, expression attribute names, and expression attribute values.

**DynamoDB Simplified** provides a fluent, intuitive API that lets you focus on *what* you want to do, not *how* to do it.

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
    .execute();
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
| **Type Safety** | Leverages DynamoDB Enhanced Client's bean mapping |
| **Zero framework deps** | Pure Java, no Spring/Micronaut dependency in the core |

---

## Quick Start

### 1. Add the dependency

```kotlin
// Gradle
dependencies {
    implementation("com.hogwai:dynamodb-simplified-core:0.1")
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>com.hogwai</groupId>
    <artifactId>dynamodb-simplified-core</artifactId>
    <version>0.1</version>
</dependency>
```

### 2. Define your entity

```java
@DynamoDbBean
public class Post {
    private String id;
    private String subreddit;
    private Long createdUtc;
    private String author;
    private String title;
    private Set<String> keywords;

    @DynamoDbPartitionKey
    public String getSubreddit() { return subreddit; }

    @DynamoDbSortKey
    public String getId() { return id; }

    // getters and setters...
}
```

### 3. Create the client

```java
// Default client (uses default credentials chain)
DynamoSimplifiedClient client = DynamoSimplifiedClient.create();

// With a custom DynamoDbClient (for DynamoDB Local, specific config, etc.)
DynamoSimplifiedClient client = DynamoSimplifiedClient.create(dynamoDbClient);

// Get table operations
Table<Post> posts = client.table("posts", Post.class);
```

### 4. Start querying

```java
// Simple query
List<Post> results = posts.query()
    .partitionKey("java")
    .execute();

// With filters
List<Post> filtered = posts.query()
    .partitionKey("java")
    .filter(f -> f.eq("author", "john"))
    .execute();

// Count matching items (iterates pages, sums server-side counts)
long count = posts.query()
    .partitionKey("java")
    .filter(f -> f.sizeGt("keywords", 3))
    .count();
```

---

## API Reference

### Query Operations

```java
// Partition key only
table.query().partitionKey("java")

// Partition key + sort key equals
table.query().partitionKeyAndSortKeyEquals("java", "post-123")

// Sort key begins with
table.query().partitionKeyAndSortKeyBeginsWith("java", "2024-")

// Sort key between
table.query().partitionKeyAndSortKeyBetween("java", "2024-01", "2024-12")

// Sort key comparisons
table.query().partitionKeyAndSortKeyGreaterThan("java", "2024-01-01")
table.query().partitionKeyAndSortKeyLessThanOrEqual("java", "2024-12-31")

// Scan index
table.query().useIndex("AuthorIndex")
```

### Filter Expressions

```java
table.query().partitionKey("java").filter(f -> f
    // Comparisons
    .eq("status", "ACTIVE")           // =
    .ne("status", "DELETED")          // <>
    .gt("score", 100)                 // >
    .ge("score", 100)                 // >=
    .lt("score", 100)                 // <
    .le("score", 100)                 // <=

    // Range
    .between("score", 10, 100)

    // List membership
    .in("status", "PENDING", "ACTIVE", "REVIEW")

    // String operations
    .beginsWith("title", "How to")
    .contains("title", "Java")

    // Collection contains
    .contains("keywords", "programming")

    // Attribute existence
    .exists("metadata")
    .notExists("deletedAt")

    // Size operations (server-side)
    .sizeEq("keywords", 5)
    .sizeGt("keywords", 3)
    .sizeLt("keywords", 10)
    .sizeGe("keywords", 1)
    .sizeLe("keywords", 20)
    .sizeBetween("keywords", 1, 10)

    // Logical operators
    .and()
    .or()

    // Grouping (parentheses)
    .group(FilterExpression.builder()
        .eq("status", "A")
        .or()
        .eq("status", "B"))
)
```

### Update Expressions

```java
table.update(item)
    .update(u -> u
        .set("title", "New Title")
        .set("score", 42)
        .increment("views", 1)
        .appendToList("tags", List.of("java", "dynamodb"))
        .remove("temporaryField")
        .addToSet("categories", Set.of("tech"))
    )
    .condition(c -> c.eq("author", "john"))
    .execute();
```

### Projections

```java
table.query()
    .partitionKey("java")
    .project("id", "title", "author")
    .execute();
```

### Sorting & Limiting

```java
table.query()
    .partitionKey("java")
    .descending()              // or .ascending()
    .limit(20)
    .execute();
```

### Pagination

```java
// Get first page
PagedResult<Post> page1 = table.query()
    .partitionKey("java")
    .limit(20)
    .executeWithPagination();

// Get next page
PagedResult<Post> page2 = table.query()
    .partitionKey("java")
    .limit(20)
    .startFrom(page1.getLastEvaluatedKey())
    .executeWithPagination();

// Check for more pages
if (page2.hasMorePages()) {
    // continue...
}
```

### Scan Operations

```java
// Scan with filter
List<Post> allByAuthor = table.scan()
    .filter(f -> f.eq("author", "john"))
    .execute();

// Parallel scan
List<Post> segment = table.scan()
    .filter(f -> f.gt("createdUtc", timestamp))
    .parallelScan(4, 0)  // 4 segments, this is segment 0
    .execute();

// Scan with count
long count = table.scan()
    .filter(f -> f.exists("metadata"))
    .count();
```

### CRUD Operations

```java
// Get item directly (returns Optional<T>)
Optional<Post> post = table.getItem("java", "post-123");

// Get item with projection (uses GetItemBuilder)
Optional<Post> post = table.get("java", "post-123")
    .project("id", "title")
    .execute();

// Put
table.putItem(post);

// Put if not exists
table.put(post)
    .onlyIfNotExists("id")
    .execute();

// Put with condition
table.put(post)
    .condition(c -> c
        .notExists("id")
        .or()
        .lt("createdUtc", oldTimestamp))
    .execute();

// Update (full item replacement)
Post updated = table.updateItem(post);

// Update with partial expression
Post updated = table.update(post)
    .update(u -> u.set("title", "New Title"))
    .condition(c -> c.eq("author", expectedAuthor))
    .execute();

// Delete directly
table.deleteItem("java", "post-123");

// Delete with condition
table.delete("java", "post-123")
    .condition(c -> c.eq("author", requestingUser))
    .execute();
```

---

## Using with Dependency Injection

### Micronaut

```java
@Factory
public class DynamoConfig {

    @Singleton
    public DynamoSimplifiedClient dynamoSimplifiedClient(DynamoDbClient dynamoDbClient) {
        return DynamoSimplifiedClient.create(dynamoDbClient);
    }
}

@Singleton
public class PostRepository {

    private final Table<Post> table;

    public PostRepository(DynamoSimplifiedClient client) {
        this.table = client.table("posts", Post.class);
    }
}
```

### Spring Boot

```java
@Configuration
public class DynamoConfig {

    @Bean
    public DynamoSimplifiedClient dynamoSimplifiedClient(DynamoDbClient dynamoDbClient) {
        return DynamoSimplifiedClient.create(dynamoDbClient);
    }
}

@Repository
public class PostRepository {

    private final Table<Post> table;

    public PostRepository(DynamoSimplifiedClient client) {
        this.table = client.table("posts", Post.class);
    }
}
```

---

## Requirements

- Java 17+
- AWS SDK v2 (2.20.0+)

---

## Project Structure

```
dynamodb-simplified/
├── dynamodb-simplified-core/    # the library (pure Java, zero framework deps)
│   ├── build.gradle.kts
│   └── src/main/java/com/hogwai/dynamodb/simplified/
│       ├── DynamoSimplifiedClient.java    # entry point, factory methods
│       ├── Table.java                     # fluent table operations
│       ├── builder/                       # QueryBuilder, PutBuilder, etc.
│       ├── exception/                     # DynamoSimplifiedException, ConditionFailedException
│       ├── expression/                    # FilterExpression, UpdateExpression, ProjectionExpression
│       ├── internal/                      # AttributeValueConverter
│       └── result/                        # PagedResult
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
