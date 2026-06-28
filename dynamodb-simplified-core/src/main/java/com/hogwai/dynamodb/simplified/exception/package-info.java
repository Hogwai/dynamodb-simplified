/**
 * Exception hierarchy for DynamoDB Simplified.
 * <p>
 * {@link com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException}
 * is the base runtime exception. Subclasses wrap specific AWS SDK exceptions
 * into consistent, domain-specific types:
 * <ul>
 *   <li>{@link com.hogwai.dynamodb.simplified.exception.OperationFailedException}
 *       — wraps any {@link software.amazon.awssdk.services.dynamodb.model.DynamoDbException}</li>
 *   <li>{@link com.hogwai.dynamodb.simplified.exception.TransactionFailedException}
 *       — wraps {@link software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException}</li>
 *   <li>{@link com.hogwai.dynamodb.simplified.exception.ConditionFailedException}
 *       — wraps {@link software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException}</li>
 * </ul>
 */
package com.hogwai.dynamodb.simplified.exception;
