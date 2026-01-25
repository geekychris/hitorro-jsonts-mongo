# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

This is a MongoDB integration library for the Hitorro JSON Type System (JVS), providing reactive MongoDB operations with automatic BSON ↔ JVS conversion. The library bridges JVS (a Jackson JsonNode-based type system) with MongoDB's reactive streams driver.

## Build System & Commands

**Build & Install:**
```bash
./build.sh
# or
mvn clean install
```

**Run Tests:**
```bash
mvn test
```

**Skip Tests During Build:**
```bash
mvn clean install -DskipTests
```

**Build Sources JAR:**
```bash
mvn source:jar
```

**Build Javadoc:**
```bash
mvn javadoc:jar
```

**Important:** This module depends on `hitorro-util:3.0.0`. If it's not in your local Maven repository, you must build it first from the sibling directory `../hitorro-util` using `mvn clean install`.

## Architecture

### Core Components

The library is organized into three main packages:

**1. `com.hitorro.mongo.codec`** - BSON ↔ JVS Conversion Layer
- `JVSCodec`: The main codec implementing MongoDB's `Codec<JVS>` interface
  - Converts JVS (Jackson JsonNode) to BSON and vice versa
  - Handles type preservation for MongoDB-specific types (ObjectId, Date, Binary)
  - Supports nested objects and arrays
- `JVSCodecConfig`: Configuration controlling codec behavior
  - `defaultConfig()`: Direct `_id` mapping, no special field transformation
  - `jvsStandardConfig()`: Maps MongoDB `_id` to `id.id` field in JVS
  - Builder pattern for custom configurations
- `JVSCodecProvider`: MongoDB codec registry integration

**2. `com.hitorro.mongo.query`** - Query Translation
- `JVSQueryBuilder`: Converts JVS query objects to MongoDB BSON filters
  - Supports simple equality queries: `new JVS().set("name", "John")`
  - Supports MongoDB operators with dot notation: `new JVS().set("age.$gte", 18)`
  - Handles complex operators: `$and`, `$or`, `$nor`
  - Converts JVS update documents to BSON update operations

**3. `com.hitorro.mongo`** - High-Level Client API
- `JVSReactiveMongoClient`: Main reactive client with builder pattern
  - All operations return Project Reactor `Mono<T>` or `Flux<T>`
  - Configurable connection pooling
  - Full CRUD operations plus aggregation, indexes, transactions

### Key Design Patterns

**Field Mapping:**
- MongoDB's `_id` field can be mapped to/from any JVS field path (e.g., `id.id`, `id.did`)
- Configured via `JVSCodecConfig` to match your domain model conventions
- Default config uses `_id` directly; JVS standard config maps to `id.id`

**Query Syntax:**
The library supports two query approaches:
1. JVS-based queries with dot notation for operators: `query.set("age.$gte", 18)`
2. Native MongoDB BSON filters: `Filters.gte("age", 18)`

**Reactive Patterns:**
- All operations use Project Reactor (`Mono`/`Flux`)
- Supports chaining, error handling, backpressure
- Compatible with Spring WebFlux and other reactive frameworks

## MongoDB Setup for Development

### Using Docker (Recommended)

**Quick Start with Provided Scripts:**

The project includes Docker setup scripts for easy MongoDB management:

```bash
# Start MongoDB with Docker Compose
./start-mongodb.sh

# Start with web admin interface (Mongo Express on http://localhost:8081)
./start-mongodb.sh --with-admin

# Stop MongoDB
./stop-mongodb.sh

# Stop and remove all data
./stop-mongodb.sh --remove-data
```

**Alternative: Simple Docker Script:**
```bash
# Start MongoDB
./docker-mongodb.sh start

# Start with authentication
./docker-mongodb.sh start --with-auth

# View logs
./docker-mongodb.sh logs

# Connect to MongoDB shell
./docker-mongodb.sh shell

# Stop MongoDB
./docker-mongodb.sh stop
```

See `DOCKER.md` for complete documentation.

**Manual Docker Command:**
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_DATABASE=testdb \
  mongo:latest
```

**Start with Authentication:**
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  -e MONGO_INITDB_DATABASE=testdb \
  mongo:latest
```

**Connection String with Auth:**
```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://admin:password@localhost:27017/testdb?authSource=admin")
    .database("testdb")
    .build();
```

**Common Docker Commands:**
```bash
# Stop MongoDB
docker stop mongodb

# Start existing container
docker start mongodb

# View logs
docker logs mongodb

# Connect with mongo shell
docker exec -it mongodb mongosh

# Remove container (data will be lost)
docker rm -f mongodb

# Start with persistent volume
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  mongo:latest
```

**Docker Compose Setup:**
Create `docker-compose.yml` in project root:
```yaml
version: '3.8'
services:
  mongodb:
    image: mongo:latest
    container_name: hitorro-mongodb
    ports:
      - "27017:27017"
    environment:
      - MONGO_INITDB_DATABASE=testdb
    volumes:
      - mongodb_data:/data/db
    networks:
      - hitorro-network

volumes:
  mongodb_data:

networks:
  hitorro-network:
    driver: bridge
```

Then use:
```bash
# Start MongoDB
docker-compose up -d

# Stop MongoDB
docker-compose down

# Stop and remove volumes (delete all data)
docker-compose down -v
```

### Using Local MongoDB Installation

**macOS (Homebrew):**
```bash
brew install mongodb-community
brew services start mongodb-community
```

**Default Connection:**
```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .build();
```

## Testing

### Test Framework
**Frameworks:** JUnit 4 and JUnit 5 (Jupiter)
- Tests use embedded MongoDB (`de.flapdoodle.embed.mongo:4.16.2`) - no external MongoDB required
- Test files follow `*Test.java` naming convention
- AssertJ is available for fluent assertions
- Mockito available for mocking
- reactor-test available for testing reactive streams

### Running Tests

**Run All Tests:**
```bash
mvn test
```

**Run Single Test Class:**
```bash
mvn test -Dtest=JVSCodecTest
```

**Run Specific Test Method:**
```bash
mvn test -Dtest=JVSCodecTest#testBasicEncodeDecode
```

**Run Tests with Debug Output:**
```bash
mvn test -X
```

**Skip Tests During Build:**
```bash
mvn clean install -DskipTests
```

### Writing Tests

**Basic Test Structure:**
```java
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import com.hitorro.mongo.codec.JVSCodecConfig;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class MyMongoTest {
    
    @Test
    void testInsertAndFind() {
        // Tests use embedded MongoDB - no external setup needed
        JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
            .connectionString("mongodb://localhost:27017")
            .database("testdb")
            .build();
        
        JVS doc = new JVS().set("name", "test").set("value", 123);
        
        // Use StepVerifier for reactive testing
        StepVerifier.create(
            client.insertOne("mycollection", doc)
                .flatMap(result -> client.findById("mycollection", result.getInsertedId()))
        )
        .assertNext(found -> {
            assertEquals("test", found.getString("name"));
            assertEquals(123, found.getLong("value"));
        })
        .verifyComplete();
        
        client.close();
    }
}
```

## API Reference

### JVSReactiveMongoClient - Core Operations

**Client Creation:**
```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .codecConfig(JVSCodecConfig.defaultConfig())  // or jvsStandardConfig()
    .maxConnectionPoolSize(100)
    .minConnectionPoolSize(0)
    .maxWaitTime(120000)  // milliseconds
    .maxConnectionIdleTime(0)  // 0 = unlimited
    .build();
```

**Insert Operations:**
```java
// Insert single document
Mono<InsertOneResult> insertOne(String collectionName, JVS document)

// Insert multiple documents
Mono<InsertManyResult> insertMany(String collectionName, List<JVS> documents)

// Example:
client.insertOne("users", new JVS().set("name", "John"))
    .subscribe(result -> System.out.println(result.getInsertedId()));
```

**Find Operations:**
```java
// Find all documents
Flux<JVS> findAll(String collectionName)

// Find with BSON filter
Flux<JVS> find(String collectionName, Bson filter)

// Find with JVS query
Flux<JVS> find(String collectionName, JVS query)

// Find by ID
Mono<JVS> findById(String collectionName, Object id)

// Find one document
Mono<JVS> findOne(String collectionName, Bson filter)
Mono<JVS> findOne(String collectionName, JVS query)

// Examples:
client.find("users", new JVS().set("age.$gte", 18))
    .subscribe(user -> System.out.println(user));

client.findById("users", someObjectId)
    .subscribe(user -> processUser(user));
```

**Update Operations:**
```java
// Update single document
Mono<UpdateResult> updateOne(String collectionName, Bson filter, Bson update)
Mono<UpdateResult> updateOne(String collectionName, JVS filter, JVS update)

// Update multiple documents
Mono<UpdateResult> updateMany(String collectionName, Bson filter, Bson update)

// Replace document
Mono<UpdateResult> replaceOne(String collectionName, Bson filter, JVS replacement)
Mono<UpdateResult> replaceById(String collectionName, Object id, JVS replacement)

// Examples:
// Update with JVS (dot notation for operators)
client.updateOne("users",
    new JVS().set("name", "John"),
    new JVS().set("$set.age", 31).set("$inc.loginCount", 1))
    .subscribe(result -> System.out.println("Modified: " + result.getModifiedCount()));

// Update with BSON
import com.mongodb.client.model.Updates;
client.updateOne("users",
    Filters.eq("name", "John"),
    Updates.combine(Updates.set("age", 31), Updates.inc("loginCount", 1)))
    .subscribe();
```

**Delete Operations:**
```java
// Delete single document
Mono<DeleteResult> deleteOne(String collectionName, Bson filter)

// Delete by ID
Mono<DeleteResult> deleteById(String collectionName, Object id)

// Delete multiple documents
Mono<DeleteResult> deleteMany(String collectionName, Bson filter)

// Example:
client.deleteOne("users", new JVS().set("status", "inactive"))
    .subscribe(result -> System.out.println("Deleted: " + result.getDeletedCount()));
```

**Count Operations:**
```java
// Count with filter
Mono<Long> count(String collectionName, Bson filter)

// Count all
Mono<Long> count(String collectionName)

// Example:
client.count("users", Filters.eq("active", true))
    .subscribe(count -> System.out.println("Active users: " + count));
```

**Aggregation:**
```java
Flux<JVS> aggregate(String collectionName, List<Bson> pipeline)

// Example:
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Sorts;

client.aggregate("users", Arrays.asList(
    Aggregates.match(Filters.gte("age", 18)),
    Aggregates.group("$department", 
        Accumulators.avg("avgAge", "$age"),
        Accumulators.sum("count", 1)),
    Aggregates.sort(Sorts.descending("count"))
))
.subscribe(result -> System.out.println(result));
```

**Index Operations:**
```java
// Create index
Mono<String> createIndex(String collectionName, Bson keys)
Mono<String> createIndex(String collectionName, Bson keys, IndexOptions options)

// Drop index
Mono<Void> dropIndex(String collectionName, String indexName)

// Example:
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

client.createIndex("users", 
    Indexes.ascending("email"),
    new IndexOptions().unique(true))
    .subscribe(indexName -> System.out.println("Created: " + indexName));

// Compound index
client.createIndex("users", 
    Indexes.compoundIndex(
        Indexes.ascending("lastName"),
        Indexes.ascending("firstName")
    ))
    .subscribe();
```

**Collection Operations:**
```java
// Get collection for advanced operations
MongoCollection<JVS> getCollection(String collectionName)

// Drop collection
Mono<Void> dropCollection(String collectionName)

// Example - direct collection access:
MongoCollection<JVS> collection = client.getCollection("users");
collection.find(Filters.eq("status", "active"))
    .limit(10)
    .skip(20)
    .subscribe(user -> processUser(user));
```

**Client Management:**
```java
// Get underlying MongoDB client
MongoClient getMongoClient()

// Get database name
String getDatabaseName()

// Close client (implements AutoCloseable)
void close()

// Example with try-with-resources:
try (JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
        .connectionString("mongodb://localhost:27017")
        .database("mydb")
        .build()) {
    // Use client
    client.findAll("users").blockLast();
} // Automatically closed
```

### JVSQueryBuilder - Query Construction

**Simple Equality:**
```java
JVS query = new JVS().set("name", "John").set("age", 30);
Bson filter = JVSQueryBuilder.fromJVS(query);
// Translates to: {name: "John", age: 30}
```

**Comparison Operators:**
```java
JVS query = new JVS()
    .set("age.$gte", 18)     // age >= 18
    .set("age.$lt", 65)      // age < 65
    .set("score.$gt", 80)    // score > 80
    .set("score.$lte", 100); // score <= 100
```

**Array and String Operators:**
```java
JVS query = new JVS()
    .set("tags.$in", Arrays.asList("admin", "moderator"))  // tags in ["admin", "moderator"]
    .set("status.$ne", "deleted")                          // status != "deleted"
    .set("name.$regex", "^John");                          // name matches regex
```

**Logical Operators:**
```java
// $or operator
JVS query = new JVS().set("$or", Arrays.asList(
    new JVS().set("status", "active"),
    new JVS().set("role", "admin")
));

// $and operator
JVS query = new JVS().set("$and", Arrays.asList(
    new JVS().set("age.$gte", 18),
    new JVS().set("verified", true)
));

// $nor operator
JVS query = new JVS().set("$nor", Arrays.asList(
    new JVS().set("status", "banned"),
    new JVS().set("deleted", true)
));
```

**Update Documents:**
```java
// JVS update with operators
JVS update = new JVS()
    .set("$set.name", "John Doe")
    .set("$set.lastModified", System.currentTimeMillis())
    .set("$inc.loginCount", 1)
    .set("$push.tags", "verified");

List<Bson> updateOps = JVSQueryBuilder.toUpdateBson(update);
```

### JVSCodecConfig - Configuration Options

**Default Configuration:**
```java
JVSCodecConfig config = JVSCodecConfig.defaultConfig();
// - Direct _id mapping (no field transformation)
// - Preserve ObjectId as string
// - Preserve dates as timestamps
// - Auto-detect BSON types
```

**JVS Standard Configuration:**
```java
JVSCodecConfig config = JVSCodecConfig.jvsStandardConfig();
// - Maps MongoDB _id to JVS field "id.id"
// - Useful when your domain model uses id.id convention
```

**Custom Configuration:**
```java
JVSCodecConfig config = JVSCodecConfig.builder()
    .mapIdField(true)                      // Enable _id field mapping
    .jvsIdField("id.did")                  // Map to custom field path
    .convertToObjectId(true)               // Auto-convert string IDs to ObjectId
    .preserveObjectIdAsString(true)        // Store ObjectId as hex string
    .preserveDateAsTimestamp(true)         // Store dates as milliseconds
    .preserveBinaryAsBase64(true)          // Store binary as base64
    .detectBsonTypes(true)                 // Auto-detect ObjectId patterns
    .build();
```

## Dependencies & Versions

- **Java:** 21 (required - both source and target)
- **MongoDB Reactive Driver:** 5.2.1
- **Project Reactor:** 3.6.11
- **Jackson:** 2.18.2 (transitively from hitorro-util)
- **Hitorro Util:** 3.0.0 (contains JVS core)

## Code Conventions

**JVS Field Access:**
- Use dot notation for nested fields: `jvs.set("user.name", "John")` creates nested structure
- Use `getString()`, `getLong()`, `get()` methods for retrieval
- Direct JSON node access via `jvs.getJsonNode()` when needed

**Codec Configuration:**
When creating a client, choose the appropriate codec config based on your domain model:
- Use `defaultConfig()` if working directly with MongoDB `_id`
- Use `jvsStandardConfig()` if your JVS objects use `id.id` field convention
- Build custom config if you need different field mappings or type preservation settings

**Error Handling:**
Reactive operations require explicit error handling:
```java
client.findById("users", id)
    .doOnError(err -> log.error("Find failed", err))
    .onErrorReturn(new JVS())  // or other fallback
    .subscribe(result -> processResult(result));
```

## Common Pitfalls

**ID Field Mapping Confusion:**
If documents aren't found or IDs are missing, check the codec config. MongoDB always uses `_id` internally, but the codec may map it to a different JVS field based on configuration.

**Operator Syntax:**
Query operators MUST use dot notation in JVS: `"age.$gte"` not `"age": {"$gte": ...}`

**Reactive Stream Completion:**
Remember to call `.subscribe()`, `.block()`, or `.blockLast()` to trigger execution. Reactive streams are lazy - just creating a `Mono` or `Flux` doesn't execute the operation.

**Dependency on hitorro-util:**
If you get compile errors about missing JVS class, ensure `hitorro-util` is built and installed in your local Maven repository first.

## Project Structure

```
hitorro-jsonts-mongo/
├── pom.xml                      # Maven build configuration
├── build.sh                     # Convenience build script
├── README.md                    # User documentation
├── QUICKSTART.md                # Quick start guide
└── src/
    ├── main/java/com/hitorro/mongo/
    │   ├── JVSReactiveMongoClient.java  # Main client API
    │   ├── codec/
    │   │   ├── JVSCodec.java            # BSON codec implementation
    │   │   ├── JVSCodecConfig.java      # Codec configuration
    │   │   └── JVSCodecProvider.java    # Codec registry provider
    │   └── query/
    │       └── JVSQueryBuilder.java     # JVS→BSON query translator
    └── test/java/com/hitorro/mongo/
        └── codec/
            └── JVSCodecTest.java        # Codec unit tests
```
