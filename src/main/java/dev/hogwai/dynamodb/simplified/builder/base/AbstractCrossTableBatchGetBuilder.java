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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async cross-table batch get builders.
 * <p>
 * Manages entries (key + table associations), consistent read, projection,
 * and consumed capacity settings shared across sync and async variants.
 *
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractCrossTableBatchGetBuilder<S extends AbstractCrossTableBatchGetBuilder<S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractCrossTableBatchGetBuilder.class);

    protected final List<Entry> entries = new ArrayList<>();
    protected Boolean consistentRead;
    protected ReturnConsumedCapacity returnConsumedCapacity;

    protected AbstractCrossTableBatchGetBuilder() {
    }

    /**
     * Stores a table reference, key, and optional per-entry projection expression.
     * The table is stored as {@code Object} to accommodate both {@code Table}
     * and {@code AsyncTable} types without coupling the base to either package.
     */
    public record Entry(Object table, Key key, @Nullable ProjectionExpression projectionExpression) {
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
     * Restricts the returned attributes for the most recently added entry
     * to the specified attribute names.
     * <p>
     * Projection is applied server-side, reducing data transfer and consumed capacity.
     *
     * @param attributes the attribute names to include
     * @return this builder
     * @throws IllegalStateException if no entries have been added yet
     */
    public @NonNull S project(@NonNull String... attributes) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(Messages.NO_CROSS_TABLE_BATCH_GET_KEYS);
        }
        ProjectionExpression expression = ProjectionExpression.builder().include(attributes);
        Entry lastEntry = entries.removeLast();
        entries.add(new Entry(lastEntry.table(), lastEntry.key(), expression));
        return self();
    }

    /**
     * Restricts the returned attributes for the most recently added entry
     * using a {@link ProjectionExpression} builder consumer.
     *
     * @param consumer a consumer to configure the projection expression
     * @return this builder
     * @throws IllegalStateException if no entries have been added yet
     */
    public @NonNull S project(@NonNull Consumer<ProjectionExpression> consumer) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(Messages.NO_CROSS_TABLE_BATCH_GET_KEYS);
        }
        ProjectionExpression expression = ProjectionExpression.builder();
        consumer.accept(expression);
        Entry lastEntry = entries.removeLast();
        entries.add(new Entry(lastEntry.table(), lastEntry.key(), expression));
        return self();
    }

    /**
     * Configures whether to return consumed capacity information for the operation.
     *
     * @param returnConsumedCapacity the consumed capacity reporting level
     * @return this builder for chaining
     */
    @NonNull
    public S returnConsumedCapacity(@NonNull ReturnConsumedCapacity returnConsumedCapacity) {
        this.returnConsumedCapacity = returnConsumedCapacity;
        return self();
    }

    /**
     * Configures whether to use strongly consistent reads for this batch get operation.
     * <p>
     * If not set, DynamoDB defaults to eventually consistent reads.
     *
     * @param consistentRead {@code true} for strongly consistent reads
     * @return this builder for chaining
     */
    public @NonNull S consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return self();
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
     * Groups entries by their DynamoDB table name.
     *
     * @return a map from table name to its entries
     */
    protected Map<String, List<Entry>> groupEntriesByTable() {
        Map<String, List<Entry>> entriesByTable = new HashMap<>();
        for (Entry entry : entries) {
            String tableName = getTableName(entry);
            entriesByTable.computeIfAbsent(tableName, ignored -> new ArrayList<>()).add(entry);
        }
        return entriesByTable;
    }

    /**
     * Builds the request items map from entries grouped by table.
     * <p>
     * Also populates the {@code tableSchemas} map with the schema for each table,
     * needed downstream for deserialization.
     *
     * @param entriesByTable entries grouped by table name
     * @param tableSchemas   map to populate with table schemas (output)
     * @return the request items for the batch get operation
     */
    protected Map<String, KeysAndAttributes> buildRequestItems(
            Map<String, List<Entry>> entriesByTable,
            Map<String, TableSchema<?>> tableSchemas) {
        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        for (Map.Entry<String, List<Entry>> tableEntry : entriesByTable.entrySet()) {
            String tableName = tableEntry.getKey();
            List<Entry> tableEntries = tableEntry.getValue();

            List<Map<String, AttributeValue>> sdkKeys = new ArrayList<>(tableEntries.size());
            ProjectionExpression projection = null;
            TableSchema<?> schema = null;
            for (Entry entry : tableEntries) {
                TableSchema<?> entrySchema = getTableSchema(entry);
                if (schema == null) {
                    schema = entrySchema;
                }
                sdkKeys.add(entry.key().primaryKeyMap(entrySchema));
                var projExpr = entry.projectionExpression();
                if (projExpr != null && !projExpr.isEmpty()) {
                    projection = projExpr;
                }
            }
            tableSchemas.put(tableName, schema);

            KeysAndAttributes.Builder kaBuilder = KeysAndAttributes.builder().keys(sdkKeys);
            if (projection != null) {
                kaBuilder
                        .projectionExpression(projection.getExpression())
                        .expressionAttributeNames(projection.getExpressionNames());
            }
            if (consistentRead != null) {
                kaBuilder.consistentRead(consistentRead);
            }
            requestItems.put(tableName, kaBuilder.build());
        }
        return requestItems;
    }
}
