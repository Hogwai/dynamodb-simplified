package com.hogwai.dynamodb.simplified.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilterExpression")
class FilterExpressionTest {

    // ============ isEmpty ============

    @Test
    @DisplayName("isEmpty returns true for fresh builder, false after adding condition")
    void isEmpty() {
        FilterExpression fe = FilterExpression.builder();
        assertTrue(fe.isEmpty());
        assertEquals("", fe.getExpression());
        assertTrue(fe.getExpressionNames().isEmpty());
        assertTrue(fe.getExpressionValues().isEmpty());

        fe.eq("name", "Alice");
        assertFalse(fe.isEmpty());
    }

    // ============ Basic Comparisons ============

    @Test
    @DisplayName("eq builds correct equality expression")
    void eq() {
        FilterExpression fe = FilterExpression.builder().eq("name", "Alice");
        assertEquals("#n0 = :v0", fe.getExpression());
        assertEquals(Map.of("#n0", "name"), fe.getExpressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("Alice").build()), fe.getExpressionValues());
    }

    @Test
    @DisplayName("ne builds correct not-equal expression")
    void ne() {
        FilterExpression fe = FilterExpression.builder().ne("status", "active");
        assertEquals("#n0 <> :v0", fe.getExpression());
        assertEquals(Map.of("#n0", "status"), fe.getExpressionNames());
        assertEquals(AttributeValue.builder().s("active").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("gt builds correct greater-than expression with numeric value")
    void gt() {
        FilterExpression fe = FilterExpression.builder().gt("age", 18);
        assertEquals("#n0 > :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("18").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("ge builds correct greater-than-or-equal expression")
    void ge() {
        FilterExpression fe = FilterExpression.builder().ge("age", 21);
        assertEquals("#n0 >= :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("21").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("lt builds correct less-than expression")
    void lt() {
        FilterExpression fe = FilterExpression.builder().lt("age", 65);
        assertEquals("#n0 < :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("65").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("le builds correct less-than-or-equal expression")
    void le() {
        FilterExpression fe = FilterExpression.builder().le("age", 99);
        assertEquals("#n0 <= :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("99").build(), fe.getExpressionValues().get(":v0"));
    }

    // ============ String Operations ============

    @Test
    @DisplayName("beginsWith builds correct begins_with function expression")
    void beginsWith() {
        FilterExpression fe = FilterExpression.builder().beginsWith("name", "A");
        assertEquals("begins_with(#n0, :v0)", fe.getExpression());
        assertEquals(Map.of("#n0", "name"), fe.getExpressionNames());
        assertEquals(AttributeValue.builder().s("A").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("beginsWith preserves dotted attribute name as single name key")
    void beginsWith_dottedAttribute() {
        FilterExpression fe = FilterExpression.builder()
                .beginsWith("user.name", "A");
        // addName treats "user.name" as a single attribute name (no splitting)
        assertEquals("begins_with(#n0, :v0)", fe.getExpression());
        assertEquals(Map.of("#n0", "user.name"), fe.getExpressionNames());
    }

    @Test
    @DisplayName("contains builds correct contains function expression with string value")
    void contains() {
        FilterExpression fe = FilterExpression.builder()
                .contains("description", "urgent");
        assertEquals("contains(#n0, :v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("urgent").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("contains with numeric value produces numeric AttributeValue")
    void contains_numericValue() {
        FilterExpression fe = FilterExpression.builder()
                .contains("scores", 99);
        assertEquals("contains(#n0, :v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().n("99").build(), fe.getExpressionValues().get(":v0"));
    }

    // ============ Server-side SIZE Operations ============

    @Test
    @DisplayName("sizeEq builds correct size() equality expression")
    void sizeEq() {
        FilterExpression fe = FilterExpression.builder().sizeEq("items", 5);
        assertEquals("size(#n0) = :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("5").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeGt builds correct size() greater-than expression")
    void sizeGt() {
        FilterExpression fe = FilterExpression.builder().sizeGt("items", 0);
        assertEquals("size(#n0) > :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("0").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeGe builds correct size() greater-than-or-equal expression")
    void sizeGe() {
        FilterExpression fe = FilterExpression.builder().sizeGe("items", 1);
        assertEquals("size(#n0) >= :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("1").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeLt builds correct size() less-than expression")
    void sizeLt() {
        FilterExpression fe = FilterExpression.builder().sizeLt("items", 10);
        assertEquals("size(#n0) < :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("10").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeLe builds correct size() less-than-or-equal expression")
    void sizeLe() {
        FilterExpression fe = FilterExpression.builder().sizeLe("items", 100);
        assertEquals("size(#n0) <= :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().n("100").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeBetween builds correct size() BETWEEN expression with min and max")
    void sizeBetween() {
        FilterExpression fe = FilterExpression.builder().sizeBetween("items", 1, 10);
        assertEquals("size(#n0) BETWEEN :v0 AND :v1", fe.getExpression());
        assertEquals(AttributeValue.builder().n("1").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("10").build(), fe.getExpressionValues().get(":v1"));
    }

    // ============ Attribute Existence ============

    @Test
    @DisplayName("exists builds correct attribute_exists expression")
    void exists() {
        FilterExpression fe = FilterExpression.builder().exists("email");
        assertEquals("attribute_exists(#n0)", fe.getExpression());
        assertEquals(Map.of("#n0", "email"), fe.getExpressionNames());
        assertTrue(fe.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("notExists builds correct attribute_not_exists expression")
    void notExists() {
        FilterExpression fe = FilterExpression.builder().notExists("email");
        assertEquals("attribute_not_exists(#n0)", fe.getExpression());
        assertEquals(Map.of("#n0", "email"), fe.getExpressionNames());
        assertTrue(fe.getExpressionValues().isEmpty());
    }

    // ============ Attribute Type ============

    @Test
    @DisplayName("attributeType with STRING produces attribute_type expression with type code 'S'")
    void attributeType_STRING() {
        FilterExpression fe = FilterExpression.builder()
                .attributeType("data", FilterExpression.AttributeType.STRING);
        assertEquals("attribute_type(#n0, :v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("S").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("attributeType with all supported types produces correct type codes")
    void attributeType_allTypes() {
        assertAttributeType("data", FilterExpression.AttributeType.STRING, "S");
        assertAttributeType("data", FilterExpression.AttributeType.NUMBER, "N");
        assertAttributeType("data", FilterExpression.AttributeType.BINARY, "B");
        assertAttributeType("data", FilterExpression.AttributeType.BOOLEAN, "BOOL");
        assertAttributeType("data", FilterExpression.AttributeType.NULL, "NULL");
        assertAttributeType("data", FilterExpression.AttributeType.STRING_SET, "SS");
        assertAttributeType("data", FilterExpression.AttributeType.NUMBER_SET, "NS");
        assertAttributeType("data", FilterExpression.AttributeType.BINARY_SET, "BS");
        assertAttributeType("data", FilterExpression.AttributeType.LIST, "L");
        assertAttributeType("data", FilterExpression.AttributeType.MAP, "M");
    }

    private static void assertAttributeType(String attr, FilterExpression.AttributeType type, String expectedCode) {
        FilterExpression fe = FilterExpression.builder().attributeType(attr, type);
        assertEquals("attribute_type(#n0, :v0)", fe.getExpression(),
                "attributeType with " + type + " should produce expression");
        assertEquals(AttributeValue.builder().s(expectedCode).build(), fe.getExpressionValues().get(":v0"),
                "type code for " + type + " should be '" + expectedCode + "'");
    }

    // ============ BETWEEN ============

    @Test
    @DisplayName("between with numeric values produces correct BETWEEN expression")
    void between_numeric() {
        FilterExpression fe = FilterExpression.builder().between("age", 18, 65);
        assertEquals("#n0 BETWEEN :v0 AND :v1", fe.getExpression());
        assertEquals(AttributeValue.builder().n("18").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("65").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("between with string values produces correct BETWEEN expression")
    void between_strings() {
        FilterExpression fe = FilterExpression.builder().between("name", "A", "Z");
        assertEquals("#n0 BETWEEN :v0 AND :v1", fe.getExpression());
        assertEquals(AttributeValue.builder().s("A").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("Z").build(), fe.getExpressionValues().get(":v1"));
    }

    // ============ IN ============

    @Test
    @DisplayName("in with string values produces correct IN expression with comma-separated placeholders")
    void in_strings() {
        FilterExpression fe = FilterExpression.builder()
                .in("color", "red", "green", "blue");
        assertEquals("#n0 IN (:v0, :v1, :v2)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("red").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("green").build(), fe.getExpressionValues().get(":v1"));
        assertEquals(AttributeValue.builder().s("blue").build(), fe.getExpressionValues().get(":v2"));
    }

    @Test
    @DisplayName("in with numeric values produces correct IN expression")
    void in_numbers() {
        FilterExpression fe = FilterExpression.builder()
                .in("score", 90, 95, 100);
        assertEquals("#n0 IN (:v0, :v1, :v2)", fe.getExpression());
        assertEquals(AttributeValue.builder().n("90").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("95").build(), fe.getExpressionValues().get(":v1"));
        assertEquals(AttributeValue.builder().n("100").build(), fe.getExpressionValues().get(":v2"));
    }

    @Test
    @DisplayName("in with mixed string and numeric values")
    void in_mixedTypes() {
        FilterExpression fe = FilterExpression.builder()
                .in("attr", "hello", 42);
        assertEquals("#n0 IN (:v0, :v1)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("hello").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("42").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("in with single value produces IN with one placeholder")
    void in_singleValue() {
        FilterExpression fe = FilterExpression.builder()
                .in("status", "active");
        assertEquals("#n0 IN (:v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("active").build(), fe.getExpressionValues().get(":v0"));
    }

    // ============ Logical Operators: and / or / not ============

    @Test
    @DisplayName("and() joins two conditions with AND operator")
    void and_chain() {
        FilterExpression fe = FilterExpression.builder()
                .eq("name", "Alice")
                .and()
                .eq("age", 30);
        assertEquals("#n0 = :v0 AND #n1 = :v1", fe.getExpression());
        assertEquals(Map.of("#n0", "name", "#n1", "age"), fe.getExpressionNames());
        assertEquals(AttributeValue.builder().s("Alice").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("30").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("or() joins two conditions with OR operator")
    void or_chain() {
        FilterExpression fe = FilterExpression.builder()
                .eq("status", "active")
                .or()
                .eq("status", "pending");
        assertEquals("#n0 = :v0 OR #n1 = :v1", fe.getExpression());
        assertEquals(AttributeValue.builder().s("active").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("pending").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("not() prepends NOT before a condition (no parentheses)")
    void not_operator() {
        FilterExpression fe = FilterExpression.builder()
                .not()
                .eq("active", true);
        assertEquals("NOT #n0 = :v0", fe.getExpression());
        assertEquals(AttributeValue.builder().bool(true).build(), fe.getExpressionValues().get(":v0"));
    }

    // ============ Group ============

    @Test
    @DisplayName("group wraps a sub-expression in parentheses and merges its names and values (overwrites colliding keys)")
    void group_expression() {
        FilterExpression inner = FilterExpression.builder()
                .eq("role", "admin")
                .or()
                .eq("role", "moderator");

        FilterExpression fe = FilterExpression.builder()
                .eq("status", "active")
                .and()
                .group(inner);

        // group() uses putAll so inner keys (#n0, :v0) overwrite outer keys
        assertEquals("#n0 = :v0 AND (#n0 = :v0 OR #n1 = :v1)", fe.getExpression());
        assertEquals(Map.of("#n0", "role", "#n1", "role"), fe.getExpressionNames());
        assertEquals(AttributeValue.builder().s("admin").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("moderator").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("not() combined with group() creates negated parenthesized sub-expression")
    void not_withGroup() {
        FilterExpression inner = FilterExpression.builder().eq("role", "admin");
        FilterExpression fe = FilterExpression.builder()
                .not()
                .group(inner);
        assertEquals("NOT (#n0 = :v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("admin").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("group can be nested with multiple levels")
    void nestedGroup() {
        FilterExpression inner = FilterExpression.builder()
                .eq("a", 1)
                .or()
                .eq("b", 2);

        FilterExpression outer = FilterExpression.builder()
                .gt("c", 0)
                .and()
                .group(inner);

        // group() putAll overwrites outer #n0 and :v0 with inner entries
        assertEquals("#n0 > :v0 AND (#n0 = :v0 OR #n1 = :v1)", outer.getExpression());
    }

    // ============ Nested Attribute Access ============

    @Test
    @DisplayName("nestedEq builds correct dotted path expression with multiple name keys")
    void nestedEq() {
        FilterExpression fe = FilterExpression.builder()
                .nestedEq("address.city", "NYC");
        assertEquals("#n0.#n1 = :v0", fe.getExpression());
        assertEquals(Map.of("#n0", "address", "#n1", "city"), fe.getExpressionNames());
        assertEquals(AttributeValue.builder().s("NYC").build(), fe.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("nestedEq with indexed list attribute adds bracket suffix to name key")
    void nestedEq_withListIndex() {
        FilterExpression fe = FilterExpression.builder()
                .nestedEq("items[0].name", "widget");
        assertEquals("#n0[0].#n1 = :v0", fe.getExpression());
        assertEquals(Map.of("#n0", "items", "#n1", "name"), fe.getExpressionNames());
    }

    @Test
    @DisplayName("nestedEq with deeply nested path produces multiple dotted name keys")
    void nestedEq_deepPath() {
        FilterExpression fe = FilterExpression.builder()
                .nestedEq("a.b.c", "value");
        assertEquals("#n0.#n1.#n2 = :v0", fe.getExpression());
        assertEquals(Map.of("#n0", "a", "#n1", "b", "#n2", "c"), fe.getExpressionNames());
    }

    // ============ Naming / Counter Patterns ============

    @Test
    @DisplayName("expression names use #nN pattern and values use :vN pattern across multiple conditions")
    void namingPattern() {
        FilterExpression fe = FilterExpression.builder()
                .eq("a", 1)
                .and()
                .eq("b", "two")
                .and()
                .eq("c", true);

        String expr = fe.getExpression();
        assertTrue(expr.contains("#n0"), "should contain #n0");
        assertTrue(expr.contains("#n1"), "should contain #n1");
        assertTrue(expr.contains("#n2"), "should contain #n2");
        assertTrue(expr.contains(":v0"), "should contain :v0");
        assertTrue(expr.contains(":v1"), "should contain :v1");
        assertTrue(expr.contains(":v2"), "should contain :v2");

        assertEquals(3, fe.getExpressionNames().size());
        assertEquals(3, fe.getExpressionValues().size());
    }

    @Test
    @DisplayName("counters increment across different method types (eq, exists, between)")
    void countersIncrementAcrossMethods() {
        FilterExpression fe = FilterExpression.builder()
                .eq("name", "Alice")       // #n0, :v0
                .and()
                .exists("email")           // #n1 (no value)
                .and()
                .between("age", 20, 30);   // #n2, :v1, :v2

        assertEquals("#n0 = :v0 AND attribute_exists(#n1) AND #n2 BETWEEN :v1 AND :v2", fe.getExpression());
        assertEquals(Map.of("#n0", "name", "#n1", "email", "#n2", "age"), fe.getExpressionNames());
        assertEquals(3, fe.getExpressionValues().size());
    }

    // ============ Complex / Real-World Combinations ============

    @Test
    @DisplayName("real-world: filter active users in age range")
    void realWorld_activeUsersByAge() {
        FilterExpression fe = FilterExpression.builder()
                .eq("active", true)
                .and()
                .between("age", 21, 65);

        assertEquals("#n0 = :v0 AND #n1 BETWEEN :v1 AND :v2", fe.getExpression());
        assertEquals(AttributeValue.builder().bool(true).build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("21").build(), fe.getExpressionValues().get(":v1"));
        assertEquals(AttributeValue.builder().n("65").build(), fe.getExpressionValues().get(":v2"));
    }

    @Test
    @DisplayName("real-world: filter items with tags containing specific values excluding deprecated")
    void realWorld_tagsFilter() {
        FilterExpression fe = FilterExpression.builder()
                .exists("tags")
                .and()
                .contains("tags", "java")
                .and()
                .not()
                .contains("tags", "deprecated");

        assertEquals("attribute_exists(#n0) AND contains(#n1, :v0) AND NOT contains(#n2, :v1)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("java").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("deprecated").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("real-world: filter by size and type constraints")
    void realWorld_sizeAndType() {
        FilterExpression fe = FilterExpression.builder()
                .sizeGt("metadata", 0)
                .and()
                .attributeType("metadata", FilterExpression.AttributeType.MAP)
                .and()
                .exists("metadata.updatedAt");

        assertEquals("size(#n0) > :v0 AND attribute_type(#n1, :v1) AND attribute_exists(#n2)", fe.getExpression());
        assertEquals(AttributeValue.builder().n("0").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("M").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("complex chaining with mixed operators, grouping, and negation")
    void complexChaining() {
        FilterExpression inner = FilterExpression.builder()
                .eq("role", "admin");

        FilterExpression fe = FilterExpression.builder()
                .eq("category", "book")
                .and()
                .between("price", 10, 50)
                .and()
                .not()
                .group(inner);

        // group() putAll overwrites outer #n0 and :v0 with inner's values
        assertEquals("#n0 = :v0 AND #n1 BETWEEN :v1 AND :v2 AND NOT (#n0 = :v0)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("admin").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("10").build(), fe.getExpressionValues().get(":v1"));
        assertEquals(AttributeValue.builder().n("50").build(), fe.getExpressionValues().get(":v2"));
    }

    @Test
    @DisplayName("sizeGe and sizeLe together simulate a size BETWEEN pattern")
    void sizeGe_sizeLe_combined() {
        FilterExpression fe = FilterExpression.builder()
                .sizeGe("items", 2)
                .and()
                .sizeLe("items", 20);

        assertEquals("size(#n0) >= :v0 AND size(#n1) <= :v1", fe.getExpression());
        assertEquals(AttributeValue.builder().n("2").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("20").build(), fe.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("chaining eq and in together")
    void eqAndInChained() {
        FilterExpression fe = FilterExpression.builder()
                .eq("type", "premium")
                .and()
                .in("region", "US", "EU", "APAC");

        assertEquals("#n0 = :v0 AND #n1 IN (:v1, :v2, :v3)", fe.getExpression());
        assertEquals(AttributeValue.builder().s("premium").build(), fe.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("US").build(), fe.getExpressionValues().get(":v1"));
        assertEquals(AttributeValue.builder().s("EU").build(), fe.getExpressionValues().get(":v2"));
        assertEquals(AttributeValue.builder().s("APAC").build(), fe.getExpressionValues().get(":v3"));
    }

    // ============ Edge Cases ============

    @Test
    @DisplayName("builder returns independent instances with separate counters")
    void builderIndependence() {
        FilterExpression fe1 = FilterExpression.builder().eq("a", 1);
        FilterExpression fe2 = FilterExpression.builder().eq("b", 2);

        assertEquals("#n0 = :v0", fe1.getExpression());
        assertEquals("#n0 = :v0", fe2.getExpression());
        // Both start at #n0/:v0 independently
    }

    @Test
    @DisplayName("reusing a FilterExpression after group preserves merged state")
    void groupWithReusedInner() {
        FilterExpression inner = FilterExpression.builder()
                .eq("x", 1);

        FilterExpression outer = FilterExpression.builder()
                .group(inner)
                .and()
                .eq("y", 2);

        // After group(inner), name/value maps contain inner's entries and
        // counters are still at 0, so eq("y",2) reuses #n0/:v0.
        assertEquals("(#n0 = :v0) AND #n0 = :v0", outer.getExpression());
    }
}
