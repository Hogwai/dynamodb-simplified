package dev.hogwai.dynamodb.simplified.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AttributePathParser")
class AttributePathParserTest {

    @Test
    @DisplayName("parse(\"tags[0].name\") returns two segments with expected names and suffixes")
    void parse_tagsIndexedDotName_returnsExpectedSegments() {
        List<PathSegment> segments = AttributePathParser.parse("tags[0].name");

        assertEquals(2, segments.size());

        PathSegment first = segments.getFirst();
        assertEquals("tags", first.name());
        assertEquals("[0]", first.indexSuffix());
        assertTrue(first.hasIndex());

        PathSegment second = segments.get(1);
        assertEquals("name", second.name());
        assertNull(second.indexSuffix());
        assertFalse(second.hasIndex());
    }

    @Test
    @DisplayName("parse(\"name.\") should return two segments: name and empty string")
    void parse_trailingDot_returnsTwoSegmentsIncludingEmpty() {
        // Preserves the explicit trailing empty segment.
        List<PathSegment> segments = AttributePathParser.parse("name.");

        assertEquals(2, segments.size());

        PathSegment first = segments.getFirst();
        assertEquals("name", first.name());
        assertNull(first.indexSuffix());

        PathSegment second = segments.get(1);
        assertEquals("", second.name());
        assertNull(second.indexSuffix());
    }
}
