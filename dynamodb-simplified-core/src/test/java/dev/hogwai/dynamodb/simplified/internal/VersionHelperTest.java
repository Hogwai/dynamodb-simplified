package dev.hogwai.dynamodb.simplified.internal;

import dev.hogwai.dynamodb.simplified.Versioned;
import dev.hogwai.dynamodb.simplified.entity.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionHelperTest {

    static class TestVersioned implements Versioned {
        private Integer version;

        TestVersioned(Integer version) {
            this.version = version;
        }

        @Override
        public Integer getVersion() {
            return version;
        }

        @Override
        public void setVersion(Integer version) {
            this.version = version;
        }
    }

    // ========== Versioned interface tests ==========

    @Test
    void getVersion_withVersionedItem_returnsVersion() {
        assertEquals(5, VersionHelper.getVersion(new TestVersioned(5)));
    }

    @Test
    void getVersion_withNonVersionedItem_returnsNull() {
        assertNull(VersionHelper.getVersion("not-versioned"));
    }

    @Test
    void buildCondition_withNullVersion_returnsNull() {
        assertNull(VersionHelper.buildCondition(new TestVersioned(null)));
    }

    @Test
    void buildCondition_withZeroVersion_usesAttributeNotExists() {
        var condition = VersionHelper.buildCondition(new TestVersioned(0));
        assertNotNull(condition);
        var expr = condition.toSdkExpression();
        assertTrue(expr.expression().contains("attribute_not_exists"));
    }

    @Test
    void buildCondition_withPositiveVersion_usesEquality() {
        var condition = VersionHelper.buildCondition(new TestVersioned(3));
        assertNotNull(condition);
        var expr = condition.toSdkExpression();
        assertTrue(expr.expression().contains("="));
    }

    @Test
    void incrementVersion_fromNull_setsToOne() {
        var item = new TestVersioned(null);
        VersionHelper.incrementVersion(item);
        assertEquals(1, item.getVersion());
    }

    @Test
    void incrementVersion_fromFive_setsToSix() {
        var item = new TestVersioned(5);
        VersionHelper.incrementVersion(item);
        assertEquals(6, item.getVersion());
    }

    // ========== @Version field annotation tests ==========

    static class VersionAnnotatedField {
        @Version
        private Integer version;
        VersionAnnotatedField() {
        }

        VersionAnnotatedField(Integer version) {
            this.version = version;
        }
    }

    @Test
    void getVersion_withAnnotatedField_returnsVersion() {
        assertEquals(3, VersionHelper.getVersion(new VersionAnnotatedField(3)));
    }

    @Test
    void getVersion_withAnnotatedFieldNull_returnsNull() {
        assertNull(VersionHelper.getVersion(new VersionAnnotatedField(null)));
    }

    @Test
    void isVersioned_withAnnotatedField_returnsTrue() {
        assertTrue(VersionHelper.isVersioned(new VersionAnnotatedField(1)));
    }

    @Test
    void isVersioned_withPlainObject_returnsFalse() {
        assertFalse(VersionHelper.isVersioned("not-versioned"));
    }

    @Test
    void incrementVersion_onAnnotatedFieldFromNull_setsToOne() {
        var item = new VersionAnnotatedField(null);
        VersionHelper.incrementVersion(item);
        assertEquals(1, item.version);
    }

    @Test
    void incrementVersion_onAnnotatedFieldFromFive_setsToSix() {
        var item = new VersionAnnotatedField(5);
        VersionHelper.incrementVersion(item);
        assertEquals(6, item.version);
    }

    @Test
    void buildCondition_onAnnotatedFieldWithNull_returnsNull() {
        assertNull(VersionHelper.buildCondition(new VersionAnnotatedField(null)));
    }

    @Test
    void buildCondition_onAnnotatedFieldWithZero_usesAttributeNotExists() {
        var condition = VersionHelper.buildCondition(new VersionAnnotatedField(0));
        assertNotNull(condition);
        assertTrue(condition.toSdkExpression().expression().contains("attribute_not_exists"));
    }

    @Test
    void buildCondition_onAnnotatedFieldWithPositive_usesEquality() {
        var condition = VersionHelper.buildCondition(new VersionAnnotatedField(3));
        assertNotNull(condition);
        assertTrue(condition.toSdkExpression().expression().contains("="));
    }

    static class VersionAnnotatedFieldNoNoArgConstructor {
        @Version
        private final Integer version;

        VersionAnnotatedFieldNoNoArgConstructor(Integer version) {
            this.version = version;
        }
    }

    @Test
    void incrementVersion_withoutNoArgConstructor_stillWorks() {
        // Object construction is not needed for increment; the helper
        // reflects on the class and field only.
        VersionAnnotatedFieldNoNoArgConstructor item =
                new VersionAnnotatedFieldNoNoArgConstructor(7);
        VersionHelper.incrementVersion(item);
        assertEquals(8, item.version);
    }
}
