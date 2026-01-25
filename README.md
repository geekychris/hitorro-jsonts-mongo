# Hitorro JSON Type System - MongoDB Integration

MongoDB integration for the Hitorro JSON Type System (JVS), providing seamless reactive MongoDB operations with automatic BSON mapping and query support.

## Features

- **Reactive MongoDB Driver**: Built on MongoDB Reactive Streams with Project Reactor
- **Automatic BSON Conversion**: Efficient bidirectional JVS ↔ BSON codec
- **Connection Pooling**: Configurable connection pool with sensible defaults
- **Flexible Queries**: Support for both JVS-based and native MongoDB BSON queries
- **Full CRUD Operations**: Insert, find, update, replace, delete with reactive streams
- **Advanced Features**: Aggregation pipelines, indexes, transactions, change streams
- **Type Preservation**: Handles MongoDB-specific types (ObjectId, Date, Binary)
- **Configurable Mapping**: Customize how JVS fields map to MongoDB documents

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.hitorro</groupId>
    <artifactId>hitorro-jsonts-mongo</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Basic Usage

```java
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Create client
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .build();

// Create a JVS document
JVS doc = new JVS()
    .set("name", "John Doe")
    .set("age", 30)
    .set("email", "john@example.com");

// Insert document
client.insertOne("users", doc)
    .subscribe(result -> System.out.println("Inserted: " + result.getInsertedId()));

// Find documents
Flux<JVS> users = client.find("users", new JVS().set("age.$gte", 18));
users.subscribe(user -> System.out.println(user.getString("name")));

// Find by ID
client.findById("users", someId)
    .subscribe(user -> System.out.println("Found: " + user));

// Update
client.updateOne("users", 
    new JVS().set("name", "John Doe"),
    new JVS().set("$set.age", 31))
    .subscribe(result -> System.out.println("Modified: " + result.getModifiedCount()));

// Delete
client.deleteOne("users", new JVS().set("name", "John Doe"))
    .subscribe(result -> System.out.println("Deleted: " + result.getDeletedCount()));

// Close client when done
client.close();
```

## Configuration

### Codec Configuration

Control how JVS objects are mapped to BSON:

```java
import com.hitorro.mongo.codec.JVSCodecConfig;

// Default configuration (direct _id mapping)
JVSCodecConfig defaultConfig = JVSCodecConfig.defaultConfig();

// JVS standard configuration (maps _id to id.id field)
JVSCodecConfig jvsConfig = JVSCodecConfig.jvsStandardConfig();

// Custom configuration
JVSCodecConfig customConfig = JVSCodecConfig.builder()
    .mapIdField(true)
    .jvsIdField("id.did")
    .preserveObjectIdAsString(true)
    .preserveDateAsTimestamp(true)
    .detectBsonTypes(true)
    .build();

JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .codecConfig(customConfig)
    .build();
```

### Connection Pool Settings

```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .maxConnectionPoolSize(200)
    .minConnectionPoolSize(10)
    .maxWaitTime(60000) // 60 seconds
    .maxConnectionIdleTime(300000) // 5 minutes
    .build();
```

## Query Patterns

### Simple Equality Queries

```java
// Find users named "John"
JVS query = new JVS().set("name", "John");
Flux<JVS> results = client.find("users", query);
```

### Operator Queries

```java
// Find users age >= 18 and < 65
JVS query = new JVS()
    .set("age.$gte", 18)
    .set("age.$lt", 65);
Flux<JVS> results = client.find("users", query);
```

### Complex Queries with $and, $or

```java
// Using JVS
JVS query = new JVS().set("$or", Arrays.asList(
    new JVS().set("status", "active"),
    new JVS().set("role", "admin")
));

// Or using native MongoDB filters
import com.mongodb.client.model.Filters;
Bson filter = Filters.or(
    Filters.eq("status", "active"),
    Filters.eq("role", "admin")
);
Flux<JVS> results = client.find("users", filter);
```

## Advanced Operations

### Aggregation Pipeline

```java
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import java.util.Arrays;

Flux<JVS> pipeline = client.aggregate("users", Arrays.asList(
    Aggregates.match(Filters.gte("age", 18)),
    Aggregates.group("$department", Accumulators.avg("avgAge", "$age")),
    Aggregates.sort(Sorts.descending("avgAge"))
));
```

### Indexes

```java
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

// Create index
client.createIndex("users", Indexes.ascending("email"), 
    new IndexOptions().unique(true))
    .subscribe(indexName -> System.out.println("Created index: " + indexName));

// Compound index
client.createIndex("users", 
    Indexes.compoundIndex(
        Indexes.ascending("name"),
        Indexes.descending("age")
    ))
    .subscribe();
```

### Reactive Patterns

```java
// Chain operations
client.insertOne("users", newUser)
    .flatMap(result -> client.findById("users", result.getInsertedId()))
    .doOnNext(user -> System.out.println("Inserted and retrieved: " + user))
    .subscribe();

// Batch processing
Flux.fromIterable(userList)
    .buffer(100)  // Process in batches of 100
    .flatMap(batch -> client.insertMany("users", batch))
    .subscribe(result -> System.out.println("Inserted batch"));

// Error handling
client.findById("users", userId)
    .doOnError(error -> System.err.println("Error: " + error.getMessage()))
    .onErrorReturn(new JVS())  // Return empty JVS on error
    .subscribe(user -> processUser(user));
```

## Direct Collection Access

For advanced use cases, access MongoDB collections directly:

```java
import com.mongodb.reactivestreams.client.MongoCollection;

MongoCollection<JVS> collection = client.getCollection("users");

// Use any MongoDB driver operation
collection.find(Filters.eq("status", "active"))
    .limit(10)
    .subscribe(user -> System.out.println(user));
```

## Type Mapping

### BSON → JVS

| BSON Type | JVS Representation | Config |
|-----------|-------------------|--------|
| ObjectId  | String (hex) or `{$oid: "..."}` | `preserveObjectIdAsString` |
| Date      | Number (timestamp) or String | `preserveDateAsTimestamp` |
| Binary    | Base64 string or `{$binary: "..."}` | `preserveBinaryAsBase64` |
| Int32     | Number | - |
| Int64     | Number | - |
| Double    | Number | - |
| String    | String | - |
| Boolean   | Boolean | - |
| Array     | Array | - |
| Document  | Object | - |
| Null      | Null | - |

### JVS → BSON

- Automatic type detection for ObjectId patterns
- Nested objects and arrays fully supported
- Configurable _id field mapping (e.g., `id.id` → `_id`)

## Testing

Run tests with embedded MongoDB:

```bash
mvn test
```

## Examples

See `/src/test/java` for comprehensive examples including:
- Basic CRUD operations
- Complex queries
- Aggregation pipelines
- Reactive patterns
- Error handling
- Custom codec configuration

## Dependencies

- **MongoDB Reactive Streams Driver**: 5.2.1
- **MongoDB Sync Driver**: 5.2.1 (for blocking ops)
- **Project Reactor**: 3.6.11
- **Jackson**: 2.18.2 (via hitorro-util)
- **Hitorro Util**: 3.0.0

## Architecture

```
hitorro-jsonts-mongo/
├── codec/
│   ├── JVSCodec           # Core BSON ↔ JVS converter
│   ├── JVSCodecConfig     # Configuration
│   └── JVSCodecProvider   # MongoDB codec registry integration
├── query/
│   └── JVSQueryBuilder    # JVS → MongoDB query translator
└── JVSReactiveMongoClient # High-level reactive client API
```

## License

MIT License - Copyright (c) 2006-2025 Chris Collins

## Contributing

This module is part of the Hitorro project. For issues or contributions, see the main Hitorro repository.
