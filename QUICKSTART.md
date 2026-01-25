# Quick Start Guide

## Installation

### 1. Build and Install hitorro-util

Since this module depends on `hitorro-util`, make sure it's built first:

```bash
cd ../hitorro-util
mvn clean install
```

### 2. Build hitorro-jsonts-mongo

```bash
cd ../hitorro-jsonts-mongo
./build.sh
# or
mvn clean install
```

## Basic Example

Create a simple Java application:

```java
package com.example;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import reactor.core.publisher.Mono;

public class MongoExample {
    public static void main(String[] args) throws Exception {
        // Create client
        JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
            .connectionString("mongodb://localhost:27017")
            .database("testdb")
            .build();
        
        // Create and insert a document
        JVS user = new JVS()
            .set("name", "Alice")
            .set("age", 25)
            .set("email", "alice@example.com");
        
        // Insert and wait for result
        client.insertOne("users", user)
            .doOnSuccess(result -> 
                System.out.println("Inserted with ID: " + result.getInsertedId()))
            .block();
        
        // Query documents
        client.find("users", new JVS().set("age.$gte", 18))
            .doOnNext(doc -> 
                System.out.println("Found: " + doc.getString("name")))
            .blockLast();
        
        // Update
        client.updateOne("users",
            new JVS().set("name", "Alice"),
            new JVS().set("$set.age", 26))
            .doOnSuccess(result ->
                System.out.println("Modified: " + result.getModifiedCount()))
            .block();
        
        // Clean up
        client.close();
    }
}
```

## MongoDB Setup

### Using Docker

```bash
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

### Using Local Installation

Make sure MongoDB is running on `localhost:27017`

## Running Tests

The module includes tests with embedded MongoDB:

```bash
mvn test
```

## Common Operations

### Insert

```java
JVS doc = new JVS().set("field", "value");
client.insertOne("collection", doc).block();
```

### Find

```java
// Find all
Flux<JVS> all = client.findAll("collection");

// Find with query
Flux<JVS> filtered = client.find("collection", 
    new JVS().set("status", "active"));

// Find by ID
Mono<JVS> one = client.findById("collection", someId);
```

### Update

```java
client.updateOne("collection",
    new JVS().set("name", "John"),  // filter
    new JVS().set("$set.age", 31))  // update
    .block();
```

### Delete

```java
client.deleteOne("collection", new JVS().set("name", "John")).block();
```

## Configuration Examples

### With JVS Standard Fields

Map MongoDB `_id` to `id.id`:

```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .codecConfig(JVSCodecConfig.jvsStandardConfig())
    .build();
```

### With Connection Pooling

```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .maxConnectionPoolSize(200)
    .minConnectionPoolSize(10)
    .build();
```

## Next Steps

- Read the [README](README.md) for detailed documentation
- Check the test files for more examples
- Explore MongoDB aggregation pipelines
- Learn about reactive patterns with Project Reactor
