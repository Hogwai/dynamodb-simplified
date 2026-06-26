package com.hogwai.dynamodb.simplified.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateExpression")
class UpdateExpressionTest {

    @Test
    @DisplayName("fresh builder is empty, becomes non-empty after set()")
    void isEmpty() {
        UpdateExpression expr = UpdateExpression.builder();
        assertTrue(expr.isEmpty());

        expr.set("name", "Alice");
        assertFalse(expr.isEmpty());
    }

    @Test
    @DisplayName("set() produces correct expression, name mapping, and string value")
    void set() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("name", "Alice");

        assertEquals("SET #u0 = :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "name"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().s("Alice").build(),
                expr.getExpressionValues().get(":u0"));
        assertEquals(1, expr.getExpressionValues().size());
    }

    @Test
    @DisplayName("setIfNotExists() wraps value in if_not_exists function")
    void setIfNotExists() {
        UpdateExpression expr = UpdateExpression.builder()
                .setIfNotExists("email", "a@b.com");

        assertEquals("SET #u0 = if_not_exists(#u0, :u0)", expr.getExpression());
        assertEquals(Map.of("#u0", "email"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().s("a@b.com").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("increment() produces #attr = #attr + :val with numeric value")
    void increment() {
        UpdateExpression expr = UpdateExpression.builder()
                .increment("views", 1);

        assertEquals("SET #u0 = #u0 + :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "views"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().n("1").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("decrement() produces #attr = #attr - :val with numeric value")
    void decrement() {
        UpdateExpression expr = UpdateExpression.builder()
                .decrement("stock", 5);

        assertEquals("SET #u0 = #u0 - :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "stock"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().n("5").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("appendToList() uses list_append with if_not_exists empty-list guard")
    void appendToList() {
        UpdateExpression expr = UpdateExpression.builder()
                .appendToList("items", List.of("a"));

        assertEquals(
                "SET #u0 = list_append(if_not_exists(#u0, :u1), :u0)",
                expr.getExpression());
        assertEquals(Map.of("#u0", "items"), expr.getExpressionNames());

        Map<String, AttributeValue> values = expr.getExpressionValues();
        assertEquals(2, values.size());
        assertEquals(
                AttributeValue.builder().l(List.of(AttributeValue.builder().s("a").build())).build(),
                values.get(":u0"));
        assertEquals(
                AttributeValue.builder().l(List.of()).build(),
                values.get(":u1"));
    }

    @Test
    @DisplayName("prependToList() uses list_append with prepended values")
    void prependToList() {
        UpdateExpression expr = UpdateExpression.builder()
                .prependToList("items", List.of("a"));

        assertEquals(
                "SET #u0 = list_append(:u0, if_not_exists(#u0, :u1))",
                expr.getExpression());
        assertEquals(Map.of("#u0", "items"), expr.getExpressionNames());

        Map<String, AttributeValue> values = expr.getExpressionValues();
        assertEquals(2, values.size());
        assertEquals(
                AttributeValue.builder().l(List.of(AttributeValue.builder().s("a").build())).build(),
                values.get(":u0"));
        assertEquals(
                AttributeValue.builder().l(List.of()).build(),
                values.get(":u1"));
    }

    @Test
    @DisplayName("setListElement() indexes into a list attribute")
    void setListElement() {
        UpdateExpression expr = UpdateExpression.builder()
                .setListElement("tags", 0, "new");

        assertEquals("SET #u0[0] = :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "tags"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().s("new").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("setNested() maps each dot-separated path segment to a name placeholder")
    void setNested() {
        UpdateExpression expr = UpdateExpression.builder()
                .setNested("address.city", "NYC");

        assertEquals("SET #u0.#u1 = :u0", expr.getExpression());
        assertEquals(
                Map.of("#u0", "address", "#u1", "city"),
                expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().s("NYC").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("remove() adds attributes to the REMOVE clause")
    void remove() {
        UpdateExpression expr = UpdateExpression.builder()
                .remove("name");

        assertEquals("REMOVE #u0", expr.getExpression());
        assertEquals(Map.of("#u0", "name"), expr.getExpressionNames());
        assertTrue(expr.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("remove() with multiple attributes produces comma-separated list")
    void removeMultiple() {
        UpdateExpression expr = UpdateExpression.builder()
                .remove("name", "email");

        assertEquals("REMOVE #u0, #u1", expr.getExpression());
        assertEquals(
                Map.of("#u0", "name", "#u1", "email"),
                expr.getExpressionNames());
        assertTrue(expr.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("removeListElement() produces indexed REMOVE")
    void removeListElement() {
        UpdateExpression expr = UpdateExpression.builder()
                .removeListElement("items", 3);

        assertEquals("REMOVE #u0[3]", expr.getExpression());
        assertEquals(Map.of("#u0", "items"), expr.getExpressionNames());
        assertTrue(expr.getExpressionValues().isEmpty());
    }

    @Test
    @DisplayName("addToSet() produces ADD clause with string-set value")
    void addToSet() {
        UpdateExpression expr = UpdateExpression.builder()
                .addToSet("tags", Set.of("a", "b"));

        assertEquals("ADD #u0 :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "tags"), expr.getExpressionNames());

        AttributeValue value = expr.getExpressionValues().get(":u0");
        assertNotNull(value);
        assertNotNull(value.ss());
        assertEquals(2, value.ss().size());
        assertTrue(value.ss().contains("a"));
        assertTrue(value.ss().contains("b"));
    }

    @Test
    @DisplayName("addNumber() produces ADD clause with numeric value")
    void addNumber() {
        UpdateExpression expr = UpdateExpression.builder()
                .addNumber("price", 10);

        assertEquals("ADD #u0 :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "price"), expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().n("10").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("deleteFromSet() produces DELETE clause with string-set value")
    void deleteFromSet() {
        UpdateExpression expr = UpdateExpression.builder()
                .deleteFromSet("tags", Set.of("old"));

        assertEquals("DELETE #u0 :u0", expr.getExpression());
        assertEquals(Map.of("#u0", "tags"), expr.getExpressionNames());

        AttributeValue value = expr.getExpressionValues().get(":u0");
        assertNotNull(value);
        assertNotNull(value.ss());
        assertEquals(1, value.ss().size());
        assertEquals("old", value.ss().getFirst());
    }

    @Test
    @DisplayName("combined SET and REMOVE produces both clauses in order")
    void combinedSetAndRemove() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("a", 1)
                .remove("b");

        assertEquals("SET #u0 = :u0 REMOVE #u1", expr.getExpression());
        assertEquals(
                Map.of("#u0", "a", "#u1", "b"),
                expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().n("1").build(),
                expr.getExpressionValues().get(":u0"));
        assertEquals(1, expr.getExpressionValues().size());
    }

    @Test
    @DisplayName("multiple SET actions are comma-separated")
    void multipleSetActions() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("x", 1)
                .set("y", 2);

        assertEquals("SET #u0 = :u0, #u1 = :u1", expr.getExpression());
        assertEquals(
                Map.of("#u0", "x", "#u1", "y"),
                expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().n("1").build(),
                expr.getExpressionValues().get(":u0"));
        assertEquals(
                AttributeValue.builder().n("2").build(),
                expr.getExpressionValues().get(":u1"));
    }

    @Test
    @DisplayName("expression names and values accumulate across mixed operation types")
    void accumulationAcrossOperations() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("name", "Alice")          // #u0, :u0
                .increment("age", 1)           // #u1, :u1
                .remove("oldField")            // #u2
                .addToSet("tags", Set.of("newTag")); // #u3, :u2

        String expression = expr.getExpression();
        assertTrue(expression.contains("SET"));
        assertTrue(expression.contains("REMOVE"));
        assertTrue(expression.contains("ADD"));
        assertFalse(expression.contains("DELETE"));

        // Name placeholders are sequential across all operations
        Map<String, String> names = expr.getExpressionNames();
        assertEquals("name", names.get("#u0"));
        assertEquals("age", names.get("#u1"));
        assertEquals("oldField", names.get("#u2"));
        assertEquals("tags", names.get("#u3"));
        assertEquals(4, names.size());

        // Value placeholders are sequential across all operations
        Map<String, AttributeValue> values = expr.getExpressionValues();
        assertEquals(
                AttributeValue.builder().s("Alice").build(),
                values.get(":u0"));
        assertEquals(
                AttributeValue.builder().n("1").build(),
                values.get(":u1"));
        assertNotNull(values.get(":u2"));
        assertNotNull(values.get(":u2").ss());
        assertEquals(1, values.get(":u2").ss().size());
        assertTrue(values.get(":u2").ss().contains("newTag"));
        assertEquals(3, values.size());
    }

    @Test
    @DisplayName("all four clauses (SET, REMOVE, ADD, DELETE) appear in order")
    void allFourClauses() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("name", "Alice")
                .remove("oldField")
                .addToSet("tags", Set.of("new"))
                .deleteFromSet("obsolete", Set.of("gone"));

        assertEquals(
                "SET #u0 = :u0 REMOVE #u1 ADD #u2 :u1 DELETE #u3 :u2",
                expr.getExpression());
    }

    @Test
    @DisplayName("set() with numeric value stores correct AttributeValue")
    void setWithNumber() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("count", 42);

        assertEquals(
                AttributeValue.builder().n("42").build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("set() with boolean value stores correct AttributeValue")
    void setWithBoolean() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("active", true);

        assertEquals(
                AttributeValue.builder().bool(true).build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("set() with null value stores NULL attribute value")
    void setWithNull() {
        UpdateExpression expr = UpdateExpression.builder()
                .set("nullable", null);

        assertEquals(
                AttributeValue.builder().nul(true).build(),
                expr.getExpressionValues().get(":u0"));
    }

    @Test
    @DisplayName("setNested with brackets in path handles indexed nested attributes")
    void setNestedWithBrackets() {
        UpdateExpression expr = UpdateExpression.builder()
                .setNested("a.b[0].c", "value");

        // addName("a") -> #u0, addName("b[0]") is handled: attr="b", index="[0]"
        // then addName("c") -> #u2
        // Result: #u0.#u1[0].#u2 = :u0
        assertEquals("SET #u0.#u1[0].#u2 = :u0", expr.getExpression());
        assertEquals(
                Map.of("#u0", "a", "#u1", "b", "#u2", "c"),
                expr.getExpressionNames());
        assertEquals(
                AttributeValue.builder().s("value").build(),
                expr.getExpressionValues().get(":u0"));
    }
}
