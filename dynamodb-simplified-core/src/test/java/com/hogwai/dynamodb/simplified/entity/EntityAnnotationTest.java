package com.hogwai.dynamodb.simplified.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Entity(discriminator = "TEST_USER", table = "test_table")
class TestEntity {
    private String userId;

    @KeyComponent(component = "PK")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

@Entity(discriminator = "TEST_POST", table = "test_table")
@KeyPrefix(component = "PK", value = "POST")
class TestPostEntity {
}

@Entity(discriminator = "MULTI_PREFIX", table = "test_table")
@KeyPrefix(component = "PK", value = "PARENT")
@KeyPrefix(component = "SK", value = "CHILD")
class MultiPrefixEntity {
}

class EntityAnnotationTest {

    @Test
    void entityAnnotation_shouldBePresent() {
        Entity ann = TestEntity.class.getAnnotation(Entity.class);
        assertThat(ann).isNotNull();
        assertThat(ann.discriminator()).isEqualTo("TEST_USER");
        assertThat(ann.table()).isEqualTo("test_table");
        assertThat(ann.discriminatorAttribute()).isEqualTo("_type");
    }

    @Test
    void keyComponentAnnotation_shouldBeReadable() throws Exception {
        KeyComponent kc = TestEntity.class.getMethod("getUserId")
                .getAnnotation(KeyComponent.class);
        assertThat(kc).isNotNull();
        assertThat(kc.component()).isEqualTo("PK");
        assertThat(kc.position()).isZero();
    }

    @Test
    void keyPrefixAnnotation_shouldBePresent() {
        KeyPrefix kp = TestPostEntity.class.getAnnotation(KeyPrefix.class);
        assertThat(kp).isNotNull();
        assertThat(kp.component()).isEqualTo("PK");
        assertThat(kp.value()).isEqualTo("POST");
    }

    @Test
    void multipleKeyPrefixes_shouldBeReadable() {
        KeyPrefix.Container container = MultiPrefixEntity.class.getAnnotation(KeyPrefix.Container.class);
        assertThat(container).isNotNull();
        assertThat(container.value()).hasSize(2);
        assertThat(container.value()[0].component()).isEqualTo("PK");
        assertThat(container.value()[1].component()).isEqualTo("SK");
    }

    @Test
    void entityAnnotation_shouldUseDefaultDiscriminatorAttribute() {
        Entity ann = TestEntity.class.getAnnotation(Entity.class);
        assertThat(ann.discriminatorAttribute()).isEqualTo("_type");
    }
}
