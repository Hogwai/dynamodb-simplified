package dev.hogwai.dynamodb.simplified.entity;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.WrappedTableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds an entity discriminator to the map written by an Enhanced Client schema.
 *
 * @param <T> the entity type
 */
final class DiscriminatorTableSchema<T> extends WrappedTableSchema<T, TableSchema<T>> {

    private final EntitySchema<T> entitySchema;

    DiscriminatorTableSchema(TableSchema<T> delegate, EntitySchema<T> entitySchema) {
        super(delegate);
        this.entitySchema = entitySchema;
    }

    @Override
    public Map<String, AttributeValue> itemToMap(T item, boolean ignoreNulls) {
        Map<String, AttributeValue> itemMap = new HashMap<>(delegateTableSchema().itemToMap(item, ignoreNulls));
        itemMap.put(entitySchema.discriminatorAttribute(), AttributeValue.fromS(entitySchema.discriminator()));
        return itemMap;
    }
}
