package com.hogwai.dynamodb.simplified.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses nested attribute paths like {@code address.city} and {@code tags[0].name}.
 * <p>
 * This is a shared utility extracted from the expression builder classes
 * ({@code FilterExpression}, {@code ProjectionExpression}, {@code UpdateExpression}),
 * which all contained identical parsing logic in their {@code addNestedName()} methods.
 * Only the parsing is unified here; the reconstruction (generating {@code #n0}/{:v0}
 * placeholders) differs per expression type and stays in each class.
 */
public final class AttributePathParser {

    private AttributePathParser() {
    }

    /**
     * Parses a dot/bracket path into segments.
     * <p>
     * Example: {@code "tags[0].name"} → {@code [("tags", "[0]"), ("name", null)]}
     *
     * @param path the dotted attribute path
     * @return ordered list of path segments
     */
    public static List<PathSegment> parse(String path) {
        String[] parts = path.split("\\.");
        List<PathSegment> segments = new ArrayList<>();
        for (String part : parts) {
            if (part.contains("[")) {
                String attrName = part.substring(0, part.indexOf('['));
                String indexSuffix = part.substring(part.indexOf('['));
                segments.add(new PathSegment(attrName, indexSuffix));
            } else {
                segments.add(new PathSegment(part, null));
            }
        }
        return segments;
    }
}
