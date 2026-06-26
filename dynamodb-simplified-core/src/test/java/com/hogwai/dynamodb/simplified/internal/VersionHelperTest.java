package com.hogwai.dynamodb.simplified.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.hogwai.dynamodb.simplified.Versioned;
import org.junit.jupiter.api.Test;

class VersionHelperTest {

    static class TestVersioned implements Versioned {
        private Integer version;
        TestVersioned(Integer version) { this.version = version; }
        @Override public Integer getVersion() { return version; }
        @Override public void setVersion(Integer version) { this.version = version; }
    }

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
}
