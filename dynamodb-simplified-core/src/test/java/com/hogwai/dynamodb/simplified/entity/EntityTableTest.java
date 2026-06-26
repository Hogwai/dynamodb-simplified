package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityTableTest {

    @Mock
    private Table<TestUser> mockTable;

    private EntitySchema<TestUser> schema;
    private DefaultEntityTable<TestUser> entityTable;

    @Entity(discriminator = "USER", table = "myapp")
    @KeyPrefix(component = "PK", value = "USER")
    static class TestUser {
        private String pk;
        private String userId;
        private String name;

        public TestUser() {}

        public TestUser(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
        public String getPk() { return pk; }
        public void setPk(String pk) { this.pk = pk; }

        @KeyComponent(component = "PK", position = 0)
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @BeforeEach
    void setUp() {
        schema = EntitySchemaReader.read(TestUser.class);
        entityTable = new DefaultEntityTable<>(mockTable, schema);
    }

    @Test
    void tableName_shouldReturnFromSchema() {
        assertThat(entityTable.tableName()).isEqualTo("myapp");
    }

    @Test
    void schema_shouldReturnSchema() {
        assertThat(entityTable.schema()).isSameAs(schema);
    }

    @Test
    void put_shouldComputeKeyAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice");
        entityTable.put(user);
        // PK should be computed from components: userId -> "USER#abc123"
        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockTable).putItem(user);
    }

    @Test
    void get_withPartitionKey_shouldDelegate() {
        when(mockTable.getItem("USER#abc123")).thenReturn(Optional.of(new TestUser("abc123", "Alice")));
        TestUser result = entityTable.get("USER#abc123");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @SuppressWarnings("unchecked")
    @Test
    void query_shouldFilterByDiscriminator() {
        var queryBuilder = mock(com.hogwai.dynamodb.simplified.builder.QueryBuilder.class);
        when(mockTable.query()).thenReturn(queryBuilder);
        when(queryBuilder.partitionKey("USER#abc123")).thenReturn(queryBuilder);
        when(queryBuilder.filter(any(java.util.function.Consumer.class))).thenReturn(queryBuilder);
        when(queryBuilder.executeAll()).thenReturn(List.of(new TestUser("abc123", "Alice")));

        List<TestUser> results = entityTable.query("USER#abc123");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("Alice");
    }

    @Test
    void delete_withPartitionKey_shouldDelegate() {
        entityTable.delete("USER#abc123");
        verify(mockTable).deleteItem("USER#abc123");
    }

    @Test
    void delete_withPartitionAndSortKey_shouldDelegate() {
        entityTable.delete("USER#abc123", "POST#def456");
        verify(mockTable).deleteItem("USER#abc123", "POST#def456");
    }

    @Test
    void update_shouldComputeKeyAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice Updated");
        when(mockTable.updateItem(user)).thenReturn(user);
        entityTable.update(user);
        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockTable).updateItem(user);
    }

    @Test
    void get_withPartitionAndSortKey_shouldDelegate() {
        when(mockTable.getItem("USER#abc123", "POST#def456"))
                .thenReturn(Optional.of(new TestUser("abc123", "Alice")));
        TestUser result = entityTable.get("USER#abc123", "POST#def456");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void get_whenNotFound_shouldReturnNull() {
        when(mockTable.getItem("NONEXISTENT")).thenReturn(Optional.empty());
        TestUser result = entityTable.get("NONEXISTENT");
        assertThat(result).isNull();
    }
}
