package com.hogwai.dynamodb.simplified.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConditionExpression")
class ConditionExpressionTest {

    @Test
    @DisplayName("eq delegates to FilterExpression correctly")
    void eq() {
        var expr = ConditionExpression.builder().eq("name", "Alice").build();
        assertEquals("#n0 = :v0", expr.getExpression());
        assertEquals(Map.of("#n0", "name"), expr.getExpressionNames());
        assertEquals(AttributeValue.builder().s("Alice").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("ne builds not-equal expression")
    void ne() {
        var expr = ConditionExpression.builder().ne("status", "active").build();
        assertEquals("#n0 <> :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().s("active").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("lt builds less-than expression")
    void lt() {
        var expr = ConditionExpression.builder().lt("age", 65).build();
        assertEquals("#n0 < :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("65").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("le builds less-than-or-equal expression")
    void le() {
        var expr = ConditionExpression.builder().le("age", 99).build();
        assertEquals("#n0 <= :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("99").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("gt builds greater-than expression")
    void gt() {
        var expr = ConditionExpression.builder().gt("age", 18).build();
        assertEquals("#n0 > :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("18").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("ge builds greater-than-or-equal expression")
    void ge() {
        var expr = ConditionExpression.builder().ge("age", 21).build();
        assertEquals("#n0 >= :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("21").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("beginsWith builds begins_with expression")
    void beginsWith() {
        var expr = ConditionExpression.builder().beginsWith("name", "A").build();
        assertEquals("begins_with(#n0, :v0)", expr.getExpression());
        assertEquals(AttributeValue.builder().s("A").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("contains builds contains expression")
    void contains() {
        var expr = ConditionExpression.builder().contains("description", "urgent").build();
        assertEquals("contains(#n0, :v0)", expr.getExpression());
        assertEquals(AttributeValue.builder().s("urgent").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeEq builds size() equality expression")
    void sizeEq() {
        var expr = ConditionExpression.builder().sizeEq("items", 5).build();
        assertEquals("size(#n0) = :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("5").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeLt builds size() less-than expression")
    void sizeLt() {
        var expr = ConditionExpression.builder().sizeLt("items", 10).build();
        assertEquals("size(#n0) < :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("10").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeLe builds size() less-than-or-equal expression")
    void sizeLe() {
        var expr = ConditionExpression.builder().sizeLe("items", 100).build();
        assertEquals("size(#n0) <= :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("100").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeGt builds size() greater-than expression")
    void sizeGt() {
        var expr = ConditionExpression.builder().sizeGt("items", 0).build();
        assertEquals("size(#n0) > :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("0").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeGe builds size() greater-than-or-equal expression")
    void sizeGe() {
        var expr = ConditionExpression.builder().sizeGe("items", 2).build();
        assertEquals("size(#n0) >= :v0", expr.getExpression());
        assertEquals(AttributeValue.builder().n("2").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("sizeBetween builds size() BETWEEN expression")
    void sizeBetween() {
        var expr = ConditionExpression.builder().sizeBetween("items", 1, 10).build();
        assertEquals("size(#n0) BETWEEN :v0 AND :v1", expr.getExpression());
        assertEquals(AttributeValue.builder().n("1").build(), expr.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("10").build(), expr.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("exists builds attribute_exists expression")
    void exists() {
        var expr = ConditionExpression.builder().exists("email").build();
        assertEquals("attribute_exists(#n0)", expr.getExpression());
        assertTrue(expr.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("notExists builds attribute_not_exists expression")
    void notExists() {
        var expr = ConditionExpression.builder().notExists("email").build();
        assertEquals("attribute_not_exists(#n0)", expr.getExpression());
        assertTrue(expr.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("attributeType builds attribute_type expression")
    void attributeType() {
        var expr = ConditionExpression.builder()
                .attributeType("data", FilterExpression.AttributeType.STRING).build();
        assertEquals("attribute_type(#n0, :v0)", expr.getExpression());
        assertEquals(AttributeValue.builder().s("S").build(), expr.getExpressionValues().get(":v0"));
    }

    @Test
    @DisplayName("between builds BETWEEN expression")
    void between() {
        var expr = ConditionExpression.builder().between("age", 18, 65).build();
        assertEquals("#n0 BETWEEN :v0 AND :v1", expr.getExpression());
        assertEquals(AttributeValue.builder().n("18").build(), expr.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().n("65").build(), expr.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("in builds IN expression")
    void inCondition() {
        var expr = ConditionExpression.builder().in("color", "red", "blue").build();
        assertEquals("#n0 IN (:v0, :v1)", expr.getExpression());
        assertEquals(AttributeValue.builder().s("red").build(), expr.getExpressionValues().get(":v0"));
        assertEquals(AttributeValue.builder().s("blue").build(), expr.getExpressionValues().get(":v1"));
    }

    @Test
    @DisplayName("and/or/not chain works")
    void logicalOperators() {
        var expr = ConditionExpression.builder()
                .eq("a", 1).and().eq("b", 2).build();
        assertEquals("#n0 = :v0 AND #n1 = :v1", expr.getExpression());
    }

    @Test
    @DisplayName("not operator prepends NOT")
    void notOperator() {
        var expr = ConditionExpression.builder().not().eq("active", true).build();
        assertEquals("NOT #n0 = :v0", expr.getExpression());
    }

    @Test
    @DisplayName("or operator joins conditions")
    void orOperator() {
        var expr = ConditionExpression.builder()
                .eq("status", "active").or().eq("status", "pending").build();
        assertEquals("#n0 = :v0 OR #n1 = :v1", expr.getExpression());
    }

    @Test
    @DisplayName("group wraps sub-expression in parentheses")
    void group() {
        var inner = FilterExpression.builder().eq("role", "admin");
        var expr = ConditionExpression.builder().eq("status", "active").and().group(inner).build();
        assertEquals("#n0 = :v0 AND (#n1 = :v1)", expr.getExpression());
    }

    @Test
    @DisplayName("nestedEq builds dotted path expression")
    void nestedEq() {
        var expr = ConditionExpression.builder().nestedEq("address.city", "NYC").build();
        assertEquals("#n0.#n1 = :v0", expr.getExpression());
    }

    @Test
    @DisplayName("from wraps FilterExpression")
    void fromWrapsFilterExpression() {
        var filter = FilterExpression.builder().eq("status", "active");
        var expr = ConditionExpression.from(filter);
        assertEquals("#n0 = :v0", expr.getExpression());
    }

    @Test
    @DisplayName("isEmpty returns true for fresh builder")
    void isEmpty() {
        var expr = ConditionExpression.builder().build();
        assertTrue(expr.isEmpty());
    }

    @Test
    @DisplayName("toSdkExpression produces valid SDK Expression")
    void toSdkExpression() {
        var expr = ConditionExpression.builder().eq("name", "test").build();
        var sdk = expr.toSdkExpression();
        assertEquals("#n0 = :v0", sdk.expression());
        assertEquals(Map.of("#n0", "name"), sdk.expressionNames());
    }

    @Test
    @DisplayName("counters increment independently across instances")
    void builderIndependence() {
        var expr1 = ConditionExpression.builder().eq("a", 1).build();
        var expr2 = ConditionExpression.builder().eq("b", 2).build();
        assertEquals("#n0 = :v0", expr1.getExpression());
        assertEquals("#n0 = :v0", expr2.getExpression());
    }
}
