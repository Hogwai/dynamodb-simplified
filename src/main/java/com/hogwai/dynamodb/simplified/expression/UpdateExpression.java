package com.hogwai.dynamodb.simplified.expression;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UpdateExpression {
    private final List<String> setActions = new ArrayList<>();
    private final List<String> removeActions = new ArrayList<>();
    private final List<String> addActions = new ArrayList<>();
    private final List<String> deleteActions = new ArrayList<>();

    private final Map<String, String> expressionNames = new HashMap<>();
    private final Map<String, AttributeValue> expressionValues = new HashMap<>();
    private final AtomicInteger nameCounter = new AtomicInteger(0);
    private final AtomicInteger valueCounter = new AtomicInteger(0);

    private UpdateExpression() {}

    public static UpdateExpression builder() {
        return new UpdateExpression();
    }

    // ============ SET Operations ============

    public UpdateExpression set(String attribute, Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    public UpdateExpression setIfNotExists(String attribute, Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = if_not_exists(%s, %s)".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    public UpdateExpression increment(String attribute, Number delta) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(delta.toString()).build());
        setActions.add("%s = %s + %s".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    public UpdateExpression decrement(String attribute, Number delta) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(delta.toString()).build());
        setActions.add("%s = %s - %s".formatted(nameKey, nameKey, valueKey));
        return this;
    }

    public UpdateExpression appendToList(String attribute, List<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        String emptyListKey = addValue(AttributeValue.builder().l(Collections.emptyList()).build());
        setActions.add("%s = list_append(if_not_exists(%s, %s), %s)".formatted(nameKey, nameKey, emptyListKey, valueKey));
        return this;
    }

    public UpdateExpression prependToList(String attribute, List<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        String emptyListKey = addValue(AttributeValue.builder().l(Collections.emptyList()).build());
        setActions.add("%s = list_append(%s, if_not_exists(%s, %s))".formatted(
                nameKey, valueKey, nameKey, emptyListKey));
        return this;
    }

    public UpdateExpression setListElement(String attribute, int index, Object value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s[%d] = %s".formatted(nameKey, index, valueKey));
        return this;
    }

    public UpdateExpression setNested(String path, Object value) {
        String nameKey = addNestedName(path);
        String valueKey = addValue(toAttributeValue(value));
        setActions.add("%s = %s".formatted(nameKey, valueKey));
        return this;
    }

    // ============ REMOVE Operations ============

    public UpdateExpression remove(String... attributes) {
        for (String attr : attributes) {
            removeActions.add(addName(attr));
        }
        return this;
    }

    public UpdateExpression removeListElement(String attribute, int index) {
        String nameKey = addName(attribute);
        removeActions.add("%s[%d]".formatted(nameKey, index));
        return this;
    }

    // ============ ADD Operations (pour sets et numbers) ============

    public UpdateExpression addToSet(String attribute, Set<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        addActions.add("%s %s".formatted(nameKey, valueKey));
        return this;
    }

    public UpdateExpression addNumber(String attribute, Number value) {
        String nameKey = addName(attribute);
        String valueKey = addValue(AttributeValue.builder().n(value.toString()).build());
        addActions.add("%s %s".formatted(nameKey, valueKey));
        return this;
    }

    // ============ DELETE Operations (pour sets) ============

    public UpdateExpression deleteFromSet(String attribute, Set<?> values) {
        String nameKey = addName(attribute);
        String valueKey = addValue(toAttributeValue(values));
        deleteActions.add("%s %s".formatted(nameKey, valueKey));
        return this;
    }

    // ============ Helper methods ============

    private String addName(String attribute) {
        String key = "#u" + nameCounter.getAndIncrement();
        expressionNames.put(key, attribute);
        return key;
    }

    private String addNestedName(String path) {
        String[] parts = path.split("\\.");
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
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
        String key = ":u" + valueCounter.getAndIncrement();
        expressionValues.put(key, value);
        return key;
    }

    private AttributeValue toAttributeValue(Object value) {
        switch (value) {
            case null -> {
                return AttributeValue.builder()
                                     .nul(true)
                                     .build();
            }
            case String strValue -> {
                return AttributeValue.builder()
                                     .s(strValue)
                                     .build();
            }
            case Number ignored -> {
                return AttributeValue.builder()
                                     .n(value.toString())
                                     .build();
            }
            case Boolean boolValue -> {
                return AttributeValue.builder()
                                     .bool(boolValue)
                                     .build();
            }
            case List ignored -> {
                List<AttributeValue> list = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    list.add(toAttributeValue(item));
                }
                return AttributeValue.builder()
                                     .l(list)
                                     .build();
            }
            case Map ignored -> {
                Map<String, AttributeValue> map = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(entry.getKey()
                                 .toString(), toAttributeValue(entry.getValue()));
                }
                return AttributeValue.builder()
                                     .m(map)
                                     .build();
            }
            case Set set when !set.isEmpty() -> {
                Object first = set.iterator()
                                  .next();
                if (first instanceof String) {
                    return AttributeValue.builder()
                                         .ss(new ArrayList<>(set))
                                         .build();
                }
                if (first instanceof Number) {
                    List<String> numStrings = new ArrayList<>();
                    for (Object num : set) {
                        numStrings.add(num.toString());
                    }
                    return AttributeValue.builder()
                                         .ns(numStrings)
                                         .build();
                }
            }
            default -> {
                // Default
            }
        }
        throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }

    public String getExpression() {
        StringBuilder builder = new StringBuilder();

        if (!setActions.isEmpty()) {
            builder.append("SET ").append(String.join(", ", setActions));
        }
        if (!removeActions.isEmpty()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append("REMOVE ").append(String.join(", ", removeActions));
        }
        if (!addActions.isEmpty()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append("ADD ").append(String.join(", ", addActions));
        }
        if (!deleteActions.isEmpty()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append("DELETE ").append(String.join(", ", deleteActions));
        }

        return builder.toString();
    }

    public Map<String, String> getExpressionNames() {
        return expressionNames;
    }

    public Map<String, AttributeValue> getExpressionValues() {
        return expressionValues;
    }

    public boolean isEmpty() {
        return setActions.isEmpty() && removeActions.isEmpty()
                && addActions.isEmpty() && deleteActions.isEmpty();
    }
}