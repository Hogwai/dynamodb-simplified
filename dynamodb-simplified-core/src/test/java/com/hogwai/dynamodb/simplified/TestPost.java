package com.hogwai.dynamodb.simplified;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Set;

/**
 * Test entity with partition key, sort key, and a GSI (by_status).
 * Used for integration tests covering sort key queries and index operations.
 */
@DynamoDbBean
public class TestPost {
    private String id;
    private Long createdAt;
    private String status;
    private String title;
    private String content;
    private Integer views;
    private Set<String> tags;

    public TestPost() {
    }

    public TestPost(String id, Long createdAt, String status, String title,
                    String content, Integer views, Set<String> tags) {
        this.id = id;
        this.createdAt = createdAt;
        this.status = status;
        this.title = title;
        this.content = content;
        this.views = views;
        this.tags = tags;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbSortKey
    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "by_status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getViews() {
        return views;
    }

    public void setViews(Integer views) {
        this.views = views;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
}
