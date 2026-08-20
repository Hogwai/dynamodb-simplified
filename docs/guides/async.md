# Async API

The async API uses the AWS SDK async clients and returns `CompletableFuture` for ordinary operation terminals. The async
builders are broadly symmetrical with the synchronous builders, but terminal types and streaming differ by builder.

## Create an async client and table

Use the default factory or wrap an already configured `DynamoDbAsyncClient`.

```java
AsyncDynamoSimplifiedClient asyncClient = AsyncDynamoSimplifiedClient.create();
AsyncTable<Post> asyncTable = asyncClient.table("posts", Post.class);
```

```java
DynamoDbAsyncClient rawClient = DynamoDbAsyncClient.builder()
        .region(Region.EU_WEST_1)
        .endpointOverride(URI.create("http://localhost:8000"))
        .build();
AsyncDynamoSimplifiedClient asyncClient = AsyncDynamoSimplifiedClient.create(rawClient);
```

Both clients are `AutoCloseable`. Close the wrapper when the application no longer needs the underlying SDK client.

## Sync-to-async mapping

| Synchronous terminal      | Async terminal            | Async result                                                       |
|---------------------------|---------------------------|--------------------------------------------------------------------|
| `execute()`               | `execute()`               | `CompletableFuture` parameterized by the operation-specific result |
| `executeAll()`            | `executeAll()`            | `CompletableFuture<List<T>>`                                       |
| `executeWithPagination()` | `executeWithPagination()` | `CompletableFuture<PagedResult<T>>`                                |
| `executeAndGetFirst()`    | `executeAndGetFirst()`    | `CompletableFuture<Optional<T>>`                                   |
| `count()`                 | `count()`                 | `CompletableFuture<Long>`                                          |
| query `executeStream()`   | query `streamResults()`   | `SdkPublisher<T>`                                                  |
| scan `executeStream()`    | scan `executeStream()`    | `CompletableFuture<SdkPublisher<T>>`                               |

For example, async get and query operations compose as futures:

```java
CompletableFuture<Optional<Post>> post = asyncTable.get("post-1", 123L).execute();

CompletableFuture<List<Post>> posts = asyncTable.query()
        .partitionKey("post-1")
        .executeAll();

CompletableFuture<PagedResult<Post>> page = asyncTable.query()
        .partitionKey("post-1")
        .limit(10)
        .executeWithPagination();
```

`executeAll()` eagerly aggregates all pages into a list. In the current async implementation, `executeWithPagination()`
also collects pages before returning the first `PagedResult`. It should not be treated as a memory-reduction primitive.
For very large result sets, prefer the query or scan publisher.

## Composing futures and handling failures

Use `thenApply`, `thenCompose`, `handle`, `whenComplete`, or `exceptionally` instead of blocking a request thread.

```java
CompletableFuture<String> title = asyncTable.get("post-1", 123L)
        .execute()
        .thenApply(optional -> optional.map(Post::getTitle).orElse("missing"));

CompletableFuture<List<Post>> published = asyncTable.query()
        .partitionKey("post-1")
        .filter(f -> f.eq("status", "PUBLISHED"))
        .executeAll()
        .exceptionally(error -> {
            // Collected scan paths use the library mapper.
            // Query/page paths and publishers may expose the SDK exception directly.
            log.error("Query failed", error);
            return List.of();
        });
```

`join()` and `get()` expose asynchronous failures through `CompletionException` (or `ExecutionException` for `get()`).
Inspect the cause: collected scan paths use the library exception hierarchy, while query/page paths and publishers may
expose the original SDK exception. Do not assume one exception type for every async terminal.

## Async streaming

Async query streaming is a direct reactive publisher:

```java
SdkPublisher<Post> queryPublisher = asyncTable.query()
        .partitionKey("post-1")
        .streamResults();

queryPublisher.

subscribe(post ->

process(post));
```

Async scan streaming returns a future that completes with the publisher:

```java
CompletableFuture<SdkPublisher<Post>> scanPublisher = asyncTable.scan()
        .filter(f -> f.gt("views", 100))
        .executeStream();

scanPublisher.

thenAccept(publisher ->publisher.

subscribe(post ->

process(post)));
```

The publisher applies backpressure through the Reactive Streams contract. Do not call the query method
`executeStream()`. query uses `streamResults()` in the async API, while scan retains `executeStream()`.

## Async batch operations

Same-table async batch operations return typed result futures:

```java
CompletableFuture<BatchGetResult<Post>> batch = asyncTable.batchGet()
        .addKey("post-1", 123L)
        .addKey("post-2", 456L)
        .execute();

CompletableFuture<BatchWriteResult> writes = asyncTable.batchWrite()
        .put(post)
        .delete("post-2", 456L)
        .execute();
```

Cross-table operations are created from the async client. They accept `AsyncTable` instances and return cross-table
result types:

```java
CompletableFuture<CrossTableBatchGetResult> crossGet = asyncClient.batchGet()
        .addKey(asyncTable, "post-1", 123L)
        .execute();

CompletableFuture<CrossTableBatchWriteResult> crossWrite = asyncClient.batchWrite()
        .put(asyncTable, post)
        .delete(asyncTable, "post-2", 456L)
        .execute();
```

When the async cross-table batch-get completes, it is a direct low-level request meaning that its result exposes both
mapped items and any `unprocessedKeys` returned by that request. It does not retry unprocessed keys automatically:

```java
CrossTableBatchGetResult crossResult = crossGet.join();
Map<String, KeysAndAttributes> remaining = crossResult.getUnprocessedKeys();
```

The result's `getItems(...)` method is typed with the synchronous `Table<T>` type, not `AsyncTable<T>`. If item
deserialization is needed, pass a matching sync table reference. The returned cross-table async-get result does not
provide a typed `getItems` accessor that accepts `AsyncTable<T>`.

```java
// Create a synchronous reference for result deserialization.
try(DynamoSimplifiedClient syncClient = DynamoSimplifiedClient.create()){
Table<Post> syncPosts = syncClient.table("posts", Post.class);
List<Post> postsRead = crossResult.getItems(syncPosts);
}
```

Batch reads accept at most 100 keys per request and batch writes accept at most 25 puts and deletes combined. Same-table
`execute()` without a projection uses the AWS Enhanced paginator. It follows `unprocessedKeys()` from earlier pages
until the paginator reaches its terminal state, rather than applying a library-bounded retry loop. No application-level
backoff is guaranteed for that paginator path, and its final result is normally empty of remaining unprocessed keys.

Same-table projection uses a low-level request. Its synchronous builder retries unprocessed keys with a bounded backoff:
its asynchronous builder makes one direct request and can return `unprocessedKeys`. The synchronous cross-table builder
also uses bounded retry, while the asynchronous cross-table builder is a direct low-level request with no automatic
retry. Async batch-write builders remain bounded and use scheduled delays. See
the [batch and results guide](batch-and-results.md) and the [errors and retries guide](errors-and-retries.md) for the
result and retry boundaries.

`AsyncBatchGetBuilder.executeWithPagination()` is a separate first-page operation: it consumes only the first Enhanced
paginator page and returns a `PagedResult`, which has no field for unprocessed keys. It is not complete batch pagination
or retry handling. Unlike terminal `execute()`, this method does not perform the explicit 100-key validation meaning
that callers should not infer that the terminal limit is enforced here. Configure projections only with `execute()`
because this terminal rejects projections explicitly.
