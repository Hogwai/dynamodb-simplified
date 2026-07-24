package dev.hogwai.dynamodb.simplified.expression;

import org.jspecify.annotations.NonNull;

/**
 * A {@link FilterExpression} subtype that represents a DynamoDB condition expression.
 * <p>
 * This is a type-safe alias — it inherits all builder methods from {@link FilterExpression}
 * but is accepted by APIs that require a condition expression (e.g., put, update, delete)
 * rather than a filter expression (e.g., query, scan).
 */
public class ConditionExpression extends FilterExpression {

    private static final ConditionExpression EMPTY = new ConditionExpression();

    /**
     * Returns an empty condition expression.
     *
     * @return an empty condition expression
     */
    public static ConditionExpression empty() {
        return EMPTY;
    }

    /**
     * Creates a new empty condition expression.
     */
    public ConditionExpression() {
        super();
    }

    /**
     * Creates a condition expression that copies the state from the given filter expression.
     *
     * @param delegate the filter expression to copy
     */
    public ConditionExpression(FilterExpression delegate) {
        super(delegate);
    }

    /**
     * Wraps a {@link FilterExpression} as a {@code ConditionExpression}.
     *
     * @param filterExpression the filter expression to wrap
     * @return a new condition expression backed by the given filter expression
     */
    public static ConditionExpression from(@NonNull FilterExpression filterExpression) {
        return new ConditionExpression(filterExpression);
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
     * Extends {@link FilterExpression} to inherit all expression-building
     * capability, with covariant return types to preserve the builder type
     * through method chaining, and a terminal {@link #build()} method.
     */
    public static final class Builder extends FilterExpression {

        /** Creates a new condition expression builder. */
        public Builder() {
            super();
        }

        // region Basic Comparisons

        @Override
        @NonNull
        public Builder eq(@NonNull String attr, @NonNull Object value) {
            super.eq(attr, value);
            return this;
        }

        @Override
        @NonNull
        public Builder ne(@NonNull String attr, @NonNull Object value) {
            super.ne(attr, value);
            return this;
        }

        @Override
        @NonNull
        public Builder lt(@NonNull String attr, @NonNull Object value) {
            super.lt(attr, value);
            return this;
        }

        @Override
        @NonNull
        public Builder le(@NonNull String attr, @NonNull Object value) {
            super.le(attr, value);
            return this;
        }

        @Override
        @NonNull
        public Builder gt(@NonNull String attr, @NonNull Object value) {
            super.gt(attr, value);
            return this;
        }

        @Override
        @NonNull
        public Builder ge(@NonNull String attr, @NonNull Object value) {
            super.ge(attr, value);
            return this;
        }

        // endregion

        // region String Operations

        @Override
        @NonNull
        public Builder beginsWith(@NonNull String attr, @NonNull String prefix) {
            super.beginsWith(attr, prefix);
            return this;
        }

        @Override
        @NonNull
        public Builder contains(@NonNull String attr, @NonNull Object value) {
            super.contains(attr, value);
            return this;
        }

        // endregion

        // region Server-side SIZE Operations

        @Override
        @NonNull
        public Builder sizeEq(@NonNull String attr, int size) {
            super.sizeEq(attr, size);
            return this;
        }

        @Override
        @NonNull
        public Builder sizeLt(@NonNull String attr, int size) {
            super.sizeLt(attr, size);
            return this;
        }

        @Override
        @NonNull
        public Builder sizeLe(@NonNull String attr, int size) {
            super.sizeLe(attr, size);
            return this;
        }

        @Override
        @NonNull
        public Builder sizeGt(@NonNull String attr, int size) {
            super.sizeGt(attr, size);
            return this;
        }

        @Override
        @NonNull
        public Builder sizeGe(@NonNull String attr, int size) {
            super.sizeGe(attr, size);
            return this;
        }

        @Override
        @NonNull
        public Builder sizeBetween(@NonNull String attr, int min, int max) {
            super.sizeBetween(attr, min, max);
            return this;
        }

        // endregion

        // region Attribute Existence

        @Override
        @NonNull
        public Builder exists(@NonNull String attr) {
            super.exists(attr);
            return this;
        }

        @Override
        @NonNull
        public Builder notExists(@NonNull String attr) {
            super.notExists(attr);
            return this;
        }

        // endregion

        // region Attribute Type

        @Override
        @NonNull
        public Builder attributeType(@NonNull String attr, FilterExpression.AttributeType type) {
            super.attributeType(attr, type);
            return this;
        }

        // endregion

        // region BETWEEN, IN

        @Override
        @NonNull
        public Builder between(@NonNull String attr, @NonNull Object low, @NonNull Object high) {
            super.between(attr, low, high);
            return this;
        }

        @Override
        @NonNull
        public Builder in(@NonNull String attr, @NonNull Object... values) {
            super.in(attr, values);
            return this;
        }

        // endregion

        // region Logical

        @Override
        @NonNull
        public Builder and() {
            super.and();
            return this;
        }

        @Override
        @NonNull
        public Builder or() {
            super.or();
            return this;
        }

        @Override
        @NonNull
        public Builder not() {
            super.not();
            return this;
        }

        /**
         * Wraps the given nested expression in parentheses and merges its
         * expression attribute names and values into this builder.
         *
         * @param nested the expression to wrap in parentheses
         * @return this builder for chaining
         */
        @Override
        @NonNull
        public Builder group(@NonNull FilterExpression nested) {
            super.group(nested);
            return this;
        }

        // endregion

        // region Nested

        @Override
        @NonNull
        public Builder nestedEq(@NonNull String path, @NonNull Object value) {
            super.nestedEq(path, value);
            return this;
        }

        // endregion

        // region Build

        /**
         * Builds the {@link ConditionExpression}.
         *
         * @return a new condition expression with the accumulated conditions
         */
        public @NonNull ConditionExpression build() {
            return new ConditionExpression(this);
        }
    }
}
// endregion
