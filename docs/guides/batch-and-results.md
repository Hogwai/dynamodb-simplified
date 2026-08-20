# Batch and Results

Batch operations group reads or writes into DynamoDB batch requests. This guide covers same-table and cross-table
builders, their result objects, and the handling of unprocessed work. Imports and unrelated bean setup are omitted from
the snippets. The paginator behavior described here follows AWS SDK 2.46.17.

## DynamoDB limits

- A batch get accepts at most 100 keys in one request.
- A batch write accepts at most 25 put and delete requests combined.
- DynamoDB may return keys or write requests as unprocessed even when the request itself succeed (it can happen because
  of throttling or temporary capacity pressure).

Terminal sync and async same-table batch-get `execute()` validate the 100-key limit.
`AsyncBatchGetBuilder.executeWithPagination()` is different: it does not perform that explicit validation and consumes
only its first response page. Batch writes validate their 25-request limit. See
the [errors and retries guide](errors-and-retries.md) for retry boundaries.

## Same-table batch get

Start a batch get from a typed `Table<T>`. The terminal result is `BatchGetResult<T>`.

```java
BatchGetResult<Post> result = table.batchGet()
        .addKey("post-1", 123L)
        .addKey("post-2", 456L)
        .consistentRead(true)
        .execute();

List<Post> posts = result.items();
Map<String, KeysAndAttributes> remaining = result.unprocessedKeys();
boolean incomplete = result.hasUnprocessed();
```

`items()` contains the successfully mapped items. Without a projection, both same-table sync and async `execute()` use
the AWS Enhanced paginator. The paginator follows `unprocessedKeys()` from previous pages until its terminal state; it
is not a library-bounded application retry and no application-level backoff is guaranteed. Its final result is normally
empty of remaining keys.

With a projection, the same-table builder uses the low-level API. The sync projection path retries unprocessed keys with
a bounded backoff, while the async projection path makes one direct request. A direct or bounded-retry boundary can
therefore return remaining keys in `unprocessedKeys()`; an empty map means no keys remain at that boundary.

## Same-table batch write

`BatchWriteBuilder<T>` accepts puts and deletes for one table and returns a `BatchWriteResult`.

```java
BatchWriteResult result = table.batchWrite()
        .put(post1)
        .put(post2)
        .delete("post-3", 789L)
        .execute();

Map<String, List<WriteRequest>> remaining = result.unprocessedItems();
if(result.

hasUnprocessed()){
        log.

warn("Some writes remain unprocessed: {}",remaining);
}
```

For a collection of items, `table.putAll(items)` is a convenience method that returns `BatchWriteResult`.

## Cross-table batch operations

Use the client-level builders when keys or writes belong to multiple typed tables.

```java
CrossTableBatchGetResult result = client.batchGet()
        .addKey(posts, "post-1", 123L)
        .addKey(comments, "post-1", 456L)
        .execute();

List<Post> postsRead = result.getItems(posts);
Map<String, KeysAndAttributes> remaining = result.getUnprocessedKeys();
```

Cross-table batch writes use the corresponding table as an argument:

```java
CrossTableBatchWriteResult result = client.batchWrite()
        .put(posts, post)
        .delete(comments, "post-1", 456L)
        .execute();

Map<String, List<WriteRequest>> remaining = result.unprocessedItems();
```

The cross-table get result deserializes items with the `Table<T>` passed to `getItems`; its `getUnprocessedKeys()`
accessor exposes keys returned by the low-level path at its retry boundary. The synchronous cross-table builder uses
bounded retry with backoff. The asynchronous cross-table builder makes one direct low-level request and can return its
`unprocessedKeys` without automatic retry. For the async cross-table batch-get builder, keys are added with
`AsyncTable<T>`, but the returned `CrossTableBatchGetResult.getItems(...)` method still requires a matching synchronous
`Table<T>` reference. There is no equivalent `getItems(AsyncTable<T>)` overload. The write result groups unprocessed
requests by table name.

```java
CompletableFuture<CrossTableBatchGetResult> future = asyncClient.batchGet()
        .addKey(asyncPosts, "post-1", 123L)
        .execute();

CrossTableBatchGetResult asyncResult = future.join();
Map<String, KeysAndAttributes> asyncRemaining = asyncResult.getUnprocessedKeys();
Table<Post> syncPosts = client.table("posts", Post.class);
List<Post> asyncPostsRead = asyncResult.getItems(syncPosts);
```

## Result interfaces and pagination

`BatchGetResult`, `BatchWriteResult`, `CrossTableBatchGetResult`, and `CrossTableBatchWriteResult` expose
operation-specific accessors for returned items or unprocessed work.

`PagedResult<T>` is the result of a paginated query or scan, not a batch result:

```java
PagedResult<Post> page = table.query()
        .partitionKey("post-1")
        .limit(10)
        .executeWithPagination();

List<Post> pageItems = page.items();
Map<String, AttributeValue> nextKey = page.lastEvaluatedKey();
if(page.

hasMorePages()){
PagedResult<Post> nextPage = table.query()
        .partitionKey("post-1")
        .startFrom(nextKey)
        .executeWithPagination();
}
```

`lastEvaluatedKey()` is `null` or empty on the final page.
`limit(10)` limits items evaluated before a filter, so the returned page can contain fewer items than the limit. A
filter can also produce an empty page with a continuation key.

## Retry behavior

The no-projection same-table sync and async batch-get `execute()` paths use the AWS Enhanced paginator. It follows
unprocessed keys between pages until its terminal state; this is not a library-bounded retry loop, and no application
backoff is guaranteed. The final paginator result is normally without remaining keys.

The sync same-table projection path and sync cross-table low-level path retry unprocessed keys for up to three attempts
with exponential backoff. Async projection and async cross-table batch-get paths make one direct request with no
automatic retry. If a direct or bounded-retry boundary returns remaining work, it is exposed by the result object so the
application can decide what to do next. Async batch-write builders separately use bounded retry with scheduled delays.
