package com.hogwai.dynamodb.simplified.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AttributeValueConverter}.
 * <p>
 * Covers {@link AttributeValueConverter#toAttributeValue(Object)} and
 * {@link AttributeValueConverter#toKeyAttributeValue(Object)}.
 */
class AttributeValueConverterTest {

    // ---------------------------------------------------------------
    // toAttributeValue – null
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(null) should return AttributeValue with nul(true)")
    void shouldConvertNullToNulAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(null);
        assertTrue(result.nul(), "Expected nul() to be true for null input");
    }

    // ---------------------------------------------------------------
    // toAttributeValue – String
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(String) should return AttributeValue with s()")
    void shouldConvertStringToSAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue("hello");
        assertEquals("hello", result.s());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Number (Integer)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Integer) should return AttributeValue with n()")
    void shouldConvertIntegerToNAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(42);
        assertEquals("42", result.n());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Number (Double)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Double) should return AttributeValue with n()")
    void shouldConvertDoubleToNAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(3.14);
        assertEquals("3.14", result.n());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Boolean
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Boolean true) should return AttributeValue with bool(true)")
    void shouldConvertBooleanTrueToBoolAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(true);
        assertTrue(result.bool());
    }

    @Test
    @DisplayName("toAttributeValue(Boolean false) should return AttributeValue with bool(false)")
    void shouldConvertBooleanFalseToBoolAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(false);
        assertFalse(result.bool());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – byte[]
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(byte[]) should return AttributeValue with b()")
    void shouldConvertByteArrayToBAttribute() {
        byte[] bytes = {1, 2, 3, 4};
        AttributeValue result = AttributeValueConverter.toAttributeValue(bytes);
        assertArrayEquals(bytes, result.b().asByteArray());
    }

    @Test
    @DisplayName("toAttributeValue(empty byte[]) should return AttributeValue with empty b()")
    void shouldConvertEmptyByteArrayToBAttribute() {
        byte[] bytes = {};
        AttributeValue result = AttributeValueConverter.toAttributeValue(bytes);
        assertArrayEquals(bytes, result.b().asByteArray());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – List<String>
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(List<String>) should return AttributeValue with l([s(...), s(...)])")
    void shouldConvertStringListToLAttribute() {
        List<String> list = List.of("a", "b");
        AttributeValue result = AttributeValueConverter.toAttributeValue(list);
        List<AttributeValue> items = result.l();
        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals("a", items.get(0).s());
        assertEquals("b", items.get(1).s());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – List with mixed types
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(List with mixed types) should preserve types via nested l()")
    void shouldConvertMixedListToLAttribute() {
        List<Object> list = List.of("text", 42, true);
        AttributeValue result = AttributeValueConverter.toAttributeValue(list);
        List<AttributeValue> items = result.l();
        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("text", items.get(0).s());
        assertEquals("42", items.get(1).n());
        assertTrue(items.get(2).bool());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Set<String>
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Set<String>) should return AttributeValue with ss()")
    void shouldConvertStringSetToSsAttribute() {
        Set<String> set = new LinkedHashSet<>(List.of("x", "y"));
        AttributeValue result = AttributeValueConverter.toAttributeValue(set);
        List<String> ss = result.ss();
        assertNotNull(ss);
        assertEquals(2, ss.size());
        assertTrue(ss.contains("x"));
        assertTrue(ss.contains("y"));
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Set<Integer>
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Set<Integer>) should return AttributeValue with ns()")
    void shouldConvertIntegerSetToNsAttribute() {
        Set<Integer> set = new LinkedHashSet<>(List.of(1, 2));
        AttributeValue result = AttributeValueConverter.toAttributeValue(set);
        List<String> ns = result.ns();
        assertNotNull(ns);
        assertEquals(2, ns.size());
        assertTrue(ns.contains("1"));
        assertTrue(ns.contains("2"));
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Set<byte[]>
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Set<byte[]>) should return AttributeValue with bs()")
    void shouldConvertByteArraySetToBsAttribute() {
        byte[] a = {1, 2};
        byte[] b = {3, 4};
        Set<byte[]> set = new LinkedHashSet<>();
        set.add(a);
        set.add(b);
        AttributeValue result = AttributeValueConverter.toAttributeValue(set);
        List<SdkBytes> bs = result.bs();
        assertNotNull(bs);
        assertEquals(2, bs.size());
        assertArrayEquals(a, bs.get(0).asByteArray());
        assertArrayEquals(b, bs.get(1).asByteArray());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Map<String, Object>
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Map<String, Object>) should return AttributeValue with m() containing correct entries")
    void shouldConvertStringObjectMapToMAttribute() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);
        map.put("active", true);

        AttributeValue result = AttributeValueConverter.toAttributeValue(map);
        Map<String, AttributeValue> m = result.m();
        assertNotNull(m);
        assertEquals(3, m.size());

        assertEquals("Alice", m.get("name").s());
        assertEquals("30", m.get("age").n());
        assertTrue(m.get("active").bool());
    }

    @Test
    @DisplayName("toAttributeValue(Map with non-String keys) should convert keys via toString()")
    void shouldConvertMapWithNonStringKeys() {
        Map<Integer, String> map = Map.of(1, "one", 2, "two");
        AttributeValue result = AttributeValueConverter.toAttributeValue(map);
        Map<String, AttributeValue> m = result.m();
        assertNotNull(m);
        assertEquals(2, m.size());
        assertEquals("one", m.get("1").s());
        assertEquals("two", m.get("2").s());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Nested structures
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(Map with nested List and Map) should recurse correctly")
    void shouldConvertNestedMapWithList() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("key", "value");

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("items", List.of(1, inner));
        outer.put("flag", false);

        AttributeValue result = AttributeValueConverter.toAttributeValue(outer);
        Map<String, AttributeValue> m = result.m();

        // items list
        AttributeValue itemsAv = m.get("items");
        assertNotNull(itemsAv);
        List<AttributeValue> items = itemsAv.l();
        assertEquals(2, items.size());
        assertEquals("1", items.get(0).n());

        // nested map inside list
        Map<String, AttributeValue> innerMap = items.get(1).m();
        assertNotNull(innerMap);
        assertEquals("value", innerMap.get("key").s());

        // flag
        assertFalse(m.get("flag").bool());
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Empty collections
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(empty List) should return AttributeValue with empty l()")
    void shouldConvertEmptyListToLAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(List.of());
        List<AttributeValue> items = result.l();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("toAttributeValue(empty Set) should return AttributeValue with empty l() (not ss/ns)")
    void shouldConvertEmptySetToEmptyLAttribute() {
        AttributeValue result = AttributeValueConverter.toAttributeValue(Set.of());
        // Empty sets are returned as l([]), not ss([])
        List<AttributeValue> items = result.l();
        assertNotNull(items);
        assertTrue(items.isEmpty());

        // Verify the value is a list, not a typed set (ss/ns/bs)
        assertTrue(result.ss().isEmpty(), "ss should be empty when l() is set");
        assertTrue(result.ns().isEmpty(), "ns should be empty when l() is set");
        assertTrue(result.bs().isEmpty(), "bs should be empty when l() is set");
    }

    // ---------------------------------------------------------------
    // toAttributeValue – Unsupported type
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toAttributeValue(unsupported type) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingUnsupportedType() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toAttributeValue(LocalDate.of(2025, 1, 1))
        );
        assertTrue(ex.getMessage().contains(LocalDate.class.getName()));
    }

    @Test
    @DisplayName("toAttributeValue(unsupported type) should include the class name in the message")
    void shouldThrowWithClassNameForUnsupportedType() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toAttributeValue(new Object())
        );
        assertTrue(ex.getMessage().contains(Object.class.getName()));
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – String
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toKeyAttributeValue(String) should return AttributeValue with s()")
    void shouldConvertStringToKeySAttribute() {
        AttributeValue result = AttributeValueConverter.toKeyAttributeValue("pk-value");
        assertEquals("pk-value", result.s());
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – Number
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toKeyAttributeValue(Integer) should return AttributeValue with n()")
    void shouldConvertIntegerToKeyNAttribute() {
        AttributeValue result = AttributeValueConverter.toKeyAttributeValue(123);
        assertEquals("123", result.n());
    }

    @Test
    @DisplayName("toKeyAttributeValue(Long) should return AttributeValue with n()")
    void shouldConvertLongToKeyNAttribute() {
        AttributeValue result = AttributeValueConverter.toKeyAttributeValue(999_999_999_999L);
        assertEquals("999999999999", result.n());
    }

    @Test
    @DisplayName("toKeyAttributeValue(Double) should return AttributeValue with n()")
    void shouldConvertDoubleToKeyNAttribute() {
        AttributeValue result = AttributeValueConverter.toKeyAttributeValue(1.5);
        assertEquals("1.5", result.n());
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – byte[]
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toKeyAttributeValue(byte[]) should return AttributeValue with b()")
    void shouldConvertByteArrayToKeyBAttribute() {
        byte[] bytes = {10, 20, 30};
        AttributeValue result = AttributeValueConverter.toKeyAttributeValue(bytes);
        assertArrayEquals(bytes, result.b().asByteArray());
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – Unsupported types
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toKeyAttributeValue(Boolean) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingBooleanToKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(true)
        );
    }

    @Test
    @DisplayName("toKeyAttributeValue(List) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingListToKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(List.of("a"))
        );
    }

    @Test
    @DisplayName("toKeyAttributeValue(Map) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingMapToKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(Map.of("k", "v"))
        );
    }

    @Test
    @DisplayName("toKeyAttributeValue(Set) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingSetToKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(Set.of("x"))
        );
    }

    @Test
    @DisplayName("toKeyAttributeValue(LocalDate) should throw IllegalArgumentException")
    void shouldThrowWhenConvertingLocalDateToKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(LocalDate.now())
        );
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – null (no null guard)
    @Test
    @DisplayName("toKeyAttributeValue(null) should throw IllegalArgumentException")
    void toKeyAttributeValue_null() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(null)
        );
    }

    // ---------------------------------------------------------------
    // toKeyAttributeValue – Error message includes type name
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toKeyAttributeValue(unsupported) should mention the unsupported type in the message")
    void shouldMentionClassNameInErrorMessage() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValueConverter.toKeyAttributeValue(true)
        );
        assertTrue(ex.getMessage().contains(Boolean.class.getSimpleName())
                        || ex.getMessage().contains(Boolean.class.getName()),
                "Error message should indicate the unsupported type");
    }
}
