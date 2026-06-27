package com.hogwai.dynamodb.simplified.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Entity(discriminator = "USER", table = "myapp")
@KeyPrefix(component = "PK", value = "USER")
class User {
    private String userId;
    private String email;

    public User() {
    }

    public User(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    @KeyComponent(component = "PK")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

class EntitySchemaReaderTest {

    @Test
    void readSchema_shouldBuildFromAnnotations() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        assertThat(schema.discriminator()).isEqualTo("USER");
        assertThat(schema.tableName()).isEqualTo("myapp");
        assertThat(schema.discriminatorAttribute()).isEqualTo("_type");
        assertThat(schema.entityClass()).isEqualTo(User.class);
    }

    @Test
    void computeKey_shouldUsePrefixAndComponents() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        String key = schema.computeKey("PK", new User("abc123", "a@b.com"));
        assertThat(key).isEqualTo("USER#abc123");
    }

    @Test
    void computeKey_shouldReturnNullForUndefinedComponent() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        String key = schema.computeKey("SK", new User("abc", "a@b.com"));
        assertThat(key).isNull();
    }

    @Test
    void readSchema_shouldRejectNonAnnotatedClass() {
        assertThrows(IllegalArgumentException.class,
                () -> EntitySchemaReader.read(String.class));
    }

    @Test
    void keyComponents_shouldReturnComponentNames() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        assertThat(schema.keyComponents()).containsExactly("PK");
    }

    // ============ Additional Branch Coverage Tests ============

    @Entity(discriminator = "NOPREFIX", table = "myapp")
    static class NoPrefixEntity {
        private String id;

        NoPrefixEntity() {
        }

        NoPrefixEntity(String id) {
            this.id = id;
        }

        @KeyComponent(component = "PK")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @Test
    @DisplayName("computeKey without KeyPrefix returns key without prefix")
    void computeKey_withoutPrefix_returnsKeyWithoutPrefix() {
        EntitySchema<NoPrefixEntity> schema = EntitySchemaReader.read(NoPrefixEntity.class);
        String key = schema.computeKey("PK", new NoPrefixEntity("abc123"));
        // No prefix, just the value
        assertThat(key).isEqualTo("abc123");
    }

    @Test
    @DisplayName("computeKey with null component value skips null in key")
    void computeKey_withNullComponentValue_appendsNothingForNull() {
        EntitySchema<NoPrefixEntity> schema = EntitySchemaReader.read(NoPrefixEntity.class);
        String key = schema.computeKey("PK", new NoPrefixEntity(null));
        // Component value is null, so nothing is appended; no prefix, so empty string
        assertThat(key).isEmpty();
    }

    @Entity(discriminator = "MULTI", table = "myapp")
    @KeyPrefix(component = "PK", value = "ENT")
    static class MultiKeyEntity {
        private String type;
        private String id;

        MultiKeyEntity() {
        }

        MultiKeyEntity(String type, String id) {
            this.type = type;
            this.id = id;
        }

        @KeyComponent(component = "PK")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @KeyComponent(component = "PK", position = 1)
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @Test
    @DisplayName("computeKey with multiple components joins them with hash")
    void computeKey_withMultipleComponents_joinsWithHash() {
        EntitySchema<MultiKeyEntity> schema = EntitySchemaReader.read(MultiKeyEntity.class);
        String key = schema.computeKey("PK", new MultiKeyEntity("USER", "abc"));
        // Prefix + component0 + '#' + component1
        assertThat(key).isEqualTo("ENT#USER#abc");
    }

    @Entity(discriminator = "ORDER", table = "myapp")
    @KeyPrefix(component = "PK", value = "ORDER")
    @KeyPrefix(component = "SK", value = "DATE")
    static class OrderEntity {
        private String orderId;
        private String date;

        OrderEntity() {
        }

        OrderEntity(String orderId, String date) {
            this.orderId = orderId;
            this.date = date;
        }

        @KeyComponent(component = "PK")
        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        @KeyComponent(component = "SK")
        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    @Test
    @DisplayName("computeKey with SK prefix returns key with SK prefix")
    void computeKey_withSkPrefix_returnsKeyWithPrefix() {
        EntitySchema<OrderEntity> schema = EntitySchemaReader.read(OrderEntity.class);
        String sk = schema.computeKey("SK", new OrderEntity("123", "2024-01-15"));
        assertThat(sk).isEqualTo("DATE#2024-01-15");
    }

    // ============ @KeyComponent on Fields Tests ============

    @Entity(discriminator = "FLD", table = "field-test")
    @KeyPrefix(component = "PK", value = "FLD")
    static class FieldKeyEntity {
        @KeyComponent(component = "PK")
        private String id;
        private String name;

        public FieldKeyEntity() {
        }

        FieldKeyEntity(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("readSchema with @KeyComponent on field builds schema from field annotation")
    void readSchema_withFieldAnnotation_buildsSchema() {
        EntitySchema<FieldKeyEntity> schema = EntitySchemaReader.read(FieldKeyEntity.class);
        assertThat(schema.keyComponents()).containsExactly("PK");
    }

    @Test
    @DisplayName("computeKey with @KeyComponent on field uses field-derived getter")
    void computeKey_withFieldAnnotation_usesGetter() {
        EntitySchema<FieldKeyEntity> schema = EntitySchemaReader.read(FieldKeyEntity.class);
        String key = schema.computeKey("PK", new FieldKeyEntity("abc"));
        assertThat(key).isEqualTo("FLD#abc");
    }

    @Entity(discriminator = "BAD", table = "bad")
    static class FieldNoGetterEntity {
        @KeyComponent(component = "PK")
        private String id;

        public FieldNoGetterEntity() {
        }
        // Intentionally no getId() getter
    }

    @Test
    @DisplayName("readSchema with @KeyComponent on field without getter throws DynamoSimplifiedException")
    void readSchema_withFieldAnnotation_noGetter_throws() {
        assertThrows(DynamoSimplifiedException.class,
                () -> EntitySchemaReader.read(FieldNoGetterEntity.class));
    }
}
