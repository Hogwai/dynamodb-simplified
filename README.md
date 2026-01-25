# DynamoDB Lightweight "Exists" Patterns (Java SDK v2)

This repository demonstrates efficient patterns for checking item existence in Amazon DynamoDB without retrieving full item payloads. It focuses on emulating SQL-like `EXISTS` behavior using the **AWS SDK for Java v2**.

## 📦 Prerequisites

* Java 17+
* Spring Boot / Micronaut / Quarkus (Dependency Injection)
* AWS SDK for Java v2 (`software.amazon.awssdk:dynamodb`)
* Lombok

## 🎯 The Goal

In SQL, checking for existence is cheap (`SELECT 1 FROM table WHERE id = ?`). In DynamoDB, `GetItem` retrieves the entire item by default. If your items are large (e.g., storing HTML bodies, JSON blobs), this wastes **Network Bandwidth** and increases latency.

This project explores strategies to:

1. Minimize **Network Overhead** (Payload size).
2. Understand **Read Capacity Unit (RCU)** consumption.
3. Handle **Batch Operations** correctly.

## 🛠️ Strategies Implemented

### 1. `existsByProjection`

* **Method:** `GetItem` with `ProjectionExpression`.
* **How it works:** Asks DynamoDB to return only the specific key attribute instead of the whole item.
* **Pros:** Significantly reduces network bandwidth and deserialization CPU cost.
* **Cons:** **Does not reduce RCU costs.** DynamoDB still reads the full item size from disk to process the projection.

### 2. `batchExists`

* **Method:** `BatchGetItem` with key mapping.
* **How it works:** Checks up to 100 items in a single HTTP request.

* **Key Logic:**
  * DynamoDB only returns items that *exist*. The code maps the response back to the requested list to determine `true`/`false`.
  * **Pro Tip:** Production implementations must handle `UnprocessedKeys` (throttling) via a retry loop.

### 3. `hasKeywordsByGetItem`

* **Method:** `GetItem` checking a specific attribute (e.g., a list or boolean).
* **How it works:** Retrieves only the `keywords` attribute to check if it contains data.
* **Use Case:** Faster than client-side filtering if the item is large. Note that `FilterExpression` on a `Query` still consumes RCU for the full item read.

### 4. `hasPostsForSubreddit`

* **Method:** `Query` with `Limit(1)`.
* **Use Case:** Efficiently checks if *any* item exists in a partition (collection) without reading the whole list.

## 💡 Performance Cheatsheet

| Technique | Saves Bandwidth? | Saves RCU ($$$)? | Best For |
| :--- | :---: | :---: | :--- |
| **ProjectionExpression** | ✅ Yes | ❌ No | Large items, reducing network latency. |
| **Eventual Consistency** | ❌ No | ✅ **Yes** | `exists` checks where delay is acceptable. |
| **Keys-Only GSI** | ✅ Yes | ✅ **Yes** | Heavy `exists` checks on very large items. |

## 🚀 Best Practices

1. **Use Eventual Consistency:**
    For existence checks, you rarely need Strong Consistency. Set `.consistentRead(false)` to halve your RCU bill.

    ```java
    GetItemRequest.builder().consistentRead(false)...
    ```

2. **Handle Batch Throttling:**
    `BatchGetItem` may return partial results. Always check `response.unprocessedKeys()` and retry those specific keys.

3. **Global Secondary Indexes (GSI):**
    If your items are massive (e.g., >4KB), create a **KEYS_ONLY GSI**. Reading from the index is much cheaper than reading from the main table with a projection.
