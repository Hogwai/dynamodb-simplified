/**
 * Asynchronous (CompletableFuture-based) API for DynamoDB Simplified.
 * <p>
 * Mirrors the synchronous API in {@link dev.hogwai.dynamodb.simplified} and
 * its sub-packages. Each builder has an async counterpart with identical
 * parameters and method names; return types differ ({@code T} sync vs
 * {@link java.util.concurrent.CompletableFuture}{@code <T>} async).
 * <p>
 * {@link dev.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient}
 * and {@link dev.hogwai.dynamodb.simplified.async.AsyncTable} are the
 * primary entry points for async operations.
 */
package dev.hogwai.dynamodb.simplified.async;
