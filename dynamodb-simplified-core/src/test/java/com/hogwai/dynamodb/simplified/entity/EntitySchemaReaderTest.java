package com.hogwai.dynamodb.simplified.entity;

import org.junit.jupiter.api.Test;

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

    @KeyComponent(component = "PK", position = 0)
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
}
