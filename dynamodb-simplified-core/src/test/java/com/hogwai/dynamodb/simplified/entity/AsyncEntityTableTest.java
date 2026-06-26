package com.hogwai.dynamodb.simplified.entity;

import com.hogwai.dynamodb.simplified.async.AsyncQueryBuilder;
import com.hogwai.dynamodb.simplified.async.AsyncTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncEntityTableTest {

    @Mock
    private AsyncTable<TestUser> mockAsyncTable;

    private EntitySchema<TestUser> schema;
    private AsyncDefaultEntityTable<TestUser> entityTable;

    @Entity(discriminator = "USER", table = "myapp")
    @KeyPrefix(component = "PK", value = "USER")
    static class TestUser {
        private String pk;
        private String userId;
        private String name;

        TestUser() {
        }

        TestUser(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        @KeyComponent(component = "PK")
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void setUp() {
        schema = EntitySchemaReader.read(TestUser.class);
        entityTable = new AsyncDefaultEntityTable<>(mockAsyncTable, schema);
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
        when(mockAsyncTable.putItem(user)).thenReturn(CompletableFuture.completedFuture(null));

        entityTable.put(user).join();

        // PK should be computed from components: userId -> "USER#abc123"
        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockAsyncTable).putItem(user);
    }

    @Test
    void get_withPartitionKey_shouldDelegate() {
        TestUser expected = new TestUser("abc123", "Alice");
        when(mockAsyncTable.getItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expected)));

        TestUser result = entityTable.get("USER#abc123").join();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void get_withPartitionAndSortKey_shouldDelegate() {
        TestUser expected = new TestUser("abc123", "Alice");
        when(mockAsyncTable.getItem("USER#abc123", "POST#def456"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expected)));

        TestUser result = entityTable.get("USER#abc123", "POST#def456").join();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void get_whenNotFound_shouldReturnNull() {
        when(mockAsyncTable.getItem("NONEXISTENT"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        TestUser result = entityTable.get("NONEXISTENT").join();

        assertThat(result).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void query_shouldFilterByDiscriminator() {
        var queryBuilder = mock(AsyncQueryBuilder.class);
        when(mockAsyncTable.query()).thenReturn(queryBuilder);
        when(queryBuilder.partitionKey("USER#abc123")).thenReturn(queryBuilder);
        when(queryBuilder.filter(any(java.util.function.Consumer.class))).thenReturn(queryBuilder);
        when(queryBuilder.executeAll())
                .thenReturn(CompletableFuture.completedFuture(List.of(new TestUser("abc123", "Alice"))));

        List<TestUser> results = entityTable.query("USER#abc123").join();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("Alice");
    }

    @Test
    void deleteEntity_shouldComputeKeysAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice");
        when(mockAsyncTable.deleteItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.deleteEntity(user).join();

        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockAsyncTable).deleteItem("USER#abc123");
    }

    @Test
    void delete_withPartitionKey_shouldDelegate() {
        when(mockAsyncTable.deleteItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.delete("USER#abc123").join();

        verify(mockAsyncTable).deleteItem("USER#abc123");
    }

    @Test
    void delete_withPartitionAndSortKey_shouldDelegate() {
        when(mockAsyncTable.deleteItem("USER#abc123", "POST#def456"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.delete("USER#abc123", "POST#def456").join();

        verify(mockAsyncTable).deleteItem("USER#abc123", "POST#def456");
    }

    @Test
    void update_shouldComputeKeyAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice Updated");
        TestUser updatedUser = new TestUser("abc123", "Alice Updated");
        when(mockAsyncTable.updateItem(user)).thenReturn(CompletableFuture.completedFuture(updatedUser));

        TestUser result = entityTable.update(user).join();

        assertThat(user.getPk()).isEqualTo("USER#abc123");
        assertThat(result).isSameAs(updatedUser);
        verify(mockAsyncTable).updateItem(user);
    }
}
