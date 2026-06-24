package com.hogwai.dynamodb.simplified.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;

/**
 * Converts Java objects to DynamoDB {@link AttributeValue}.
 * <p>
 * Supports: null, String, Number, Boolean, byte[], List, Set (String/Number), and Map.
 * Key-only operations (partition/sort keys) restrict to String, Number, and Binary.
 */
public final class AttributeValueConverter {

    private AttributeValueConverter() {
    }

    /**
     * Converts any supported Java type to an AttributeValue.
     */
    @NonNull
    public static AttributeValue toAttributeValue(@Nullable Object value) {
        if (value == null) {
            return AttributeValue.builder().nul(true).build();
        }
        return switch (value) {
            case String s        -> AttributeValue.builder().s(s).build();
            case Number n        -> AttributeValue.builder().n(n.toString()).build();
            case Boolean b       -> AttributeValue.builder().bool(b).build();
            case byte[] bytes    -> AttributeValue.builder().b(SdkBytes.fromByteArray(bytes)).build();
            case List<?> list    -> toListAttributeValue(list);
            case Set<?> set      -> toSetAttributeValue(set);
            case Map<?, ?> map   -> toMapAttributeValue(map);
            default -> throw new IllegalArgumentException(
                    "Unsupported type: " + value.getClass().getName());
        };
    }

    /**
     * Converts a partition key or sort key value to an AttributeValue.
     * DynamoDB keys allow only String, Number, and Binary.
     */
    @NonNull
    public static AttributeValue toKeyAttributeValue(@Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Key value must not be null");
        }
        return switch (value) {
            case String s -> AttributeValue.builder().s(s).build();
            case Number n -> AttributeValue.builder().n(n.toString()).build();
            case byte[] bytes -> AttributeValue.builder().b(SdkBytes.fromByteArray(bytes)).build();
            default -> throw new IllegalArgumentException(
                    "Unsupported key type: " + value.getClass().getName()
                            + ". Keys must be String, Number, or byte[].");
        };
    }

    // ---- Private helpers ----

    private static AttributeValue toListAttributeValue(List<?> list) {
        List<AttributeValue> items = new ArrayList<>(list.size());
        for (Object item : list) {
            items.add(toAttributeValue(item));
        }
        return AttributeValue.builder().l(items).build();
    }

    private static AttributeValue toSetAttributeValue(Set<?> set) {
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert empty Set to DynamoDB attribute. "
                    + "DynamoDB does not support empty sets (SS, NS, BS). "
                    + "Use null or omit the attribute instead.");
        }
        Object first = set.iterator().next();
        if (first instanceof String) {
            @SuppressWarnings("unchecked")
            Collection<String> strings = (Collection<String>) set;
            return AttributeValue.builder().ss(strings).build();
        }
        if (first instanceof Number) {
            List<String> nums = new ArrayList<>(set.size());
            for (Object n : set) {
                nums.add(n.toString());
            }
            return AttributeValue.builder().ns(nums).build();
        }
        if (first instanceof byte[]) {
            List<SdkBytes> binaries = new ArrayList<>(set.size());
            for (Object b : set) {
                binaries.add(SdkBytes.fromByteArray((byte[]) b));
            }
            return AttributeValue.builder().bs(binaries).build();
        }
        throw new IllegalArgumentException("Unsupported set element type: " + first.getClass().getName());
    }

    private static AttributeValue toMapAttributeValue(Map<?, ?> map) {
        Map<String, AttributeValue> attrs = HashMap.newHashMap(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            attrs.put(entry.getKey().toString(), toAttributeValue(entry.getValue()));
        }
        return AttributeValue.builder().m(attrs).build();
    }
}
