package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fluent builder for retrieving an item from a DynamoDB table by its key,
 * with optional projection and consistent read settings. Part of the DynamoDB Simplified library.
 *
 * @param <T> the type of the item
 */
public class GetItemBuilder<T> {
    private final DynamoDbTable<T> table;
    private final Object partitionKey;
    private final Object sortKey;
    private ProjectionExpression projectionExpression;
    private boolean consistentRead = false;

    /**
     * Constructs a new {@code GetItemBuilder} for the given table and key.
     *
     * @param table        the DynamoDB table
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value (may be {@code null} if the table has no sort key)
     */
    public GetItemBuilder(@NonNull DynamoDbTable<T> table, @NonNull Object partitionKey, @Nullable Object sortKey) {
        this.table = table;
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
    }

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    public @NonNull GetItemBuilder<T> project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return this;
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    public @NonNull GetItemBuilder<T> project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
        return this;
    }

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    public @NonNull GetItemBuilder<T> consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    /**
     * Executes the get operation and returns the item, if found.
     *
     * @return an {@link Optional} containing the item, or empty if no item
     *         exists with the specified key
     */
    public @NonNull Optional<T> execute() {
        if (projectionExpression != null && !projectionExpression.isEmpty()) {
            return executeWithProjection();
        }
        return executeSimple();
    }

    private Optional<T> executeSimple() {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                                                                 .key(k -> {
                                                                     k.partitionValue(toAttributeValue(partitionKey));
                                                                     if (sortKey != null) {
                                                                         k.sortValue(toAttributeValue(sortKey));
                                                                     }
                                                                 })
                                                                .consistentRead(consistentRead)
                                                                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    private Optional<T> executeWithProjection() {
        Key.Builder keyBuilder = Key.builder().partitionValue(toAttributeValue(partitionKey));
        if (sortKey != null) {
            keyBuilder.sortValue(toAttributeValue(sortKey));
        }
        Key key = keyBuilder.build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                           .queryConditional(QueryConditional.keyEqualTo(key))
                                                           .consistentRead(consistentRead)
                                                           .limit(1)
                                                           .attributesToProject(projectionExpression.getExpressionNames().values().toArray(new String[0]))
                                                           .build();

        return table.query(request)
                    .items()
                    .stream()
                    .findFirst();
    }

    private static AttributeValue toAttributeValue(Object value) {
        return AttributeValueConverter.toKeyAttributeValue(value);
    }
}