# Quick Start: Avoiding Duplicates in MongoDB

## The Problem You Asked About

> "Can we ensure that if we create a JSON, store it, retrieve it, modify it and save it that we **replace the original object and do not end up with a duplicate**?"

## ✅ Yes! Use the new `save()` method

```java
// 1. Create and store
JVS document = new JVS()
    .set("name", "John")
    .set("status", "pending");
client.save("users", document).block();

// 2. Retrieve it
JVS retrieved = client.findOne("users", 
    new JVS().set("name", "John")).block();

// 3. Modify it
retrieved.set("status", "approved");
retrieved.set("notes", "Ready to go");

// 4. Save it - REPLACES original, NO DUPLICATE!
client.save("users", retrieved).block();

// Result: You still have exactly 1 document
```

## What `save()` Does

- **Has `_id`?** → Replaces the existing document (or inserts if not found)
- **No `_id`?** → Inserts as new document with generated `_id`
- **Result:** Never creates duplicates!

## Before vs After

### ❌ Before (using `insertOne` - creates duplicate error)
```java
// This FAILS on second save
client.insertOne("users", retrieved).block(); 
// Error: duplicate _id!
```

### ✅ After (using `save` - works perfectly)
```java
// This WORKS every time
client.save("users", retrieved).block();
// No duplicates!
```

## Full Documentation

See `SAVE_USAGE_GUIDE.md` for:
- Detailed examples
- Reactive patterns
- Custom ID mapping
- Batch operations
- Advanced upsert scenarios

## Tests

See comprehensive tests in:
`MongoIntegrationTest.java`
- `testSaveWithoutId()` 
- `testSaveWithExistingId()`
- `testCreateRetrieveModifySaveWorkflow()` ← **Your exact use case!**
- `testMultipleSavesNoDuplicates()`
