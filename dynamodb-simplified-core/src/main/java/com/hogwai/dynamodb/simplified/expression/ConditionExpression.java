package com.hogwai.dynamodb.simplified.expression;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/**
 * A typed wrapper around a condition expression for DynamoDB pre-write gating
 * (PutItem, UpdateItem, DeleteItem, TransactWriteItems condition checks).
 * <p>
 * This is semantically distinct from a {@link FilterExpression}, which is used
 * for post-read filtering on Query/Scan operations. DynamoDB treats these as
 * separate expression types, see the DynamoDB Developer Guide for details.
 * <p>
 * Internally delegates to a {@link FilterExpression} for the actual expression
 * building logic, but presents a distinct type in the public API to prevent
 * accidental misuse of filter expressions where condition expressions are
 * expected, and vice versa.
 */
public final class ConditionExpression {
    private final FilterExpression delegate;

    private ConditionExpression(FilterExpression delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps a {@link FilterExpression} as a {@code ConditionExpression} for
     * typed API use. Useful for migrating existing code that builds expressions
     * using {@code FilterExpression}.
     *
     * @param filterExpression the filter expression to wrap
     * @return a new condition expression backed by the given filter expression
     */
    public static ConditionExpression from(@NonNull FilterExpression filterExpression) {
        return new ConditionExpression(filterExpression);
    }

    /**
     * Converts this condition expression into an AWS SDK {@link Expression}
     * for use with enhanced client request builders.
     *
     * @return the SDK expression
     */
    @NonNull
    public Expression toSdkExpression() {
        return delegate.toSdkExpression();
    }

    /**
     * Returns the built condition expression string.
     *
     * @return the expression string
     */
    @NonNull
    public String getExpression() {
        return delegate.getExpression();
    }

    /**
     * Returns the map of expression attribute names used in this expression.
     *
     * @return the expression attribute names map
     */
    @NonNull
    public Map<String, String> getExpressionNames() {
        return delegate.getExpressionNames();
    }

    /**
     * Returns the map of expression attribute values used in this expression.
     *
     * @return the expression attribute values map
     */
    @NonNull
    public Map<String, AttributeValue> getExpressionValues() {
        return delegate.getExpressionValues();
    }

    /**
     * Returns whether no conditions have been added to this expression yet.
     *
     * @return {@code true} if the expression is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * Creates a new builder for constructing a {@link ConditionExpression}.
     *
     * @return a new builder
     */
    public static @NonNull Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ConditionExpression}.
     * <p>
     * Every public builder method delegates to the underlying
     * {@link FilterExpression} builder, ensuring identical expression-building
     * capability while maintaining a distinct type.
     */
    public static final class Builder {

        /** Creates a new condition expression builder. */
        public Builder() {
        }

        private final FilterExpression delegate = FilterExpression.builder();

        // ============ Basic Comparisons ============

        /**
         * Adds an equality condition: {@code attr = value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder eq(@NonNull String attr, @NonNull Object value) {
            delegate.eq(attr, value);
            return this;
        }

        /**
         * Adds a not-equal condition: {@code attr <> value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder ne(@NonNull String attr, @NonNull Object value) {
            delegate.ne(attr, value);
            return this;
        }

        /**
         * Adds a less-than condition: {@code attr < value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder lt(@NonNull String attr, @NonNull Object value) {
            delegate.lt(attr, value);
            return this;
        }

        /**
         * Adds a less-than-or-equal condition: {@code attr <= value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder le(@NonNull String attr, @NonNull Object value) {
            delegate.le(attr, value);
            return this;
        }

        /**
         * Adds a greater-than condition: {@code attr > value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder gt(@NonNull String attr, @NonNull Object value) {
            delegate.gt(attr, value);
            return this;
        }

        /**
         * Adds a greater-than-or-equal condition: {@code attr >= value}.
         *
         * @param attr  the attribute name
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder ge(@NonNull String attr, @NonNull Object value) {
            delegate.ge(attr, value);
            return this;
        }

        // ============ String Operations ============

        /**
         * Adds a {@code begins_with} condition: the attribute value starts with the given prefix.
         *
         * @param attr   the attribute name
         * @param prefix the prefix to match
         * @return this builder for chaining
         */
        @NonNull
        public Builder beginsWith(@NonNull String attr, @NonNull String prefix) {
            delegate.beginsWith(attr, prefix);
            return this;
        }

        /**
         * Adds a {@code contains} condition: the attribute value contains the given value.
         *
         * @param attr  the attribute name
         * @param value the value to search for within the attribute
         * @return this builder for chaining
         */
        @NonNull
        public Builder contains(@NonNull String attr, @NonNull Object value) {
            delegate.contains(attr, value);
            return this;
        }

        // ============ Server-side SIZE Operations ============

        /**
         * Adds a condition on the server-side size of the attribute value: {@code size(attr) = value}.
         *
         * @param attr the attribute name
         * @param size the expected size
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeEq(@NonNull String attr, int size) {
            delegate.sizeEq(attr, size);
            return this;
        }

        /**
         * Adds a condition: {@code size(attr) < value}.
         *
         * @param attr the attribute name
         * @param size the size threshold
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeLt(@NonNull String attr, int size) {
            delegate.sizeLt(attr, size);
            return this;
        }

        /**
         * Adds a condition: {@code size(attr) <= value}.
         *
         * @param attr the attribute name
         * @param size the size threshold
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeLe(@NonNull String attr, int size) {
            delegate.sizeLe(attr, size);
            return this;
        }

        /**
         * Adds a condition: {@code size(attr) > value}.
         *
         * @param attr the attribute name
         * @param size the size threshold
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeGt(@NonNull String attr, int size) {
            delegate.sizeGt(attr, size);
            return this;
        }

        /**
         * Adds a condition: {@code size(attr) >= value}.
         *
         * @param attr the attribute name
         * @param size the size threshold
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeGe(@NonNull String attr, int size) {
            delegate.sizeGe(attr, size);
            return this;
        }

        /**
         * Adds a condition: {@code size(attr) BETWEEN min AND max}.
         *
         * @param attr the attribute name
         * @param min  the minimum size (inclusive)
         * @param max  the maximum size (inclusive)
         * @return this builder for chaining
         */
        @NonNull
        public Builder sizeBetween(@NonNull String attr, int min, int max) {
            delegate.sizeBetween(attr, min, max);
            return this;
        }

        // ============ Attribute Existence ============

        /**
         * Adds a condition that the attribute must exist (not null).
         *
         * @param attr the attribute name
         * @return this builder for chaining
         */
        @NonNull
        public Builder exists(@NonNull String attr) {
            delegate.exists(attr);
            return this;
        }

        /**
         * Adds a condition that the attribute must not exist (is null or absent).
         *
         * @param attr the attribute name
         * @return this builder for chaining
         */
        @NonNull
        public Builder notExists(@NonNull String attr) {
            delegate.notExists(attr);
            return this;
        }

        // ============ Attribute Type ============

        /**
         * Adds a condition checking the attribute's DynamoDB type using {@code attribute_type}.
         *
         * @param attr the attribute name
         * @param type the expected DynamoDB attribute type
         * @return this builder for chaining
         */
        @NonNull
        public Builder attributeType(@NonNull String attr, FilterExpression.AttributeType type) {
            delegate.attributeType(attr, type);
            return this;
        }

        // ============ BETWEEN, IN ============

        /**
         * Adds a BETWEEN condition: {@code attr BETWEEN low AND high}.
         *
         * @param attr the attribute name
         * @param low  the lower bound (inclusive)
         * @param high the upper bound (inclusive)
         * @return this builder for chaining
         */
        @NonNull
        public Builder between(@NonNull String attr, @NonNull Object low, @NonNull Object high) {
            delegate.between(attr, low, high);
            return this;
        }

        /**
         * Adds an IN condition: {@code attr IN (value1, value2, ...)}.
         *
         * @param attr   the attribute name
         * @param values the set of values to test against
         * @return this builder for chaining
         */
        @NonNull
        public Builder in(@NonNull String attr, Object... values) {
            delegate.in(attr, values);
            return this;
        }

        // ============ Logical ============

        /**
         * Adds a logical AND between the previously added condition and the next one.
         *
         * @return this builder for chaining
         */
        @NonNull
        public Builder and() {
            delegate.and();
            return this;
        }

        /**
         * Adds a logical OR between the previously added condition and the next one.
         *
         * @return this builder for chaining
         */
        @NonNull
        public Builder or() {
            delegate.or();
            return this;
        }

        /**
         * Adds a logical NOT to negate the previously added condition.
         *
         * @return this builder for chaining
         */
        @NonNull
        public Builder not() {
            delegate.not();
            return this;
        }

        /**
         * Wraps the given nested {@link ConditionExpression} in parentheses and
         * merges its expression attribute names and values into this builder.
         * Useful for creating grouped or nested conditions with proper precedence.
         *
         * @param nested the condition expression to wrap in parentheses
         * @return this builder for chaining
         */
        @NonNull
        public Builder group(@NonNull ConditionExpression nested) {
            delegate.group(nested.delegate);
            return this;
        }

        // ============ Nested ============

        /**
         * Adds an equality condition on a nested attribute path (e.g. {@code "address.city"}).
         * The path is automatically expanded with expression attribute names for each segment.
         *
         * @param path  the dotted nested attribute path
         * @param value the value to compare against
         * @return this builder for chaining
         */
        @NonNull
        public Builder nestedEq(@NonNull String path, @NonNull Object value) {
            delegate.nestedEq(path, value);
            return this;
        }

        // ============ Build ============

        /**
         * Builds the {@link ConditionExpression}.
         *
         * @return a new condition expression with the accumulated conditions
         */
        public @NonNull ConditionExpression build() {
            return new ConditionExpression(delegate);
        }
    }
}
