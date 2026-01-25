# Hitorro JSON Type System MongoDB Integration - Project Summary

## Overview

`hitorro-jsonts-mongo` is a comprehensive MongoDB integration module for the Hitorro JSON Type System (JVS). It provides seamless, reactive MongoDB operations with automatic BSON mapping, enabling efficient storage and retrieval of JVS documents.

## Key Components

### 1. Codec Layer (`com.hitorro.mongo.codec`)

**JVSCodec**
- Core bidirectional converter between JVS (Jackson JsonNode) and MongoDB BSON
- Handles type preservation for BSON-specific types (ObjectId, Date, Binary)
- Configurable field mapping strategies

**JVSCodecConfig**
- Builder-based configuration for codec behavior
- Controls _id field mapping, type preservation, and auto-detection
- Includes presets: `defaultConfig()` and `jvsStandardConfig()`

**JVSCodecProvider**
- Integrates JVSCodec into MongoDB's codec registry system
- Enables seamless use of JVS with MongoDB collections

### 2. Query Layer (`com.hitorro.mongo.query`)

**JVSQueryBuilder**
- Converts JVS query objects to MongoDB BSON filters
- Supports MongoDB query operators ($eq, $gt, $gte, $lt, $lte, $in, etc.)
- Handles complex queries ($and, $or, $nor)
- Provides convenient field.operator syntax (e.g., `age.$gte`)
- Includes update operation builders

### 3. Client Layer (`com.hitorro.mongo`)

**JVSReactiveMongoClient**
- High-level reactive MongoDB client built on MongoDB Reactive Streams
- Uses Project Reactor (Mono/Flux) for reactive operations
- Comprehensive CRUD operations
- Advanced features: aggregation, indexing, transactions
- Connection pooling with configurable settings
- Builder pattern for easy configuration

## Features

### Core Capabilities
- ✅ Reactive MongoDB operations via Project Reactor
- ✅ Automatic JVS ↔ BSON conversion
- ✅ Connection pooling with configurable limits
- ✅ Both JVS-based and native BSON query support
- ✅ Full CRUD operations (Create, Read, Update, Delete)
- ✅ Aggregation pipeline support
- ✅ Index management
- ✅ Type-safe document handling

### BSON Type Mapping
- ✅ ObjectId (as string or structured object)
- ✅ Date/DateTime (as timestamp or ISO string)
- ✅ Binary data (as Base64 or structured object)
- ✅ All standard BSON numeric types
- ✅ Nested documents and arrays
- ✅ Null values

### Configuration Options
- ✅ Custom _id field mapping (e.g., `id.id` → `_id`)
- ✅ ObjectId string/object conversion
- ✅ Date format preferences
- ✅ Binary encoding options
- ✅ BSON type auto-detection
- ✅ Connection pool sizing
- ✅ Connection timeout settings

## Architecture

```
┌─────────────────────────────────────────┐
│   Application Layer (User Code)        │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│   JVSReactiveMongoClient                │
│   - Builder pattern                     │
│   - CRUD operations                     │
│   - Reactive (Mono/Flux)                │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
┌───────▼──────┐   ┌────────▼────────┐
│ JVSCodec     │   │ JVSQueryBuilder │
│ - Encoding   │   │ - JVS → Bson    │
│ - Decoding   │   │ - Operators     │
└───────┬──────┘   └────────┬────────┘
        │                   │
        └─────────┬─────────┘
                  │
┌─────────────────▼───────────────────────┐
│   MongoDB Reactive Streams Driver       │
│   - Connection management               │
│   - BSON protocol                       │
│   - Reactive publishers                 │
└─────────────────────────────────────────┘
```

## Usage Patterns

### Simple CRUD
```java
// Create client
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .build();

// Insert
JVS doc = new JVS().set("name", "John").set("age", 30);
client.insertOne("users", doc).block();

// Query
Flux<JVS> users = client.find("users", new JVS().set("age.$gte", 18));

// Update
client.updateOne("users", 
    new JVS().set("name", "John"),
    new JVS().set("$set.age", 31)).block();
```

### Advanced Queries
```java
// Complex query with operators
JVS query = new JVS()
    .set("age.$gte", 18)
    .set("age.$lt", 65)
    .set("status", "active");

// Or using native MongoDB
Bson filter = Filters.and(
    Filters.gte("age", 18),
    Filters.lt("age", 65),
    Filters.eq("status", "active")
);
```

### Reactive Patterns
```java
// Chain operations
client.insertOne("users", newUser)
    .flatMap(result -> client.findById("users", result.getInsertedId()))
    .subscribe(user -> System.out.println("Created: " + user));

// Batch processing
Flux.fromIterable(documents)
    .buffer(100)
    .flatMap(batch -> client.insertMany("collection", batch))
    .subscribe();
```

## Testing

- Unit tests for codec conversion
- Integration tests with embedded MongoDB
- Reactive stream testing with reactor-test
- Configuration validation tests

Run tests:
```bash
mvn test
```

## Dependencies

### Runtime
- **MongoDB Reactive Streams Driver**: 5.2.1
- **MongoDB Sync Driver**: 5.2.1
- **Project Reactor Core**: 3.6.11
- **BSON**: 5.2.1
- **Hitorro Util**: 3.0.0 (provides JVS and Jackson)

### Test
- **JUnit 4**: 4.13.2
- **JUnit 5**: 5.11.4
- **AssertJ**: 3.27.3
- **Mockito**: 5.14.2
- **Reactor Test**: 3.6.11
- **Embedded MongoDB**: 4.16.2

## Build & Installation

```bash
# Build hitorro-util first
cd ../hitorro-util
mvn clean install

# Build this module
cd ../hitorro-jsonts-mongo
mvn clean install
```

## File Structure

```
hitorro-jsonts-mongo/
├── pom.xml
├── README.md
├── QUICKSTART.md
├── PROJECT_SUMMARY.md
├── build.sh
├── .gitignore
└── src/
    ├── main/
    │   └── java/com/hitorro/mongo/
    │       ├── JVSReactiveMongoClient.java
    │       ├── codec/
    │       │   ├── JVSCodec.java
    │       │   ├── JVSCodecConfig.java
    │       │   └── JVSCodecProvider.java
    │       └── query/
    │           └── JVSQueryBuilder.java
    └── test/
        └── java/com/hitorro/mongo/
            └── codec/
                └── JVSCodecTest.java
```

## Future Enhancements

Potential areas for expansion:
- Change streams support
- Transactions API wrapper
- GridFS integration for large objects
- Advanced aggregation builders
- Query result projections
- Bulk write operations with retry logic
- Metrics and monitoring integration
- Spring Data MongoDB compatibility layer

## License

MIT License - Copyright (c) 2006-2025 Chris Collins

## Contributing

Part of the Hitorro project ecosystem. Contributions welcome.
