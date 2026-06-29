package dev.hogwai.dynamodb.simplified.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

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

    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

    private AttributePathParser() {
    }

    /**
     * Parses a dot/bracket path into segments.
     * <p>
     * Example: {@code "tags[0].name"} -> {@code [("tags", "[0]"), ("name", null)]}
     *
     * @param path the dotted attribute path
     * @return ordered list of path segments
     */
    public static List<PathSegment> parse(String path) {
        String[] parts = DOT_PATTERN.split(path);
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

    /**
     * Reconstructs a nested attribute path using a name resolver function.
     * <p>
     * Each path segment is resolved via the name resolver, and index suffixes
     * (e.g., {@code [0]}) are appended when present. Segments are joined with
     * dots.
     * <p>
     * Example: {@code "tags[0].name"} with resolver {@code s -> "#n0"} ->
     * {@code "#n0[0].\#p0"}
     *
     * @param path         the dotted attribute path
     * @param nameResolver function that resolves a segment name to a placeholder
     * @return the reconstructed path string with resolved placeholders
     */
    public static String rebuildNestedPath(String path, UnaryOperator<String> nameResolver) {
        List<PathSegment> segments = parse(path);
        StringJoiner joiner = new StringJoiner(".");
        for (PathSegment segment : segments) {
            String resolved = nameResolver.apply(segment.name());
            if (segment.hasIndex()) {
                joiner.add(resolved + segment.indexSuffix());
            } else {
                joiner.add(resolved);
            }
        }
        return joiner.toString();
    }
}
