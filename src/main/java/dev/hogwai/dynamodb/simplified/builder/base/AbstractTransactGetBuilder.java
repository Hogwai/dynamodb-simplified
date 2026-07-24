package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.KeyUtils;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import dev.hogwai.dynamodb.simplified.internal.Messages;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async transact get builders.
 * <p>
 * Manages entries (table + key associations) and projection settings shared
 * across sync and async variants.
 *
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractTransactGetBuilder<S extends AbstractTransactGetBuilder<S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractTransactGetBuilder.class);

    protected final List<Entry> entries = new ArrayList<>();

    protected AbstractTransactGetBuilder() {
    }

    /**
     * Stores a table reference, key, and optional per-entry projection expression.
     * The table is stored as {@code Object} to accommodate both
     * {@code DynamoDbTable} and {@code DynamoDbAsyncTable} types without coupling
     * the base to either package.
     */
    public record Entry(Object table, Key key, @Nullable ProjectionExpression projectionExpression) {
        public Entry(Object table, Key key) {
            this(table, key, null);
        }
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     *
     * @return this builder
     */
    protected abstract @NonNull S self();

    /**
     * Extracts the DynamoDB table name from an entry's table reference.
     *
     * @param entry the entry whose table name to extract
     * @return the DynamoDB table name
     */
    protected abstract @NonNull String getTableName(@NonNull Entry entry);

    /**
     * Extracts the table schema from an entry's table reference.
     *
     * @param entry the entry whose table schema to extract
     * @return the table schema
     */
    @SuppressWarnings("java:S1452")
    protected abstract @NonNull TableSchema<?> getTableSchema(@NonNull Entry entry);

    /**
     * Restricts the returned attributes for the most recently added item
     * to the specified attribute names.
     * <p>
     * Projection is applied server-side, reducing data transfer and consumed capacity.
     *
     * @param attributes the attribute names to include
     * @return this builder
     * @throws IllegalStateException if no items have been added yet
     */
    public @NonNull S project(@NonNull String... attributes) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(Messages.NO_TRANSACT_GET_ITEMS);
        }
        ProjectionExpression expression = ProjectionExpression.builder().include(attributes);
        Entry lastEntry = entries.removeLast();
        entries.add(new Entry(lastEntry.table(), lastEntry.key(), expression));
        return self();
    }

    /**
     * Restricts the returned attributes for the most recently added item
     * using a {@link ProjectionExpression} builder consumer.
     *
     * @param consumer a consumer to configure the projection expression
     * @return this builder
     * @throws IllegalStateException if no items have been added yet
     */
    public @NonNull S project(@NonNull Consumer<ProjectionExpression> consumer) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(Messages.NO_TRANSACT_GET_ITEMS);
        }
        ProjectionExpression expression = ProjectionExpression.builder();
        consumer.accept(expression);
        Entry lastEntry = entries.removeLast();
        entries.add(new Entry(lastEntry.table(), lastEntry.key(), expression));
        return self();
    }

    /**
     * Checks whether the given entry has a non-empty projection expression.
     *
     * @param entry the entry to check
     * @return {@code true} if the entry has a non-null, non-empty projection expression
     */
    protected static boolean hasNonEmptyProjection(@NonNull Entry entry) {
        var projExpr = entry.projectionExpression();
        return projExpr != null && !projExpr.isEmpty();
    }

    /**
     * Builds a {@link Key} from partition and optional sort key values.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value, or {@code null} if the table has no sort key
     * @return the constructed key
     */
    protected static @NonNull Key buildKey(@NonNull Object partitionKey, @Nullable Object sortKey) {
        return KeyUtils.buildKey(
                AttributeValueConverter.toKeyAttributeValue(partitionKey),
                sortKey != null ? AttributeValueConverter.toKeyAttributeValue(sortKey) : null);
    }

    /**
     * Builds a list of {@link TransactGetItem} from the current entries for low-level
     * API calls.
     *
     * @return a list of transact get items
     */
    protected List<TransactGetItem> buildTransactItems() {
        List<TransactGetItem> transactItems = new ArrayList<>();
        for (Entry entry : entries) {
            ProjectionExpression projection = entry.projectionExpression();
            transactItems.add(TransactGetItem.builder().get(b -> {
                b.tableName(getTableName(entry));
                b.key(entry.key().primaryKeyMap(getTableSchema(entry)));
                if (projection != null && !projection.isEmpty()) {
                    b.projectionExpression(projection.getExpression());
                    b.expressionAttributeNames(projection.getExpressionNames());
                }
            }).build());
        }
        return transactItems;
    }
}
