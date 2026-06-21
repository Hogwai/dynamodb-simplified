package com.hogwai.dynamodb.simplified;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Set;

@DynamoDbBean
public class Product {
    private String id;
    private String name;
    private Double price;
    private Boolean inStock;
    private Set<String> tags;
    private Long createdAt;

    public Product() {}

    public Product(String id, String name, Double price, Boolean inStock, Set<String> tags, Long createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.inStock = inStock;
        this.tags = tags;
        this.createdAt = createdAt;
    }

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Boolean getInStock() { return inStock; }
    public void setInStock(Boolean inStock) { this.inStock = inStock; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
