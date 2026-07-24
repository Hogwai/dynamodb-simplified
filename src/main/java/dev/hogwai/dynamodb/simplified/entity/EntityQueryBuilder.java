package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.exception.ResourceNotFoundException;
import dev.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import dev.hogwai.dynamodb.simplified.internal.DynamoDbOperations;
import dev.hogwai.dynamodb.simplified.internal.ExpressionConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * Builds and executes cross-entity queries across multiple entity types
 * sharing a single DynamoDB table (single-table design).
 * <p>
 * Supports partition key filtering, sort key conditions, discriminator-based
 * entity filtering, pagination, and projection.
 *
 * @see dev.hogwai.dynamodb.simplified.entity.EntityTable#query(Object)
 */
@SuppressWarnings({"PMD.NullAssignment", "PMD.CyclomaticComplexity"})
public final class EntityQueryBuilder {

    private static final String CONDITION_JOINER = ExpressionConstants.AND;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final String discriminatorAttribute;
    private final List<EntitySchema<?>> includedSchemas = new ArrayList<>();

    @Nullable
    private Object partitionKey;

    @Nullable
    private String pkAttributeName;

    private int limit;

    // Sort key condition state
    enum KeyConditionOp {
        EQ, BEGINS_WITH, BETWEEN, GT, GE, LT, LE
    }

    @Nullable
    private KeyConditionOp keyOp;

    @Nullable
    private Object skValue;

    @Nullable
    private Object skValue2; // for BETWEEN only

    // Pagination
    @Nullable
    private Map<String, AttributeValue> exclusiveStartKey;

    // Options
    @Nullable
    private Boolean consistentRead;

    @Nullable
    private Boolean scanIndexForward;

    @Nullable
    private String[] projectedAttributes;

    @Nullable
    private Select select;

    EntityQueryBuilder(@NonNull DynamoDbClient dynamoDbClient,
                       @NonNull String tableName,
                       @NonNull String discriminatorAttribute) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.discriminatorAttribute = discriminatorAttribute;
    }

    /**
     * Sets the partition key value for the query.
     * Resets any previously configured sort key condition.
     *
     * @param partitionKey the partition key value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder partitionKey(@NonNull Object partitionKey) {
        this.partitionKey = partitionKey;
        this.keyOp = null;
        this.skValue = null;
        this.skValue2 = null;
        return this;
    }

    /**
     * Limits the number of items returned per page.
     *
     * @param limit the maximum number of items per page
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Includes an entity type in the cross-entity query results.
     * Items matching the entity's discriminator value will be deserialized
     * into the given entity class.
     *
     * @param entityClass the entity class to include
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder includeEntity(@NonNull Class<?> entityClass) {
        EntitySchema<?> schema = EntitySchemaReader.read(entityClass);
        return includeEntity(schema);
    }

    /**
     * Includes an entity type using a pre-built {@link EntitySchema}.
     *
     * @param schema the entity schema to include
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder includeEntity(@NonNull EntitySchema<?> schema) {
        if (pkAttributeName == null) {
            pkAttributeName = findPkAttributeName(schema);
        }
        includedSchemas.add(schema);
        return this;
    }

    // region Sort Key Conditions

    /**
     * Adds a sort key condition: sort key begins with the given prefix.
     *
     * @param value the prefix value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyBeginsWith(@NonNull Object value) {
        this.keyOp = KeyConditionOp.BEGINS_WITH;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    /**
     * Adds a sort key condition: sort key equals the given value.
     *
     * @param value the value to match
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyEquals(@NonNull Object value) {
        this.keyOp = KeyConditionOp.EQ;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    /**
     * Adds a sort key condition: sort key is between the given values (inclusive).
     *
     * @param from the lower bound (inclusive)
     * @param to   the upper bound (inclusive)
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyBetween(@NonNull Object from, @NonNull Object to) {
        this.keyOp = KeyConditionOp.BETWEEN;
        this.skValue = from;
        this.skValue2 = to;
        return this;
    }

    /**
     * Adds a sort key condition: sort key is strictly greater than the given value.
     *
     * @param value the threshold value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyGreaterThan(@NonNull Object value) {
        this.keyOp = KeyConditionOp.GT;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    /**
     * Adds a sort key condition: sort key is greater than or equal to the given value.
     *
     * @param value the threshold value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyGreaterThanOrEqual(@NonNull Object value) {
        this.keyOp = KeyConditionOp.GE;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    /**
     * Adds a sort key condition: sort key is strictly less than the given value.
     *
     * @param value the threshold value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyLessThan(@NonNull Object value) {
        this.keyOp = KeyConditionOp.LT;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    /**
     * Adds a sort key condition: sort key is less than or equal to the given value.
     *
     * @param value the threshold value
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder sortKeyLessThanOrEqual(@NonNull Object value) {
        this.keyOp = KeyConditionOp.LE;
        this.skValue = value;
        this.skValue2 = null;
        return this;
    }

    // endregion

    // region Projection

    /**
     * Restricts the returned attributes to the specified ones.
     *
     * @param attributes the attribute names to include in the result
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder project(@NonNull String... attributes) {
        this.projectedAttributes = Arrays.copyOf(attributes, attributes.length);
        return this;
    }

    // endregion

    // region Options

    /**
     * Enables or disables strongly consistent reads.
     *
     * @param consistentRead {@code true} for a strongly consistent read,
     *                       {@code false} (the default) for an eventually consistent read
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder consistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
        return this;
    }

    /**
     * Sets the sort order for the query results.
     *
     * @param forward {@code true} for ascending order (the default),
     *                {@code false} for descending order
     * @return this builder for chaining
     */
    @NonNull
    public EntityQueryBuilder scanIndexForward(boolean forward) {
        this.scanIndexForward = forward;
        return this;
    }

    // endregion

    // region Execution

    /**
     * Executes the query and returns all matching items grouped by entity type.
     *
     * @return a {@link CrossEntityResult} containing items mapped by entity class
     * @throws IllegalStateException if the partition key has not been set
     */
    @NonNull
    public CrossEntityResult execute() {
        if (partitionKey == null) {
            throw new IllegalStateException("Partition key must be set before executing");
        }
        if (includedSchemas.isEmpty()) {
            return new CrossEntityResult(Map.of());
        }

        QueryRequest request = buildQueryRequest(null);
        QueryResponse response = executeQuery(request);
        return mapResponse(response);
    }

    /**
     * Executes the query and returns the first page of results along with
     * pagination metadata.
     *
     * @return a {@link CrossEntityResultWithPagination} containing the first page
     */
    @NonNull
    public CrossEntityResultWithPagination executeWithPagination() {
        if (partitionKey == null) {
            throw new IllegalStateException("Partition key must be set before executing");
        }
        if (includedSchemas.isEmpty()) {
            return new CrossEntityResultWithPagination(new CrossEntityResult(Map.of()), null);
        }

        QueryRequest request = buildQueryRequest(null);
        QueryResponse response = executeQuery(request);
        CrossEntityResult result = mapResponse(response);
        return new CrossEntityResultWithPagination(result, response.lastEvaluatedKey());
    }

    /**
     * Executes the query and returns all results from all pages.
     *
     * @return a list of {@link CrossEntityResult}, one per page
     */
    @NonNull
    public List<CrossEntityResult> executeAll() {
        if (partitionKey == null) {
            throw new IllegalStateException("Partition key must be set before executing");
        }
        if (includedSchemas.isEmpty()) {
            return List.of(new CrossEntityResult(Map.of()));
        }

        List<CrossEntityResult> pages = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;

        do {
            QueryRequest request = buildQueryRequest(startKey);
            QueryResponse response = executeQuery(request);
            pages.add(mapResponse(response));
            startKey = response.lastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());

        return pages;
    }

    /**
     * Executes the query and returns the first matching item for each entity type,
     * if any.
     *
     * @return an {@link Optional} containing the first page, or empty if no items match
     */
    @NonNull
    public Optional<CrossEntityResult> executeAndGetFirst() {
        CrossEntityResultWithPagination page = executeWithPagination();
        if (page.getResult().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(page.getResult());
    }

    /**
     * Returns the total number of matching items by iterating all query result pages
     * using {@link Select#COUNT} server-side to avoid transferring item data.
     *
     * @return the total count of matching items
     */
    public long count() {
        if (partitionKey == null) {
            throw new IllegalStateException("Partition key must be set before executing");
        }
        if (includedSchemas.isEmpty()) {
            return 0L;
        }

        Select effectiveSelect = select != null ? select : Select.COUNT;
        long total = 0;
        Map<String, AttributeValue> nextKey = null;
        do {
            QueryRequest request = buildQueryRequest(nextKey, effectiveSelect);
            QueryResponse response = executeQuery(request);
            nextKey = response.lastEvaluatedKey();
            total += response.count();
        } while (hasNextPage(nextKey));
        return total;
    }

    // endregion

    // region Internal

    private static boolean hasNextPage(@Nullable Map<String, AttributeValue> key) {
        return key != null && !key.isEmpty();
    }

    private QueryRequest buildQueryRequest(@Nullable Map<String, AttributeValue> exclusiveStartKey) {
        return buildQueryRequest(exclusiveStartKey, null);
    }

    private QueryRequest buildQueryRequest(@Nullable Map<String, AttributeValue> exclusiveStartKey,
                                            @Nullable Select selectOverride) {
        String pkAttrName = pkAttributeName != null ? pkAttributeName : ExpressionConstants.PK_COMPONENT;

        StringBuilder keyConditionExpr = new StringBuilder(ExpressionConstants.PK).append(" = :pk");

        Map<String, String> exprAttrNames = new HashMap<>();
        Map<String, AttributeValue> exprAttrValues = new HashMap<>();

        exprAttrNames.put(ExpressionConstants.PK, pkAttrName);
        exprAttrValues.put(":pk", AttributeValue.builder().s(partitionKey.toString()).build());

        appendSortKeyCondition(keyConditionExpr, exprAttrNames, exprAttrValues);
        String filterExpr = buildDiscriminatorFilter(exprAttrNames, exprAttrValues);

        QueryRequest.Builder requestBuilder = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(keyConditionExpr.toString())
                .expressionAttributeNames(exprAttrNames)
                .expressionAttributeValues(exprAttrValues)
                .filterExpression(filterExpr);

        applyQueryOptions(requestBuilder, exclusiveStartKey, selectOverride);

        return requestBuilder.build();
    }

    private void appendSortKeyCondition(@NonNull StringBuilder keyConditionExpr,
                                        @NonNull Map<String, String> exprAttrNames,
                                        @NonNull Map<String, AttributeValue> exprAttrValues) {
        if (skValue == null) {
            return;
        }
        String skAttrName = findSkAttributeName();
        if (skAttrName == null) {
            return;
        }
        String skPlaceholder = ExpressionConstants.SK;
        exprAttrNames.put(skPlaceholder, skAttrName);
        String skValPlaceholder = ExpressionConstants.SK_VAL0;
        exprAttrValues.put(skValPlaceholder, AttributeValueConverter.toKeyAttributeValue(skValue));

        switch (keyOp) {
            case BEGINS_WITH -> keyConditionExpr.append(CONDITION_JOINER)
                    .append(ExpressionConstants.BEGINS_WITH).append(skPlaceholder).append(", ")
                    .append(skValPlaceholder).append(')');
            case BETWEEN -> {
                String skValPlaceholder2 = ExpressionConstants.SK_VAL1;
                exprAttrValues.put(skValPlaceholder2, AttributeValueConverter.toKeyAttributeValue(skValue2));
                keyConditionExpr.append(CONDITION_JOINER)
                        .append(skPlaceholder).append(" BETWEEN ")
                        .append(skValPlaceholder).append(CONDITION_JOINER)
                        .append(skValPlaceholder2);
            }
            case GT ->
                    keyConditionExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" > ").append(skValPlaceholder);
            case GE ->
                    keyConditionExpr.append(CONDITION_JOINER).append(skPlaceholder).append(ExpressionConstants.GE).append(skValPlaceholder);
            case LT ->
                    keyConditionExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" < ").append(skValPlaceholder);
            case LE ->
                    keyConditionExpr.append(CONDITION_JOINER).append(skPlaceholder).append(ExpressionConstants.LE).append(skValPlaceholder);
            case EQ ->
                    keyConditionExpr.append(CONDITION_JOINER).append(skPlaceholder).append(" = ").append(skValPlaceholder);
        }
    }

    @NonNull
    private String buildDiscriminatorFilter(@NonNull Map<String, String> exprAttrNames,
                                            @NonNull Map<String, AttributeValue> exprAttrValues) {
        StringBuilder filterExpr = new StringBuilder();
        boolean first = true;
        int i = 0;
        for (EntitySchema<?> schema : includedSchemas) {
            String valueKey = ":v" + i;
            i++;
            if (!first) {
                filterExpr.append(ExpressionConstants.OR);
            }
            filterExpr.append(ExpressionConstants.DT).append(" = ").append(valueKey);
            exprAttrValues.put(valueKey, AttributeValue.builder().s(schema.discriminator()).build());
            first = false;
        }
        exprAttrNames.put(ExpressionConstants.DT, discriminatorAttribute);
        return filterExpr.toString();
    }

    private void applyQueryOptions(QueryRequest.Builder builder,
                                    @Nullable Map<String, AttributeValue> exclusiveStartKey,
                                    @Nullable Select selectOverride) {
        if (limit > 0) {
            builder.limit(limit);
        }
        if (exclusiveStartKey != null) {
            builder.exclusiveStartKey(exclusiveStartKey);
        }
        if (consistentRead != null) {
            builder.consistentRead(consistentRead);
        }
        if (scanIndexForward != null) {
            builder.scanIndexForward(scanIndexForward);
        }
        if (projectedAttributes != null && projectedAttributes.length > 0) {
            builder.projectionExpression(String.join(", ", projectedAttributes));
        }
        Select effectiveSelect = selectOverride != null ? selectOverride : select;
        if (effectiveSelect != null) {
            builder.select(effectiveSelect);
        }
    }

    private QueryResponse executeQuery(QueryRequest request) {
        try {
            return dynamoDbClient.query(request);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(DynamoDbOperations.ENTITY_QUERY.getOperationName(), tableName, e);
        } catch (DynamoDbException e) {
            throw new OperationFailedException(DynamoDbOperations.ENTITY_QUERY.getOperationName(), tableName, e);
        }
    }

    /**
     * Finds the partition key attribute name from an entity schema's
     * {@code @KeyComponent(component = "PK")} annotation.
     */
    private static String findPkAttributeName(EntitySchema<?> schema) {
        List<EntitySchema.KeyComponentInfo> pkComponents = schema.getKeyComponentInfo(ExpressionConstants.PK_COMPONENT);
        if (pkComponents != null && !pkComponents.isEmpty()) {
            return pkComponents.getFirst().attributeName();
        }
        return ExpressionConstants.PK_COMPONENT;
    }

    /**
     * Tries to find a sort key attribute name from the first included schema
     * that has an SK component.
     */
    @Nullable
    private String findSkAttributeName() {
        for (EntitySchema<?> schema : includedSchemas) {
            List<EntitySchema.KeyComponentInfo> skComponents = schema.getKeyComponentInfo(ExpressionConstants.SK_COMPONENT);
            if (skComponents != null && !skComponents.isEmpty()) {
                return skComponents.getFirst().attributeName();
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CrossEntityResult mapResponse(QueryResponse response) {
        // Build a lookup: discriminator value -> entity schema
        Map<String, EntitySchema<?>> discriminatorToSchema = new HashMap<>();
        Map<Class<?>, TableSchema> tableSchemas = new HashMap<>();
        for (EntitySchema<?> schema : includedSchemas) {
            discriminatorToSchema.put(schema.discriminator(), schema);
            tableSchemas.put(schema.entityClass(), TableSchema.fromBean(schema.entityClass()));
        }

        // Initialize result map with all included entity types
        Map<Class<?>, List<Object>> resultMap = new LinkedHashMap<>();
        for (EntitySchema<?> schema : includedSchemas) {
            resultMap.put(schema.entityClass(), new ArrayList<>());
        }

        for (Map<String, AttributeValue> item : response.items()) {
            // Read discriminator attribute
            AttributeValue discValue = item.get(discriminatorAttribute);
            if (discValue != null && discValue.s() != null) {
                EntitySchema<?> schema = discriminatorToSchema.get(discValue.s());
                if (schema != null) {
                    // Map to entity object using the cached SDK TableSchema
                    TableSchema tableSchema = tableSchemas.get(schema.entityClass());
                    Object entity = tableSchema.mapToItem(item);
                    resultMap.get(schema.entityClass()).add(entity);
                }
            }
        }

        // Build unmodifiable result
        Map<Class<?>, List<?>> finalResult = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, List<Object>> entry : resultMap.entrySet()) {
            finalResult.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }

        return new CrossEntityResult(finalResult);
    }
}
// endregion
