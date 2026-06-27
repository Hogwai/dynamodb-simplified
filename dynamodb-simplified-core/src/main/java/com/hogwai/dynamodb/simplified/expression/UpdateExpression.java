package com.hogwai.dynamodb.simplified.expression;

import com.hogwai.dynamodb.simplified.internal.AttributePathParser;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Fluent builder for constructing DynamoDB update expressions as part of the
 * DynamoDB Simplified library.
 * <p>
 * Supports all four update expression clauses, {@code SET}, {@code REMOVE},
 * {@code ADD}, and {@code DELETE}, with a chainable API. The builder
 * automatically manages expression attribute name placeholders ({@code #u0},
 * {@code #u1}, ...) and expression attribute value placeholders ({@code :u0},
 * {@code :u1}, ...) to avoid reserved word conflicts. Common update patterns
 * such as incrementing/decrementing numbers, appending/prepending to lists,
 * and adding/removing set elements are provided as dedicated methods.
 */
public class UpdateExpression {
    private final List<String> setActions = new ArrayList<>();
    private final List<String> removeActions = new ArrayList<>();
    private final List<String> addActions = new ArrayList<>();
    private final List<String> deleteActions = new ArrayList<>();

    private final Map<String, String> expressionNames = new HashMap<>();
    private final Map<String, AttributeValue> expressionValues = new HashMap<>();
    private int nameCounter = 0;
    private int valueCounter = 0;

    private static final String SPACE_SEPARATED = "%s %s";

    private UpdateExpression() {
    }

    /**
     * Creates a new {@link UpdateExpression} builder.
     *
     * @return a new empty {@code UpdateExpression}
     */
    @NonNull
    public static UpdateExpression builder() {
        return new UpdateExpression();
    }

    // ============ SET Operations ============

    /**
     * Adds a {@code SET} action that assigns the given value to the specified
     * attribute, overwriting any existing value.
     *
     * @param attribute the attribute name (must not be null)
     * @param value     the value to set (must not be null: use {@link #remove(String...)} instead)
     * @return this builder for chaining
     * @throws IllegalArgumentException if value is null
     */
    @NonNull
    public UpdateExpression set(@NonNull String attribute, @Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Cannot set attribute '" + attribute + "' to null. Use remove(\"" + attribute + "\") instead.");
        }
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that assigns the given value to the specified
     * attribute only if the attribute does not already exist, using the
     * {@code if_not_exists} function.
     *
     * @param attribute the attribute name
     * @param value     the value to set if the attribute does not exist
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression setIfNotExists(@NonNull String attribute, @NonNull Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = if_not_exists(%s, %s)".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that increments a number attribute by the
     * given delta using an in-place arithmetic expression
     * ({@code attr = attr + delta}).
     *
     * @param attribute the attribute name
     * @param delta     the amount to increment by
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression increment(@NonNull String attribute, @NonNull Number delta) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(delta.toString()).build());
        setActions.add("%s = %s + %s".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that decrements a number attribute by the
     * given delta using an in-place arithmetic expression
     * ({@code attr = attr - delta}).
     *
     * @param attribute the attribute name
     * @param delta     the amount to decrement by
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression decrement(@NonNull String attribute, @NonNull Number delta) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(delta.toString()).build());
        setActions.add("%s = %s - %s".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that appends a list of values to an existing
     * list attribute using {@code list_append}. If the attribute does not
     * exist, it is initialized to an empty list before appending.
     *
     * @param attribute the list attribute name
     * @param values    the values to append
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression appendToList(@NonNull String attribute, @NonNull List<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        String emptyListKey = addValue(AttributeValue.builder().l(Collections.emptyList()).build());
        setActions.add("%s = list_append(if_not_exists(%s, %s), %s)".formatted(nameKey, nameKey, emptyListKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that prepends a list of values to an existing
     * list attribute using {@code list_append}. If the attribute does not
     * exist, it is initialized to an empty list before prepending.
     *
     * @param attribute the list attribute name
     * @param values    the values to prepend
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression prependToList(@NonNull String attribute, @NonNull List<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        String emptyListKey = addValue(AttributeValue.builder().l(Collections.emptyList()).build());
        setActions.add("%s = list_append(%s, if_not_exists(%s, %s))".formatted(
                nameKey, valueKey, nameKey, emptyListKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that assigns a value to a specific element in
     * a list attribute by index (e.g., {@code myList[2] = value}).
     *
     * @param attribute the list attribute name
     * @param index     the zero-based index of the element to set
     * @param value     the value to assign
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression setListElement(@NonNull String attribute, int index, @NonNull Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s[%d] = %s".formatted(nameKey, index, valueKey));
        return this;
    }

    /**
     * Adds a {@code SET} action that assigns a value to a nested attribute
     * path (dot-separated), such as {@code address.city}. Each path segment
     * is individually mapped to an expression attribute name placeholder.
     *
     * @param path  the nested attribute path (e.g., {@code "address.city"})
     * @param value the value to set
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression setNested(@NonNull String path, @NonNull Object value) {
        String nameKey = addNestedName(path);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    /**
     * Sets the TTL attribute to the given duration from now.
     * <p>
     * Convenience method that computes the epoch timestamp and delegates to {@link #set(String, Object)}.
     *
     * @param attribute the TTL attribute name
     * @param duration  the duration from now when the item should expire
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression ttl(@NonNull String attribute, @NonNull Duration duration) {
        long epoch = Instant.now().plus(duration).getEpochSecond();
        return set(attribute, epoch);
    }

    // ============ REMOVE Operations ============

    /**
     * Adds one or more attributes to the {@code REMOVE} clause, deleting them
     * from the item.
     *
     * @param attributes one or more attribute names to remove
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression remove(@NonNull String... attributes) {
        for (String attr : attributes) {
            removeActions.add(addName(attr));
        }
        return this;
    }

    /**
     * Adds a specific list element to the {@code REMOVE} clause, removing it
     * by index (e.g., {@code myList[2]}).
     *
     * @param attribute the list attribute name
     * @param index     the zero-based index of the element to remove
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression removeListElement(@NonNull String attribute, int index) {
        String nameKey = addName(attribute);
        removeActions.add("%s[%d]".formatted(nameKey, index));
        return this;
    }

    // ============ ADD Operations (for sets and numbers) ============

    /**
     * Adds an {@code ADD} action that inserts the given values into a set
     * attribute. If the attribute does not exist, DynamoDB creates it with
     * the provided values.
     *
     * @param attribute the set attribute name
     * @param values    the values to add to the set
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression addToSet(@NonNull String attribute, @NonNull Set<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        addActions.add(SPACE_SEPARATED.formatted(nameKey, valueKey));
        return this;
    }

    /**
     * Adds an {@code ADD} action that increments a number attribute by the
     * given value. This uses the {@code ADD} clause rather than {@code SET},
     * which is useful for atomic counters in scenarios where you want to
     * ensure the increment is applied regardless of concurrent modifications
     * (DynamoDB {@code ADD} is idempotent for sets but not for numbers).
     *
     * @param attribute the number attribute name
     * @param value     the amount to add
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression addNumber(@NonNull String attribute, @NonNull Number value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(value.toString()).build());
        addActions.add(SPACE_SEPARATED.formatted(nameKey, valueKey));
        return this;
    }

    // ============ DELETE Operations (for sets) ============

    /**
     * Adds a {@code DELETE} action that removes the given values from a set
     * attribute.
     *
     * @param attribute the set attribute name
     * @param values    the values to remove from the set
     * @return this builder for chaining
     */
    @NonNull
    public UpdateExpression deleteFromSet(@NonNull String attribute, @NonNull Set<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        deleteActions.add(SPACE_SEPARATED.formatted(nameKey, valueKey));
        return this;
    }

    // ============ Helper methods ============

    private String addName(@NonNull String attribute) {
        String key = "#u" + nameCounter;
        nameCounter++;
        expressionNames.put(key, attribute);
        return key;
    }

    private String addNestedName(@NonNull String path) {
        return AttributePathParser.rebuildNestedPath(path, this::addName);
    }

    private String addValue(@NonNull AttributeValue value) {
        String key = ":u" + valueCounter;
        valueCounter++;
        expressionValues.put(key, value);
        return key;
    }

    private static AttributeValue toAttributeValue(@Nullable Object value) {
        return AttributeValueConverter.toAttributeValue(value);
    }

    /**
     * Returns the built update expression string with {@code SET},
     * {@code REMOVE}, {@code ADD}, and/or {@code DELETE} clauses, formatted
     * as required by the DynamoDB API (e.g.,
     * {@code "SET #u0 = :u0, #u1 = :u1 REMOVE #u2"}). Only non-empty clauses
     * are included.
     *
     * @return the update expression string
     */
    @NonNull
    public String getExpression() {
        StringBuilder builder = new StringBuilder();

        if (!setActions.isEmpty()) {
            builder.append("SET ").append(String.join(", ", setActions));
        }
        if (!removeActions.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append("REMOVE ").append(String.join(", ", removeActions));
        }
        if (!addActions.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append("ADD ").append(String.join(", ", addActions));
        }
        if (!deleteActions.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append("DELETE ").append(String.join(", ", deleteActions));
        }

        return builder.toString();
    }

    /**
     * Returns the map of expression attribute names used in this expression.
     * Keys are the generated placeholders (e.g., {@code #u0}) and values are
     * the original attribute names.
     *
     * @return the expression attribute names map
     */
    @NonNull
    public Map<String, String> getExpressionNames() {
        return expressionNames;
    }

    /**
     * Returns the map of expression attribute values used in this expression.
     * Keys are the generated placeholders (e.g., {@code :u0}) and values are
     * the corresponding {@link AttributeValue} instances.
     *
     * @return the expression attribute values map
     */
    @NonNull
    public Map<String, AttributeValue> getExpressionValues() {
        return expressionValues;
    }

    /**
     * Returns whether no update actions have been added to this expression yet.
     *
     * @return {@code true} if no actions have been added, {@code false} otherwise
     */
    public boolean isEmpty() {
        return setActions.isEmpty() && removeActions.isEmpty()
                && addActions.isEmpty() && deleteActions.isEmpty();
    }
}
