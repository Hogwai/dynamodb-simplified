/**
 * Asynchronous (CompletableFuture-based) API for DynamoDB Simplified.
 * <p>
 * Mirrors the synchronous API in {@link com.hogwai.dynamodb.simplified} and
 * its sub-packages. Each builder has an async counterpart with identical
 * parameters and method names; return types differ ({@code T} sync vs
 * {@link java.util.concurrent.CompletableFuture}{@code <T>} async).
 * <p>
 * {@link com.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient}
 * and {@link com.hogwai.dynamodb.simplified.async.AsyncTable} are the
 * primary entry points for async operations.
 */
package com.hogwai.dynamodb.simplified.async;
