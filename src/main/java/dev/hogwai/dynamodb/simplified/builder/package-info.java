/**
 * Fluent builder classes for synchronous DynamoDB operations.
 * <p>
 * Each builder follows a consistent fluent pattern: obtain from
 * {@link dev.hogwai.dynamodb.simplified.Table} or
 * {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient}, configure
 * via chained methods, and call a terminal method ({@code execute()},
 * {@code executeAll()}, etc.}) to run the operation.
 * <p>
 * Builders that require low-level SDK features (updates with
 * {@code returnValues}, expression-based updates in transactions)
 * transparently fall back to {@link software.amazon.awssdk.services.dynamodb.DynamoDbClient}
 * at execution time.
 */
package dev.hogwai.dynamodb.simplified.builder;
