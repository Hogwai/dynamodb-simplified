package com.hogwai.dynamodb.simplified.expression;

import com.hogwai.dynamodb.simplified.internal.AttributePathParser;
import com.hogwai.dynamodb.simplified.internal.AttributeValueConverter;
import com.hogwai.dynamodb.simplified.internal.PathSegment;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Fluent builder for constructing DynamoDB filter expressions used in
 * {@code Scan}, {@code Query}, and other DynamoDB operations as part of the
 * DynamoDB Simplified library.
 * <p>
 * Provides a chainable API for building complex filter conditions including
 * comparisons ({@code =}, {@code <>}, {@code <}, {@code <=}, {@code >}, {@code >=}),
 * string functions ({@code begins_with}, {@code contains}), server-side
 * {@code size()} comparisons, attribute existence checks, attribute type checks,
 * {@code BETWEEN}, {@code IN}, and logical combinators ({@code AND}, {@code OR},
 * {@code NOT}). All attribute names and values are automatically mapped to
 * expression attribute names ({@code #n0}, {@code #n1}, ...) and expression
 * attribute values ({@code :v0}, {@code :v1}, ...) to avoid reserved word
 * conflicts and simplify value handling.
 */
public class FilterExpression {
    private final StringBuilder expression = new StringBuilder();
    private final Map<String, String> expressionNames = new HashMap<>();
    private final Map<String, AttributeValue> expressionValues = new HashMap<>();
    private int nameCounter = 0;
    private int valueCounter = 0;

    private FilterExpression() {
    }

    /**
     * Creates a new {@link FilterExpression} builder.
     *
     * @return a new empty {@code FilterExpression}
     */
    @NonNull
    public static FilterExpression builder() {
        return new FilterExpression();
    }

    /**
     * Converts this filter expression into an AWS SDK {@link Expression} for use
     * with enhanced client request builders.
     *
     * @return the SDK expression
     */
    @NonNull
    public Expression toSdkExpression() {
        return Expression.builder()
                .expression(getExpression())
                .expressionNames(getExpressionNames())
                .expressionValues(getExpressionValues())
                .build();
    }

    // ============ Basic Comparisons ============

    /**
     * Adds an equality condition: {@code attribute = value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression eq(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, "=", value);
    }

    /**
     * Adds a not-equal condition: {@code attribute <> value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression ne(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, "<>", value);
    }

    /**
     * Adds a less-than condition: {@code attribute < value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression lt(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, "<", value);
    }

    /**
     * Adds a less-than-or-equal condition: {@code attribute <= value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression le(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, "<=", value);
    }

    /**
     * Adds a greater-than condition: {@code attribute > value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression gt(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, ">", value);
    }

    /**
     * Adds a greater-than-or-equal condition: {@code attribute >= value}.
     *
     * @param attribute the attribute name
     * @param value     the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression ge(@NonNull String attribute, @NonNull Object value) {
        return addCondition(attribute, ">=", value);
    }

    // ============ String Operations ============

    /**
     * Adds a {@code begins_with} function condition checking whether the
     * attribute value starts with the given prefix.
     *
     * @param attribute the attribute name
     * @param prefix    the string prefix to test
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression beginsWith(@NonNull String attribute, @NonNull String prefix) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().s(prefix).build());
        appendToExpression("begins_with(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    /**
     * Adds a {@code contains} function condition checking whether the
     * attribute value contains the given substring (for strings) or element
     * (for sets).
     *
     * @param attribute the attribute name
     * @param value     the value to search for within the attribute
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression contains(@NonNull String attribute, @NonNull Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("contains(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    // ============ Server-side SIZE Operations ============

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * equals the given value.
     *
     * @param attribute the attribute name
     * @param size      the expected size
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeEq(String attribute, int size) {
        return addSizeCondition(attribute, "=", size);
    }

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * is less than the given value.
     *
     * @param attribute the attribute name
     * @param size      the size to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeLt(String attribute, int size) {
        return addSizeCondition(attribute, "<", size);
    }

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * is less than or equal to the given value.
     *
     * @param attribute the attribute name
     * @param size      the size to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeLe(String attribute, int size) {
        return addSizeCondition(attribute, "<=", size);
    }

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * is greater than the given value.
     *
     * @param attribute the attribute name
     * @param size      the size to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeGt(String attribute, int size) {
        return addSizeCondition(attribute, ">", size);
    }

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * is greater than or equal to the given value.
     *
     * @param attribute the attribute name
     * @param size      the size to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeGe(String attribute, int size) {
        return addSizeCondition(attribute, ">=", size);
    }

    /**
     * Adds a condition that the server-side {@code size()} of the attribute
     * is between the given minimum and maximum values (inclusive).
     *
     * @param attribute the attribute name
     * @param minSize   the minimum size
     * @param maxSize   the maximum size
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression sizeBetween(String attribute, int minSize, int maxSize) {
        String nameKey = addName(attribute);
        String minValueKey = addValue(AttributeValue.builder().n(String.valueOf(minSize)).build());
        String maxValueKey = addValue(AttributeValue.builder().n(String.valueOf(maxSize)).build());
        appendToExpression("size(%s) BETWEEN %s AND %s".formatted(nameKey, minValueKey, maxValueKey));
        return this;
    }

    // ============ Attribute Existence ============

    /**
     * Adds an {@code attribute_exists} condition that checks whether the
     * specified attribute is present on the item.
     *
     * @param attribute the attribute name
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression exists(@NonNull String attribute) {
        String nameKey = addName(attribute);
        appendToExpression("attribute_exists(%s)".formatted(nameKey));
        return this;
    }

    /**
     * Adds an {@code attribute_not_exists} condition that checks whether the
     * specified attribute is not present on the item.
     *
     * @param attribute the attribute name
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression notExists(@NonNull String attribute) {
        String nameKey = addName(attribute);
        appendToExpression("attribute_not_exists(%s)".formatted(nameKey));
        return this;
    }

    // ============ Attribute Type ============

    /**
     * Adds an {@code attribute_type} condition that checks whether the
     * specified attribute is of the given DynamoDB type.
     *
     * @param attribute the attribute name
     * @param type      the expected {@link AttributeType}
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression attributeType(@NonNull String attribute, @NonNull AttributeType type) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().s(type.getCode()).build());
        appendToExpression("attribute_type(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    // ============ BETWEEN ============

    /**
     * Adds a {@code BETWEEN} condition: {@code attribute BETWEEN low AND high}
     * (inclusive). Supports strings, numbers, and binaries.
     *
     * @param attribute the attribute name
     * @param low       the lower bound
     * @param high      the upper bound
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression between(@NonNull String attribute, @NonNull Object low, @NonNull Object high) {
        String nameKey = addName(attribute);
        String lowKey = addValue(toAttributeValue(low));
        String highKey = addValue(toAttributeValue(high));
        appendToExpression("%s BETWEEN %s AND %s".formatted(nameKey, lowKey, highKey));
        return this;
    }

    // ============ IN ============

    /**
     * Adds an {@code IN} condition: {@code attribute IN (val1, val2, ...)}.
     * The attribute must match at least one of the provided values.
     *
     * @param attribute the attribute name
     * @param values    one or more values to test against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression in(@NonNull String attribute, @NonNull Object... values) {
        String nameKey = addName(attribute);
        StringJoiner joiner = new StringJoiner(", ");
        for (Object value : values) {
            joiner.add(addValue(toAttributeValue(value)));
        }
        appendToExpression("%s IN (%s)".formatted(nameKey, joiner.toString()));
        return this;
    }

    // ============ Logical Operators ============

    /**
     * Appends an {@code AND} logical operator to combine two filter conditions.
     *
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression and() {
        expression.append(" AND ");
        return this;
    }

    /**
     * Appends an {@code OR} logical operator to combine two filter conditions.
     *
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression or() {
        expression.append(" OR ");
        return this;
    }

    /**
     * Appends a {@code NOT} logical operator to negate the following condition.
     *
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression not() {
        expression.append("NOT ");
        return this;
    }

    /**
     * Wraps the given nested {@link FilterExpression} in parentheses and
     * merges its expression attribute names and values into this builder.
     * Useful for creating grouped or nested conditions with proper precedence.
     *
     * @param nested the filter expression to wrap in parentheses
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression group(@NonNull FilterExpression nested) {
        // Re-key nested placeholders to avoid collision
        int nameOffset = nameCounter;
        int valueOffset = valueCounter;
        Map<String, String> rekeyedNames = new HashMap<>();
        Map<String, AttributeValue> rekeyedValues = new HashMap<>();
        nested.expressionNames.forEach((k, v) -> {
            int idx = Integer.parseInt(k.substring(2));
            String newKey = "#n" + (nameOffset + idx);
            rekeyedNames.put(newKey, v);
            nameCounter++;
        });
        nested.expressionValues.forEach((k, v) -> {
            int idx = Integer.parseInt(k.substring(2));
            String newKey = ":v" + (valueOffset + idx);
            rekeyedValues.put(newKey, v);
            valueCounter++;
        });
        // Rebuild nested expression string with new keys
        // Use regex to safely handle multi-digit indices (e.g., #n10 contains #n1)
        String rekeyedExpr = Pattern.compile("#n(\\d+)")
                .matcher(nested.expression.toString())
                .replaceAll(mr -> "#n" + (nameOffset + Integer.parseInt(mr.group(1))));
        rekeyedExpr = Pattern.compile(":v(\\d+)")
                .matcher(rekeyedExpr)
                .replaceAll(mr -> ":v" + (valueOffset + Integer.parseInt(mr.group(1))));
        this.expressionNames.putAll(rekeyedNames);
        this.expressionValues.putAll(rekeyedValues);
        appendToExpression("(" + rekeyedExpr + ")");
        return this;
    }

    // ============ Nested Attribute Access ============

    /**
     * Adds an equality condition on a nested attribute path (e.g.,
     * {@code address.city}). Dot-separated path segments are resolved to
     * individual expression attribute names, and list indices (e.g.,
     * {@code items[0]}) are supported.
     *
     * @param path  the nested attribute path (e.g., {@code "address.city"})
     * @param value the value to compare against
     * @return this builder for chaining
     */
    @NonNull
    public FilterExpression nestedEq(@NonNull String path, @NonNull Object value) {
        String nameKey = addNestedName(path);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    // ============ Utility Methods ============

    private FilterExpression addCondition(@NonNull String attribute, @NonNull String operator, @NonNull Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("%s %s %s".formatted(nameKey, operator, valueKey));
        return this;
    }

    private FilterExpression addSizeCondition(@NonNull String attribute, @NonNull String operator, int size) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(String.valueOf(size)).build());
        appendToExpression("size(%s) %s %s".formatted(nameKey, operator, valueKey));
        return this;
    }

    private String addName(@NonNull String attribute) {
        String key = "#n" + nameCounter;
        nameCounter++;
        expressionNames.put(key, attribute);
        return key;
    }

    private String addNestedName(@NonNull String path) {
        List<PathSegment> segments = AttributePathParser.parse(path);
        StringJoiner joiner = new StringJoiner(".");
        for (PathSegment segment : segments) {
            if (segment.hasIndex()) {
                joiner.add(addName(segment.name()) + segment.indexSuffix());
            } else {
                joiner.add(addName(segment.name()));
            }
        }
        return joiner.toString();
    }

    private String addValue(@NonNull AttributeValue value) {
        String key = ":v" + valueCounter;
        valueCounter++;
        expressionValues.put(key, value);
        return key;
    }

    private void appendToExpression(@NonNull String part) {
        expression.append(part);
    }

    private static AttributeValue toAttributeValue(@NonNull Object value) {
        return AttributeValueConverter.toAttributeValue(value);
    }

    /**
     * Returns the built filter expression string (e.g.,
     * {@code "#n0 = :v0 AND begins_with(#n1, :v1)"}).
     *
     * @return the filter expression string
     */
    @NonNull
    public String getExpression() {
        return expression.toString();
    }

    /**
     * Returns the map of expression attribute names used in this expression.
     * Keys are the generated placeholders (e.g., {@code #n0}) and values are
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
     * Keys are the generated placeholders (e.g., {@code :v0}) and values are
     * the corresponding {@link AttributeValue} instances.
     *
     * @return the expression attribute values map
     */
    @NonNull
    public Map<String, AttributeValue> getExpressionValues() {
        return expressionValues;
    }

    /**
     * Returns whether no conditions have been added to this expression yet.
     *
     * @return {@code true} if the expression is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return expression.isEmpty();
    }

    /**
     * Represents a DynamoDB attribute type for use with
     * {@link FilterExpression#attributeType(String, AttributeType)}.
     */
    public enum AttributeType {
        /** String ({@code S}) */
        STRING("S"),
        /** Number ({@code N}) */
        NUMBER("N"),
        /** Binary ({@code B}) */
        BINARY("B"),
        /** String Set ({@code SS}) */
        STRING_SET("SS"),
        /** Number Set ({@code NS}) */
        NUMBER_SET("NS"),
        /** Binary Set ({@code BS}) */
        BINARY_SET("BS"),
        /** Map ({@code M}) */
        MAP("M"),
        /** List ({@code L}) */
        LIST("L"),
        /** Null ({@code NULL}) */
        NULL("NULL"),
        /** Boolean ({@code BOOL}) */
        BOOLEAN("BOOL");

        private final String code;

        AttributeType(String code) {
            this.code = code;
        }

        /**
         * Returns the DynamoDB type code string for this attribute type.
         *
         * @return the type code (e.g., {@code "S"}, {@code "N"}, {@code "M"})
         */
        public String getCode() {
            return code;
        }
    }
}

