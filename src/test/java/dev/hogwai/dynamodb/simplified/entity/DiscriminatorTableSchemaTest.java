package dev.hogwai.dynamodb.simplified.entity;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscriminatorTableSchemaTest {

    @Entity(discriminator = "USER", table = "myapp")
    @DynamoDbBean
    public static class User {
        private String pk;
        private String sk;
        private String name;

        public User() {
        }

        User(String pk, String sk, String name) {
            this.pk = pk;
            this.sk = sk;
            this.name = name;
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        @DynamoDbSortKey
        public String getSk() {
            return sk;
        }

        public void setSk(String sk) {
            this.sk = sk;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(discriminator = "USER", discriminatorAttribute = "__entity", table = "myapp")
    @DynamoDbBean
    public static class CustomAttributeUser {
        private String pk;

        public CustomAttributeUser() {
            // Default constructor
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }
    }

    @Entity(discriminator = "USER", table = "myapp")
    @DynamoDbBean
    public static class UserWithMappedType {
        private String pk;
        private String type;

        public UserWithMappedType() {
        }

        UserWithMappedType(String pk, String type) {
            this.pk = pk;
            this.type = type;
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        @DynamoDbAttribute("_type")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @Test
    void itemToMap_preservesMappedAttributesAndAddsDefaultDiscriminator() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        TableSchema<User> decorated = new DiscriminatorTableSchema<>(TableSchema.fromBean(User.class), schema);

        Map<String, AttributeValue> itemMap = decorated.itemToMap(new User("pk-1", "sk-1", "Alice"), false);

        assertThat(itemMap).containsEntry("pk", AttributeValue.fromS("pk-1"))
                .containsEntry("sk", AttributeValue.fromS("sk-1"))
                .containsEntry("name", AttributeValue.fromS("Alice"))
                .containsEntry("_type", AttributeValue.fromS("USER"));
    }

    @Test
    void itemToMap_usesCustomAttributeWithoutAddingDefaultAttribute() {
        EntitySchema<CustomAttributeUser> schema = EntitySchemaReader.read(CustomAttributeUser.class);
        TableSchema<CustomAttributeUser> decorated = new DiscriminatorTableSchema<>(
                TableSchema.fromBean(CustomAttributeUser.class), schema);

        Map<String, AttributeValue> itemMap = decorated.itemToMap(new CustomAttributeUser(), false);

        assertThat(itemMap).containsEntry("__entity", AttributeValue.fromS("USER"))
                .doesNotContainKey("_type");
    }

    @Test
    void itemToMap_overwritesAValueMappedUnderTheDiscriminatorAttribute() {
        EntitySchema<UserWithMappedType> schema = EntitySchemaReader.read(UserWithMappedType.class);
        TableSchema<UserWithMappedType> decorated = new DiscriminatorTableSchema<>(
                TableSchema.fromBean(UserWithMappedType.class), schema);

        Map<String, AttributeValue> itemMap = decorated.itemToMap(new UserWithMappedType("pk-1", "OTHER"), false);

        assertThat(itemMap).containsEntry("_type", AttributeValue.fromS("USER"));
    }

    @Test
    void mapToItem_preservesMappedAttributesWhenDiscriminatorIsUnknown() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        TableSchema<User> decorated = new DiscriminatorTableSchema<>(TableSchema.fromBean(User.class), schema);
        Map<String, AttributeValue> itemMap = Map.of(
                "pk", AttributeValue.fromS("pk-1"),
                "sk", AttributeValue.fromS("sk-1"),
                "name", AttributeValue.fromS("Alice"),
                "_type", AttributeValue.fromS("UNKNOWN"));

        User user = decorated.mapToItem(itemMap);

        assertThat(user.getPk()).isEqualTo("pk-1");
        assertThat(user.getSk()).isEqualTo("sk-1");
        assertThat(user.getName()).isEqualTo("Alice");
    }

    @Test
    void itemToMap_attributeProjectionUsesDelegateWithoutInjectingDiscriminator() {
        EntitySchema<User> schema = EntitySchemaReader.read(User.class);
        TableSchema<User> decorated = new DiscriminatorTableSchema<>(TableSchema.fromBean(User.class), schema);

        Map<String, AttributeValue> itemMap = decorated.itemToMap(
                new User("pk-1", "sk-1", "Alice"), List.of("pk"));

        assertThat(itemMap).containsOnlyKeys("pk");
    }
}
