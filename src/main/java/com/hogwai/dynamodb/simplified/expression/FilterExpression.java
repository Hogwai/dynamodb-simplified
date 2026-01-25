package com.hogwai.dynamodb.simplified.expression;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

public class FilterExpression {
    private final StringBuilder expression = new StringBuilder();
    private final Map<String, String> expressionNames = new HashMap<>();
    private final Map<String, AttributeValue> expressionValues = new HashMap<>();
    private final AtomicInteger nameCounter = new AtomicInteger(0);
    private final AtomicInteger valueCounter = new AtomicInteger(0);

    private FilterExpression() {}

    public static FilterExpression builder() {
        return new FilterExpression();
    }

    // ============ Comparaisons de base ============

    public FilterExpression eq(String attribute, Object value) {
        return addCondition(attribute, "=", value);
    }

    public FilterExpression ne(String attribute, Object value) {
        return addCondition(attribute, "<>", value);
    }

    public FilterExpression lt(String attribute, Object value) {
        return addCondition(attribute, "<", value);
    }

    public FilterExpression le(String attribute, Object value) {
        return addCondition(attribute, "<=", value);
    }

    public FilterExpression gt(String attribute, Object value) {
        return addCondition(attribute, ">", value);
    }

    public FilterExpression ge(String attribute, Object value) {
        return addCondition(attribute, ">=", value);
    }

    // ============ Opérations sur les chaînes ============

    public FilterExpression beginsWith(String attribute, String prefix) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().s(prefix).build());
        appendToExpression("begins_with(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    public FilterExpression contains(String attribute, Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("contains(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    // ============ Opérations SIZE (côté serveur) ============

    public FilterExpression sizeEq(String attribute, int size) {
        return addSizeCondition(attribute, "=", size);
    }

    public FilterExpression sizeLt(String attribute, int size) {
        return addSizeCondition(attribute, "<", size);
    }

    public FilterExpression sizeLe(String attribute, int size) {
        return addSizeCondition(attribute, "<=", size);
    }

    public FilterExpression sizeGt(String attribute, int size) {
        return addSizeCondition(attribute, ">", size);
    }

    public FilterExpression sizeGe(String attribute, int size) {
        return addSizeCondition(attribute, ">=", size);
    }

    public FilterExpression sizeBetween(String attribute, int minSize, int maxSize) {
        String nameKey = addName(attribute);
        String minValueKey = addValue(AttributeValue.builder().n(String.valueOf(minSize)).build());
        String maxValueKey = addValue(AttributeValue.builder().n(String.valueOf(maxSize)).build());
        appendToExpression("size(%s) BETWEEN %s AND %s".formatted(nameKey, minValueKey, maxValueKey));
        return this;
    }

    // ============ Existence d'attributs ============

    public FilterExpression exists(String attribute) {
        String nameKey = addName(attribute);
        appendToExpression("attribute_exists(%s)".formatted(nameKey));
        return this;
    }

    public FilterExpression notExists(String attribute) {
        String nameKey = addName(attribute);
        appendToExpression("attribute_not_exists(%s)".formatted(nameKey));
        return this;
    }

    // ============ Type d'attribut ============

    public FilterExpression attributeType(String attribute, AttributeType type) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().s(type.getCode()).build());
        appendToExpression("attribute_type(%s, %s)".formatted(nameKey, valueKey));
        return this;
    }

    // ============ BETWEEN ============

    public FilterExpression between(String attribute, Object low, Object high) {
        String nameKey = addName(attribute);
        String lowKey = addValue(toAttributeValue(low));
        String highKey = addValue(toAttributeValue(high));
        appendToExpression("%s BETWEEN %s AND %s".formatted(nameKey, lowKey, highKey));
        return this;
    }

    // ============ IN ============

    public FilterExpression in(String attribute, Object... values) {
        String nameKey = addName(attribute);
        StringJoiner joiner = new StringJoiner(", ");
        for (Object value : values) {
            joiner.add(addValue(toAttributeValue(value)));
        }
        appendToExpression("%s IN (%s)".formatted(nameKey, joiner.toString()));
        return this;
    }

    // ============ Opérateurs logiques ============

    public FilterExpression and() {
        expression.append(" AND ");
        return this;
    }

    public FilterExpression or() {
        expression.append(" OR ");
        return this;
    }

    public FilterExpression not() {
        expression.append("NOT ");
        return this;
    }

    public FilterExpression group(FilterExpression nested) {
        this.expressionNames.putAll(nested.expressionNames);
        this.expressionValues.putAll(nested.expressionValues);
        appendToExpression("(" + nested.expression + ")");
        return this;
    }

    // ============ Accès aux attributs imbriqués ============

    public FilterExpression nestedEq(String path, Object value) {
        String nameKey = addNestedName(path);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    // ============ Méthodes utilitaires ============

    private FilterExpression addCondition(String attribute, String operator, Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        appendToExpression("%s %s %s".formatted(nameKey, operator, valueKey));
        return this;
    }

    private FilterExpression addSizeCondition(String attribute, String operator, int size) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(String.valueOf(size)).build());
        appendToExpression("size(%s) %s %s".formatted(nameKey, operator, valueKey));
        return this;
    }

    private String addName(String attribute) {
        String key = "#n" + nameCounter.getAndIncrement();
        expressionNames.put(key, attribute);
        return key;
    }

    private String addNestedName(String path) {
        String[] parts = path.split("\\.");
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            // Gestion des index de liste (ex: items[0])
            if (part.contains("[")) {
                String attrName = part.substring(0, part.indexOf('['));
                String index = part.substring(part.indexOf('['));
                joiner.add(addName(attrName) + index);
            } else {
                joiner.add(addName(part));
            }
        }
        return joiner.toString();
    }

    private String addValue(AttributeValue value) {
        String key = ":v" + valueCounter.getAndIncrement();
        expressionValues.put(key, value);
        return key;
    }

    private void appendToExpression(String part) {
        expression.append(part);
    }

    private AttributeValue toAttributeValue(Object value) {
        if (value == null) {
            return AttributeValue.builder().nul(true).build();
        }
        if (value instanceof String valueString) {
            return AttributeValue.builder().s(valueString).build();
        }
        if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        }
        if (value instanceof Boolean valueBool) {
            return AttributeValue.builder().bool(valueBool).build();
        }
        if (value instanceof byte[] valueByteArray) {
            return AttributeValue.builder().b(software.amazon.awssdk.core.SdkBytes.fromByteArray(valueByteArray)).build();
        }
        if (value instanceof List) {
            List<AttributeValue> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                list.add(toAttributeValue(item));
            }
            return AttributeValue.builder().l(list).build();
        }
        if (value instanceof Set<?> set) {
            if (!set.isEmpty()) {
                Object first = set.iterator().next();
                if (first instanceof String) {
                    return AttributeValue.builder().ss((Collection<String>) set).build();
                }
                if (first instanceof Number) {
                    Set<String> numStrings = new HashSet<>();
                    for (Object num : set) {
                        numStrings.add(num.toString());
                    }
                    return AttributeValue.builder().ns(numStrings).build();
                }
            }
        }
        throw new IllegalArgumentException("Unsupported type: %s".formatted(value.getClass()));
    }

    public String getExpression() {
        return expression.toString();
    }

    public Map<String, String> getExpressionNames() {
        return expressionNames;
    }

    public Map<String, AttributeValue> getExpressionValues() {
        return expressionValues;
    }

    public boolean isEmpty() {
        return expression.isEmpty();
    }

    public enum AttributeType {
        STRING("S"),
        NUMBER("N"),
        BINARY("B"),
        STRING_SET("SS"),
        NUMBER_SET("NS"),
        BINARY_SET("BS"),
        MAP("M"),
        LIST("L"),
        NULL("NULL"),
        BOOLEAN("BOOL");

        private final String code;

        AttributeType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}