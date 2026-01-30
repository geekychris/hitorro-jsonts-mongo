# MongoDB Save Usage Guide

## Problem: Avoiding Duplicates When Updating Documents

When working with MongoDB, a common pattern is:
1. Create a JSON document
2. Store it in MongoDB
3. Retrieve it later
4. Modify it
5. Save it back

**The Problem:** Using `insertOne()` on step 5 will fail with a duplicate `_id` error. Using `replaceOne()` requires you to manually track and specify the ID.

**The Solution:** Use the new `save()` method which automatically handles both insert and replace operations.

## How `save()` Works

The `save()` method implements **upsert** logic:

- **If the document has an `_id` field:**
  - MongoDB will **replace** the existing document with that `_id`
  - If no document with that `_id` exists, it will be **inserted**
  
- **If the document has no `_id` field:**
  - MongoDB will **insert** the document with a generated `_id`

This ensures you **never create duplicates** when following the create-retrieve-modify-save workflow.

## Basic Usage Examples

### Example 1: Simple Create-Modify-Save (No Duplicates!)

```java
// 1. Create and save
JVS document = new JVS()
    .set("name", "John Doe")
    .set("email", "john@example.com")
    .set("status", "pending");

client.save("users", document).block();

// 2. Later... retrieve it
JVS retrieved = client.findOne("users", 
    new JVS().set("name", "John Doe")).block();

// 3. Modify it
retrieved.set("status", "approved");
retrieved.set("approvedBy", "admin");

// 4. Save it - REPLACES the original, no duplicate!
client.save("users", retrieved).block();

// Result: Still just 1 document in the collection
```

### Example 2: Multiple Saves on Same Document

```java
JVS doc = new JVS()
    .set("counter", "page-views")
    .set("count", 0);

// First save (insert)
client.save("analytics", doc).block();

// Retrieve and increment multiple times
for (int i = 0; i < 5; i++) {
    JVS current = client.findOne("analytics", 
        new JVS().set("counter", "page-views")).block();
    
    current.set("count", current.getLong("count") + 1);
    
    // Each save replaces the existing document
    client.save("analytics", current).block();
}

// Result: Still just 1 document with count = 5
```

### Example 3: Using Custom _id Values

```java
String customId = "user-123";

JVS user = new JVS()
    .set("_id", customId)
    .set("name", "Alice")
    .set("version", 1);

// First save
client.save("users", user).block();

// Modify and save again with same _id
user.set("version", 2);
user.set("lastModified", System.currentTimeMillis());

// This replaces the existing document, no duplicate
client.save("users", user).block();

// Result: 1 document with _id = "user-123" and version = 2
```

## Reactive Usage with Reactor

### Chain of Operations

```java
import reactor.core.publisher.Mono;

Mono<UpdateResult> workflow = client
    // Insert initial document
    .insertOne("orders", new JVS()
        .set("orderId", "ORD-001")
        .set("status", "pending")
        .set("total", 100.00))
    
    // Retrieve it
    .flatMap(insertResult -> 
        client.findById("orders", insertResult.getInsertedId()))
    
    // Modify
    .map(order -> {
        order.set("status", "processing");
        order.set("processedAt", System.currentTimeMillis());
        return order;
    })
    
    // Save (replaces)
    .flatMap(modifiedOrder -> 
        client.save("orders", modifiedOrder));

// Execute
workflow.block();
```

## Method Comparison

### `insertOne()` - Use for first-time inserts only
```java
// ✅ Good: First-time insert
client.insertOne("users", newUser).block();

// ❌ Bad: Will fail if document with same _id exists
client.insertOne("users", existingUser).block(); // ERROR!
```

### `replaceById()` - Use when you explicitly know the ID
```java
Object id = ...; // You must track the ID

// ✅ Good: Explicit ID-based replacement
client.replaceById("users", id, updatedUser).block();

// ❌ Bad: Requires manual ID tracking
```

### `save()` - Use for create-or-update operations ⭐ RECOMMENDED
```java
// ✅ Perfect: Works for both new and existing documents
client.save("users", document).block();

// Document has _id? → Replaces existing
// Document has no _id? → Inserts new
// Either way: No duplicates!
```

## Batch Operations

### Save Multiple Documents

```java
List<JVS> documents = Arrays.asList(doc1, doc2, doc3);

// Each document will be saved (insert or replace)
Flux<UpdateResult> results = client.saveMany("collection", documents);

results.collectList().block();
```

## Advanced: Custom Upsert Criteria

If you need to upsert based on criteria other than `_id`:

```java
// Upsert based on email field
JVS filter = new JVS().set("email", "user@example.com");
JVS document = new JVS()
    .set("email", "user@example.com")
    .set("name", "Updated Name")
    .set("lastLogin", System.currentTimeMillis());

client.upsert("users", filter, document).block();

// If a user with that email exists → replaces it
// If no user with that email → inserts it
```

## Configuration: ID Field Mapping

By default, `save()` uses the `_id` field. You can configure it to map from JVS standard fields:

```java
// Configure to map id.id → _id
JVSCodecConfig config = JVSCodecConfig.builder()
    .mapIdField(true)
    .jvsIdField("id.id")  // Use id.id as the MongoDB _id
    .build();

JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .codecConfig(config)
    .build();

// Now documents with id.id will use that for _id
JVS doc = new JVS()
    .set("id.id", "USER-123")
    .set("name", "Alice");

client.save("users", doc).block();
// Saved with _id = "USER-123"
```

## Summary

**Use `save()` whenever you want to:**
- ✅ Create or update a document without worrying about duplicates
- ✅ Implement the retrieve-modify-save pattern
- ✅ Avoid tracking whether a document is new or existing
- ✅ Ensure exactly one document per unique ID

**The Result:**
- No duplicate documents
- Simpler code (no manual ID tracking)
- Works seamlessly with both new and existing documents
