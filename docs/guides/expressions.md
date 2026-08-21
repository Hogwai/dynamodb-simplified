# Expressions

This guide covers the expression builders used by queries, scans, conditional writes, updates, and projections.
The Java snippets omit imports and unrelated bean setup unless an import is part of the API being demonstrated.

## Filters and conditions

`FilterExpression` is used with `query()` and `scan()`.
A filter is evaluated after DynamoDB reads candidate items.
`ConditionExpression` uses the same comparison vocabulary, but is evaluated before a put, update, or delete is accepted.

```java
List<Post> posts = table.query()
        .partitionKey("post-1")
        .filter(f -> f.eq("status", "ACTIVE")
                .and()
                .gt("views", 100))
        .executeAll();

table.put(post)
  .condition(c -> c.notExists("id"))
  .execute();
```

Logical operators are explicit.
Insert `.and()`, `.or()`, or `.not()` between conditions; two predicate calls are not implicitly joined.

```java
List<Post> posts = table.scan()
        .filter(f -> f.eq("status", "ACTIVE")
                .or()
                .eq("status", "QUEUED"))
        .executeAll();

table.delete("post-1", 123L)
  .condition(c -> c.not().exists("locked"))
  .execute();
```

Use `group(...)` when precedence matters.
It accepts another `FilterExpression` and adds parentheses around it.

```java
FilterExpression status = FilterExpression.builder()
        .eq("status", "ACTIVE")
        .or()
        .eq("status", "QUEUED");

List<Post> posts = table.query()
        .partitionKey("post-1")
        .filter(f -> f.group(status)
                .and()
                .gt("views", 100))
        .executeAll();
```

## Operators

The comparison methods are `eq`, `ne`, `lt`, `le`, `gt`, and `ge`.

Each comparison is a separate builder method:

| Method | Example                                   |
|--------|-------------------------------------------|
| `eq`   | `.filter(f -> f.eq("status", "ACTIVE"))`  |
| `ne`   | `.filter(f -> f.ne("status", "DELETED"))` |
| `lt`   | `.filter(f -> f.lt("views", 100))`        |
| `le`   | `.filter(f -> f.le("views", 100))`        |
| `gt`   | `.filter(f -> f.gt("views", 100))`        |
| `ge`   | `.filter(f -> f.ge("views", 100))`        |

For multiple comparisons, add the logical operator explicitly:

```java
List<Post> popular = table.query()
        .partitionKey("post-1")
        .filter(f -> f.ge("views", 100)
                .and()
                .ne("status", "DELETED"))
        .executeAll();
```

```java
table.query()
  .partitionKey("post-1")
  .filter(f -> f.between("createdAt", 1000L, 2000L)
  .and()
  .in("status", "ACTIVE", "QUEUED")
  .and()
  .contains("tags", "java")
  .and()
  .beginsWith("title", "DynamoDB"))
  .executeAll();
```

Existence and type checks are available through `exists`, `notExists`, and `attributeType`.
The type is `FilterExpression.AttributeType`, with values such as `STRING`, `NUMBER`, `MAP`, `LIST`, `STRING_SET`, and `BOOLEAN`.

```java
table.scan()
  .filter(f -> f.exists("metadata")
  .and()
  .attributeType("metadata", FilterExpression.AttributeType.MAP))
  .executeAll();
```

Nested paths use `nestedEq`.
Dot-separated paths and list indexes are mapped to expression attribute names.

```java
table.query()
  .partitionKey("post-1")
  .filter(f -> f.nestedEq("author.address.city", "Paris"))
  .executeAll();
```

## Server-side `size()`

The six server-side size operators are `sizeEq`, `sizeLt`, `sizeLe`, `sizeGt`, `sizeGe`, and `sizeBetween`.

```java
List<Post> results = table.query()
        .partitionKey("post-1")
        .filter(f -> f.sizeGe("tags", 2)
                .and()
                .sizeLe("tags", 10))
        .executeAll();

List<Post> shortTitles = table.scan()
        .filter(f -> f.sizeBetween("title", 5, 80))
        .executeAll();
```

DynamoDB evaluates `size(attribute)` server-side after reading candidate items.
It does not fetch the collection or string to the client just to calculate its size, and the filter does not reduce consumed read capacity.

## Update expressions

Update expressions support the DynamoDB `SET`, `REMOVE`, `ADD`, and `DELETE` clauses.

```java
Optional<Post> updated = table.update(post, expression -> expression
                .set("title", "New title")
                .setIfNotExists("summary", "")
                .remove("legacyField")
                .addNumber("views", 1)
                .addToSet("tags", Set.of("java"))
                .deleteFromSet("blockedTags", Set.of("obsolete")))
        .execute();
```

Numeric arithmetic is also available through `increment` and `decrement`:

```java
table.update(post, expression -> expression
  .increment("views", 1)
  .decrement("remainingRetries", 1))
  .execute();
```

For lists, use `appendToList`, `prependToList`, `setListElement`, and `removeListElement`.
Nested paths use `setNested`.

```java
table.update(post, expression -> expression
  .appendToList("comments", List.of("first"))
  .prependToList("pinnedComments", List.of("pinned"))
  .setListElement("commentPreview", 0, "updated")
  .removeListElement("oldComments", 1)
  .setNested("metadata.reviewed", true))
  .execute();
```

`ttl(attribute, duration)` stores an epoch timestamp calculated from the current time and the supplied `Duration`.

```java
table.update(post, expression -> expression
  .set("status", "ARCHIVED")
  .ttl("expiresAt", Duration.ofDays(90)))
  .execute();
```

## Projections

Projections restrict the attributes returned by a query, scan, or get operation.
Use string arguments for top-level attributes, or the projection builder for nested paths and list elements.

```java
List<Post> posts = table.query()
        .partitionKey("post-1")
        .project("id", "title")
        .executeAll();

Optional<Post> post = table.get("post-1", 123L)
        .project(p -> p.include("id")
                .includeNested("author.name")
                .includeListElement("tags", 0))
        .execute();
```

## DynamoDB constraints

- Filter expressions do not change the key condition.
  Put partition and sort key restrictions in `partitionKey(...)` and its sort-key variants.
- Filters and conditions cannot be used interchangeably at the operation level: filters apply after reads, while conditions gate writes.
- DynamoDB reserves some attribute names.
  The builders map names and values to expression placeholders, but the expression must still obey DynamoDB's supported data types and path rules.
- `size()` is supported only for DynamoDB values for which DynamoDB defines a size, such as strings, lists, maps, and sets.
- Updates cannot set an attribute to `null` with `set`; use `remove(...)` for a missing attribute instead.
