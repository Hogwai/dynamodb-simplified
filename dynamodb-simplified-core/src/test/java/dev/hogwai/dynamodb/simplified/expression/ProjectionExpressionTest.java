package dev.hogwai.dynamodb.simplified.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectionExpression")
class ProjectionExpressionTest {

    @Nested
    @DisplayName("isEmpty")
    class IsEmpty {

        @Test
        @DisplayName("returns true for a fresh builder")
        void freshBuilder_isEmpty() {
            ProjectionExpression expr = ProjectionExpression.builder();
            assertTrue(expr.isEmpty());
        }

        @Test
        @DisplayName("returns false after a single attribute is included")
        void afterInclude_isNotEmpty() {
            ProjectionExpression expr = ProjectionExpression.builder().include("name");
            assertFalse(expr.isEmpty());
        }

        @Test
        @DisplayName("returns false after a nested path is included")
        void afterIncludeNested_isNotEmpty() {
            ProjectionExpression expr = ProjectionExpression.builder().includeNested("address.city");
            assertFalse(expr.isEmpty());
        }

        @Test
        @DisplayName("returns false after a list element is included")
        void afterIncludeListElement_isNotEmpty() {
            ProjectionExpression expr = ProjectionExpression.builder().includeListElement("tags", 0);
            assertFalse(expr.isEmpty());
        }
    }

    @Nested
    @DisplayName("include (single attribute)")
    class IncludeSingle {

        @Test
        @DisplayName("single attribute produces #p0 placeholder and correct mapping")
        void singleAttribute() {
            ProjectionExpression expr = ProjectionExpression.builder().include("name");

            assertEquals("#p0", expr.getExpression());
            assertEquals(Map.of("#p0", "name"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("reserved word attribute is safely aliased to #p0")
        void reservedWordAttribute() {
            ProjectionExpression expr = ProjectionExpression.builder().include("status");

            assertEquals("#p0", expr.getExpression());
            assertEquals(Map.of("#p0", "status"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("two separate includes each get their own alias")
        void twoSeparateCalls() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("name")
                    .include("email");

            assertEquals("#p0, #p1", expr.getExpression());
            assertEquals(Map.of("#p0", "name", "#p1", "email"), expr.getExpressionNames());
        }
    }

    @Nested
    @DisplayName("include (multiple attributes)")
    class IncludeMultiple {

        @Test
        @DisplayName("varargs produces sequential placeholders")
        void multipleAttributes() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("id", "name", "email");

            assertEquals("#p0, #p1, #p2", expr.getExpression());
            assertEquals(Map.of("#p0", "id", "#p1", "name", "#p2", "email"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("single-element varargs still increments counter")
        void singleElementVarargs() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("a")
                    .include("b", "c");

            assertEquals("#p0, #p1, #p2", expr.getExpression());
            assertEquals(Map.of("#p0", "a", "#p1", "b", "#p2", "c"), expr.getExpressionNames());
        }
    }

    @Nested
    @DisplayName("includeNested")
    class IncludeNested {

        @Test
        @DisplayName("two-level path produces dotted placeholders")
        void twoLevelPath() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeNested("address.city");

            assertEquals("#p0.#p1", expr.getExpression());
            assertEquals(Map.of("#p0", "address", "#p1", "city"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("three-level path produces three dotted placeholders")
        void threeLevelPath() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeNested("a.b.c");

            assertEquals("#p0.#p1.#p2", expr.getExpression());
            assertEquals(Map.of("#p0", "a", "#p1", "b", "#p2", "c"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("path with list index bracket notation")
        void pathWithListIndex() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeNested("items[0].name");

            assertEquals("#p0[0].#p1", expr.getExpression());
            assertEquals(Map.of("#p0", "items", "#p1", "name"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("path with multiple list indices")
        void pathWithMultipleListIndices() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeNested("matrix[1].values[2]");

            assertEquals("#p0[1].#p1[2]", expr.getExpression());
            assertEquals(Map.of("#p0", "matrix", "#p1", "values"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("index at start of single segment path")
        void singleSegmentWithIndex() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeNested("tags[3]");

            assertEquals("#p0[3]", expr.getExpression());
            assertEquals(Map.of("#p0", "tags"), expr.getExpressionNames());
        }
    }

    @Nested
    @DisplayName("includeListElement")
    class IncludeListElement {

        @Test
        @DisplayName("list element with index 0")
        void indexZero() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeListElement("tags", 0);

            assertEquals("#p0[0]", expr.getExpression());
            assertEquals(Map.of("#p0", "tags"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("list element with positive index")
        void positiveIndex() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeListElement("tags", 1);

            assertEquals("#p0[1]", expr.getExpression());
            assertEquals(Map.of("#p0", "tags"), expr.getExpressionNames());
        }

        @Test
        @DisplayName("multiple list elements from different attributes")
        void multipleListElements() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .includeListElement("tags", 0)
                    .includeListElement("ratings", 2);

            assertEquals("#p0[0], #p1[2]", expr.getExpression());
            assertEquals(Map.of("#p0", "tags", "#p1", "ratings"), expr.getExpressionNames());
        }
    }

    @Nested
    @DisplayName("combined expressions")
    class CombinedExpressions {

        @Test
        @DisplayName("include, includeNested, and includeListElement produce comma-separated expression")
        void allThreeCombined() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("id")
                    .includeNested("address.city")
                    .includeListElement("tags", 1);

            assertEquals("#p0, #p1.#p2, #p3[1]", expr.getExpression());
            assertEquals(Map.of(
                    "#p0", "id",
                    "#p1", "address",
                    "#p2", "city",
                    "#p3", "tags"
            ), expr.getExpressionNames());
        }

        @Test
        @DisplayName("multiple includes in sequence all appear in order")
        void sequenceOrder() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("a")
                    .include("b")
                    .includeNested("c.d")
                    .includeListElement("e", 0)
                    .include("f");

            assertEquals("#p0, #p1, #p2.#p3, #p4[0], #p5", expr.getExpression());
        }
    }

    @Nested
    @DisplayName("getExpressionNames")
    class ExpressionNames {

        @Test
        @DisplayName("returns all mappings for mixed includes")
        void allMappings() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("name")
                    .includeNested("address.city")
                    .includeListElement("tags", 0);

            Map<String, String> expected = Map.of(
                    "#p0", "name",
                    "#p1", "address",
                    "#p2", "city",
                    "#p3", "tags"
            );
            assertEquals(expected, expr.getExpressionNames());
        }

        @Test
        @DisplayName("returns empty map for fresh builder")
        void freshBuilder() {
            ProjectionExpression expr = ProjectionExpression.builder();
            assertTrue(expr.getExpressionNames().isEmpty());
        }

        @Test
        @DisplayName("duplicate attribute names each get a unique placeholder")
        void duplicateAttributes() {
            ProjectionExpression expr = ProjectionExpression.builder()
                    .include("name")
                    .include("name");

            assertEquals(Map.of("#p0", "name", "#p1", "name"), expr.getExpressionNames());
        }
    }

    @Nested
    @DisplayName("builder independence")
    class BuilderIndependence {

        @Test
        @DisplayName("two builders start with independent name counters")
        void independentCounters() {
            ProjectionExpression builder1 = ProjectionExpression.builder();
            ProjectionExpression builder2 = ProjectionExpression.builder();

            builder1.include("alpha");
            builder2.include("beta");

            assertEquals("#p0", builder1.getExpression());
            assertEquals(Map.of("#p0", "alpha"), builder1.getExpressionNames());

            assertEquals("#p0", builder2.getExpression());
            assertEquals(Map.of("#p0", "beta"), builder2.getExpressionNames());
        }

        @Test
        @DisplayName("mutating one builder does not affect the other")
        void noCrossContamination() {
            ProjectionExpression builder1 = ProjectionExpression.builder().include("a");
            ProjectionExpression builder2 = ProjectionExpression.builder().include("b");

            // Add more to builder1 only
            builder1.include("c");

            assertEquals("#p0, #p1", builder1.getExpression());
            assertEquals("#p0", builder2.getExpression());
        }
    }
}
