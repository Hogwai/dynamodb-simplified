/**
 * Single-table design entity support for DynamoDB Simplified.
 * <p>
 * Provides annotations ({@link com.hogwai.dynamodb.simplified.entity.Entity @Entity},
 * {@link com.hogwai.dynamodb.simplified.entity.KeyComponent @KeyComponent},
 * {@link com.hogwai.dynamodb.simplified.entity.KeyPrefix @KeyPrefix},
 * {@link com.hogwai.dynamodb.simplified.entity.Version @Version}) for
 * declarative mapping of entity classes to composite key schemas.
 * <p>
 * {@link com.hogwai.dynamodb.simplified.entity.EntitySchema} reads
 * {@code @KeyComponent} annotations from entity classes.
 * {@link com.hogwai.dynamodb.simplified.entity.EntityTable} / 
 * {@link com.hogwai.dynamodb.simplified.entity.AsyncEntityTable} provide
 * entity-aware CRUD operations that automatically compute composite key
 * values and filter by discriminator.
 */
package com.hogwai.dynamodb.simplified.entity;
