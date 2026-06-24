package com.hogwai.dynamodb.simplified.expression;

import com.hogwai.dynamodb.simplified.internal.AttributePathParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;



/**
 * Fluent builder for constructing DynamoDB projection expressions as part of
 * the DynamoDB Simplified library.
 * <p>
 * A projection expression specifies the attributes to retrieve from a DynamoDB
 * table or index, reducing the data returned by {@code Scan}, {@code Query},
 * or {@code GetItem} operations. This builder automatically maps attribute
 * names to expression attribute name placeholders ({@code #p0}, {@code #p1}, ...)
 * to avoid reserved word conflicts. Supports top-level attributes, nested
 * attribute paths (e.g., {@code address.city}), and list element access
 * (e.g., {@code items[0]}).
 */
public class ProjectionExpression {
    private final List<String> projections = new ArrayList<>();
    private final List<String> rawProjections = new ArrayList<>();
    private final Map<String, String> expressionNames = new HashMap<>();
    private int nameCounter = 0;

    private ProjectionExpression() {
    }

    /**
     * Creates a new {@link ProjectionExpression} builder.
     *
     * @return a new empty {@code ProjectionExpression}
     */
    @NonNull
    public static ProjectionExpression builder() {
        return new ProjectionExpression();
    }

    /**
     * Includes one or more top-level attribute names in the projection.
     * Each attribute is automatically mapped to an expression attribute name
     * placeholder.
     *
     * @param attributes one or more attribute names to include
     * @return this builder for chaining
     */
    @NonNull
    public ProjectionExpression include(@NonNull String... attributes) {
        for (String attr : attributes) {
            String key = addName(attr);
            projections.add(key);
            rawProjections.add(attr);
        }
        return this;
    }

    @NonNull
    public ProjectionExpression includeNested(@NonNull String path) {
        String nameKey = addNestedName(path);
        projections.add(nameKey);
        rawProjections.add(path);
        return this;
    }

    @NonNull
    public ProjectionExpression includeListElement(@NonNull String attribute, int index) {
        String nameKey = addName(attribute);
        projections.add(nameKey + "[" + index + "]");
        rawProjections.add(attribute + "[" + index + "]");
        return this;
    }

    private String addName(@NonNull String attribute) {
        String key = "#p" + nameCounter;
        nameCounter++;
        expressionNames.put(key, attribute);
        return key;
    }

    private String addNestedName(@NonNull String path) {
        return AttributePathParser.rebuildNestedPath(path, this::addName);
    }

    /**
     * Returns the built projection expression string, a comma-separated list
     * of attribute references (e.g., {@code "#p0, #p1, #p2[0]"}).
     *
     * @return the projection expression string
     */
    @NonNull
    public String getExpression() {
        return String.join(", ", projections);
    }

    /**
     * Returns the map of expression attribute names used in this expression.
     * Keys are the generated placeholders (e.g., {@code #p0}) and values are
     * the original attribute names.
     *
     * @return the expression attribute names map
     */
    @NonNull
    public Map<String, String> getExpressionNames() {
        return expressionNames;
    }

    /**
     * Returns whether no attributes have been added to this projection yet.
     *
     * @return {@code true} if the projection is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return projections.isEmpty();
    }

    /**
     * Returns the raw attribute paths as they were added by the user.
     * <p>
     * These are the original attribute names and paths (e.g.,
     * {@code "address.city"}) rather than the expression placeholder forms
     * (e.g., {@code "#p0.#p1"}). Use this when passing projection attributes
     * directly to the DynamoDB SDK's {@code attributesToProject} method.
     *
     * @return list of raw attribute paths
     */
    @NonNull
    public List<String> getProjectedAttributes() {
        return List.copyOf(rawProjections);
    }
}
