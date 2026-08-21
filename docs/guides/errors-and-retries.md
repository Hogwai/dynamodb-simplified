# Errors and Retries

DynamoDB Simplified maps common DynamoDB failures to a small exception hierarchy.
Batch-get retry behavior depends on the execution path: the no-projection same-table `execute()` paths use the AWS Enhanced paginator, whereas low-level paths may use bounded library retries or a single direct request.
Async batch-write work uses bounded retries with scheduled delays.
None of these paths turns every operation into an application-level retry loop.
Imports and unrelated setup are omitted from the snippets.

## Table of Contents

- [Exception hierarchy](#exception-hierarchy)
- [Conditional failures](#conditional-failures)
- [Transaction failures](#transaction-failures)
- [Async failures](#async-failures)
- [Retry boundaries](#retry-boundaries)
- [Async batch-write delays](#async-batch-write-delays)

## Exception hierarchy

The public exception types are organized as follows:

```text
DynamoSimplifiedException
├── OperationFailedException
├── ConditionFailedException
├── TransactionFailedException
└── ResourceNotFoundException
```

`DynamoSimplifiedException` is the common unchecked base type.

| Exception                    | Meaning                                                                                             |
|------------------------------|-----------------------------------------------------------------------------------------------------|
| `OperationFailedException`   | An SDK/DynamoDB service failure for an operation such as query, put, update, delete, or batch work. |
| `ConditionFailedException`   | A put, update, or delete condition evaluated to false.                                              |
| `TransactionFailedException` | DynamoDB canceled a transaction.                                                                    |
| `ResourceNotFoundException`  | The requested table or other DynamoDB resource was not found.                                       |

`OperationFailedException` retains the SDK exception as its cause and includes the operation and table context in its message when a table is known.

```java
try {
    table.query().partitionKey("post-1").executeAll();
} catch (OperationFailedException error) {
    logger.error("{}", error.getMessage(), error);
    Throwable sdkFailure = error.getCause();
}
```

## Conditional failures

Conditions are checked by DynamoDB before the write.
A failed condition is reported as `ConditionFailedException`, rather than as a generic operation failure.

```java
try {
    table.put(post).condition(c -> c.notExists("id")).execute();
} catch (ConditionFailedException error){
    // The item already exists, or the condition was otherwise false
    logger.info("Write was rejected by its condition");
}
```

The same exception applies to conditional update and delete operations.
Filters are different: a filter removes items after a read and does not cause a condition failure.

## Transaction failures

Transactions are all-or-nothing.
When DynamoDB cancels one, the mapped `TransactionFailedException` exposes one cancellation reason per transaction operation through `getCancellationReasons()` and `getCancellationReason(index)`.
A `null` reason means that the corresponding operation had no cancellation reason.

```java
try {
   client.transactWrite()
    .put(table, newPost)
    .conditionCheck(table, "post-1", 123L, c -> c.eq("status", "OPEN"))
    .execute();
} catch (TransactionFailedException error) {
    List<String> reasons = error.getCancellationReasons();
    logger.warn("Transaction canceled: {}", reasons);
}
```

Do not assume that the first non-null reason is the only relevant failure; inspect the list in transaction operation order.

## Async failures

Async builders complete their futures exceptionally.
Collected scan paths map DynamoDB SDK failures through the library exception hierarchy, while query/page paths and publishers may expose the original SDK exception.
If code calls `join()`, Java wraps the failure in `CompletionException`; inspect `getCause()` and do not assume one library exception type for every async terminal.

```java
CompletableFuture<List<Post>> future = asyncTable.query()
        .partitionKey("post-1")
        .executeAll();

future.exceptionally(error -> {
Throwable cause = error instanceof CompletionException
        ? error.getCause()
        : error;
    logger.error("Async query failed", cause);
    return List.of();
});
```

Prefer `exceptionally`, `handle`, or `whenComplete` at the future boundary so the caller thread is not blocked while the operation is in flight.

## Retry boundaries

There are three different retry layers and several batch-get boundaries:
- **AWS SDK retries** apply to SDK requests according to the configured SDK retry policy.
   They concern service request failures and throttling.
- **Enhanced batch-get pagination** is used by same-table sync and async `execute()` without a projection.
   The AWS Enhanced paginator requests the `unprocessedKeys()` from earlier pages until its terminal state.
   This is not a library-bounded application retry, and no application-level backoff is guaranteed.
   The final paginator result is normally without remaining keys.
- **Library low-level batch retries** apply specifically to DynamoDB's `unprocessedKeys` and `unprocessedItems` responses.
   Same-table synchronous projection and synchronous cross-table batch-get make up to three retry attempts with exponential backoff.
   Async projection and async cross-table batch-get make one direct request and can expose returned `unprocessedKeys`.
   Async batch-write builders retry with scheduled delays and a bounded policy.
- **Application retries** are a decision to repeat a whole operation.
   The library does not automatically repeat an arbitrary query, put, update, delete, or transaction after a mapped exception.

Results from a direct request or a bounded low-level retry boundary can still report unprocessed keys or items.
Do not infer that a key remains in the result after the Enhanced paginator has processed it; its terminal result is normally without remaining keys.
Applications that retry a whole operation should consider idempotency, conditional writes, and whether a partial batch result has already been applied.

## Async batch-write delays

Async batch-write builders use scheduled asynchronous delays between retry attempts.
They do not use `Thread.sleep` on the caller thread, so the returned `CompletableFuture` remains non-blocking while unprocessed items wait for their next attempt.
This applies to both same-table and cross-table async batch writes.

```java
CompletableFuture<BatchWriteResult> write = asyncTable.batchWrite()
        .put(post)
        .execute();

write.thenAccept(result -> {
   if (result.hasUnprocessed()) {
    logger.warn("Batch completed with unprocessed items");
   }
});
```

If a retryable async batch-write request ultimately fails with an SDK exception, the future completes exceptionally with the mapped library exception.
