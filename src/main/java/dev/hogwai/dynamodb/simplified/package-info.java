/**
 * Core entry points and client-facing API for DynamoDB Simplified.
 * <p>
 * {@link dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient} and
 * {@link dev.hogwai.dynamodb.simplified.Table} are the primary entry points
 * for synchronous operations. {@link dev.hogwai.dynamodb.simplified.Versioned}
 * provides an optimistic locking interface for item version management.
 * <p>
 * Sub-packages contain fluent builders ({@code builder/}), async variants
 * ({@code async/}), expression builders ({@code expression/}), exception
 * types ({@code exception/}), result wrappers ({@code result/}), entity
 * support for single-table design ({@code entity/}), and internal utilities
 * ({@code internal/}).
 */
package dev.hogwai.dynamodb.simplified;
