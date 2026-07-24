package dev.hogwai.dynamodb.simplified.builder.base;

import dev.hogwai.dynamodb.simplified.expression.ProjectionExpression;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.KeyUtils;
import dev.hogwai.dynamodb.simplified.internal.Logging;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for sync and async batch get builders.
 *
 * @param <T> the item type
 * @param <S> the concrete builder type (for fluent chaining)
 */
public abstract class AbstractBatchGetBuilder<T, S extends AbstractBatchGetBuilder<T, S>> {

    protected static final Logger LOG = Logging.getLogger(AbstractBatchGetBuilder.class);

    protected final List<Key> keys = new ArrayList<>();
    protected Boolean consistentRead;
    protected ProjectionExpression projectionExpression;
    protected ReturnConsumedCapacity returnConsumedCapacity;

    protected AbstractBatchGetBuilder() {
    }

    /**
     * Returns {@code this} typed as {@code S} for fluent chaining.
     */
    protected abstract S self();

    /**
     * Adds a key to retrieve by partition key only.
     *
     * @param partitionKey the partition key value
     * @return this builder
     */
    public @NonNull S addKey(@NonNull Object partitionKey) {
        keys.add(KeyUtils.buildKey(AttributeValueConverter.toKeyAttributeValue(partitionKey), null));
        return self();
    }

    /**
     * Adds a key to retrieve by partition and sort key.
     *
     * @param partitionKey the partition key value
     * @param sortKey      the sort key value
     * @return this builder
     */
    public @NonNull S addKey(@NonNull Object partitionKey, @NonNull Object sortKey) {
        keys.add(KeyUtils.buildKey(
                AttributeValueConverter.toKeyAttributeValue(partitionKey),
                AttributeValueConverter.toKeyAttributeValue(sortKey)));
        return self();
    }

    /**
     * Adds multiple keys at once.
     *
     * @param keys the keys to retrieve
     * @return this builder
     */
    public @NonNull S addKeys(@NonNull Collection<Key> keys) {
        this.keys.addAll(keys);
        return self();
    }

    /**
     * Adds multiple keys from a list of partition key values (without sort keys).
     *
     * @param partitionKeys the partition key values
     * @return this builder
     */
    public @NonNull S keys(@NonNull List<Object> partitionKeys) {
        List<Key> newKeys = partitionKeys.stream()
                .map(pk -> KeyUtils.buildKey(AttributeValueConverter.toKeyAttributeValue(pk), null))
                .toList();
        return addKeys(newKeys);
    }

    /**
     * Enables or disables strongly consistent reads for this batch get.
     * <p>
     * If not set, the default (eventually consistent) is used by the DynamoDB API.
     *
     * @param consistentRead {@code true} for strongly consistent reads
     * @return this builder
     */
    public @NonNull S consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return self();
    }

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in each result
     * @return this builder for chaining
     */
    public @NonNull S project(@NonNull String... attributes) {
        this.projectionExpression = ProjectionExpression.builder().include(attributes);
        return self();
    }

    /**
     * Restricts the returned attributes by configuring a {@link ProjectionExpression}
     * via a consumer.
     *
     * @param projectionBuilder a consumer that configures the {@link ProjectionExpression}
     * @return this builder for chaining
     */
    public @NonNull S project(@NonNull Consumer<ProjectionExpression> projectionBuilder) {
        this.projectionExpression = ProjectionExpression.builder();
        projectionBuilder.accept(this.projectionExpression);
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
     * Builds a {@link KeysAndAttributes} from the current keys and projection/consistency settings.
     *
     * @param schema the table schema for converting enhanced keys to SDK key maps
     * @return the configured {@link KeysAndAttributes}
     */
    protected @NonNull KeysAndAttributes buildKeysAndAttributes(@NonNull TableSchema<?> schema) {
        List<Map<String, AttributeValue>> sdkKeys = new ArrayList<>(keys.size());
        for (Key key : keys) {
            sdkKeys.add(key.primaryKeyMap(schema));
        }
        KeysAndAttributes.Builder builder = KeysAndAttributes.builder()
                .keys(sdkKeys)
                .projectionExpression(projectionExpression.getExpression())
                .expressionAttributeNames(projectionExpression.getExpressionNames());
        if (consistentRead != null) {
            builder.consistentRead(consistentRead);
        }
        return builder.build();
    }

    /**
     * Returns the DynamoDB table name.
     */
    protected abstract @NonNull String tableName();
}
