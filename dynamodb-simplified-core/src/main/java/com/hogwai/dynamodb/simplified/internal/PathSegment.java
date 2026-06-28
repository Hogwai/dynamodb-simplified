package com.hogwai.dynamodb.simplified.internal;

/**
 * A single segment in a parsed dotted attribute path.
 * <p>
 * For a path like {@code tags[0].name}, the segments are:
 * <ol>
 *   <li>{@code name="tags", indexSuffix="[0]"} (indexed attribute)</li>
 *   <li>{@code name="name", indexSuffix=null} (plain attribute)</li>
 * </ol>
 *
 * @param name        the attribute name
 * @param indexSuffix the bracket-index suffix (e.g., {@code [0]}), or {@code null}
 */
public record PathSegment(String name, String indexSuffix) {

    /**
     * Returns the attribute name.
     *
     * @return the attribute name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the bracket-index suffix (e.g., {@code [0]}), or {@code null}.
     *
     * @return the index suffix, or {@code null} if this segment has no index
     */
    @Override
    public String indexSuffix() {
        return indexSuffix;
    }

    /**
     * Returns {@code true} if this segment has a list index suffix.
     *
     * @return {@code true} if this is an indexed segment
     */
    public boolean hasIndex() {
        return indexSuffix != null;
    }
}
