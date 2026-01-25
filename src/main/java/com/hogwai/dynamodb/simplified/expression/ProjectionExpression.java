package com.hogwai.dynamodb.simplified.expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectionExpression {
    private final List<String> projections = new ArrayList<>();
    private final Map<String, String> expressionNames = new HashMap<>();
    private final AtomicInteger nameCounter = new AtomicInteger(0);

    private ProjectionExpression() {}

    public static ProjectionExpression builder() {
        return new ProjectionExpression();
    }

    public ProjectionExpression include(String... attributes) {
        for (String attr : attributes) {
            String key = addName(attr);
            projections.add(key);
        }
        return this;
    }

    public ProjectionExpression includeNested(String path) {
        String nameKey = addNestedName(path);
        projections.add(nameKey);
        return this;
    }

    public ProjectionExpression includeListElement(String attribute, int index) {
        String nameKey = addName(attribute);
        projections.add(nameKey + "[" + index + "]");
        return this;
    }

    private String addName(String attribute) {
        String key = "#p" + nameCounter.getAndIncrement();
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

    public String getExpression() {
        return String.join(", ", projections);
    }

    public Map<String, String> getExpressionNames() {
        return expressionNames;
    }

    public boolean isEmpty() {
        return projections.isEmpty();
    }
}