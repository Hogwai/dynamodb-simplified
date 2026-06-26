/**
 * Expression builders for DynamoDB operations.
 * <p>
 * {@link com.hogwai.dynamodb.simplified.expression.FilterExpression} serves dual roles:
 * <ol>
 *   <li>As a <b>filter expression</b> for Query/Scan operations (post-read filtering)</li>
 *   <li>As the internal delegate for
 *       {@link com.hogwai.dynamodb.simplified.expression.ConditionExpression}
 *       (pre-write gating for Put/Update/Delete)</li>
 * </ol>
 * While these are semantically distinct in DynamoDB, the builder API is identical,
 * so {@code FilterExpression} is reused as the implementation for both.
 * <p>
 * {@link com.hogwai.dynamodb.simplified.expression.ProjectionExpression} controls
 * which attributes are returned in read operations.
 * {@link com.hogwai.dynamodb.simplified.expression.UpdateExpression} builds
 * server-side update instructions for item modifications.
 */
package com.hogwai.dynamodb.simplified.expression;
