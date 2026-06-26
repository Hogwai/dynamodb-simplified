package com.hogwai.dynamodb.simplified.internal;

/**
 * A single segment in a parsed dotted attribute path.
 * <p>
 * For a path like {@code tags[0].name}, the segments are:
 * <ol>
 *   <li>{@code name="tags", indexSuffix="[0]"} (indexed attribute)</li>
 *   <li>{@code name="name", indexSuffix=null} (plain attribute)</li>
 * </ol>
 */
public record PathSegment(String name, String indexSuffix) {

    /**
     * Returns the attribute name.
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the bracket-index suffix (e.g., {@code [0]}), or {@code null}.
     */
    @Override
    public String indexSuffix() {
        return indexSuffix;
    }

    /**
     * Returns {@code true} if this segment has a list index suffix.
     */
    public boolean hasIndex() {
        return indexSuffix != null;
    }
}
