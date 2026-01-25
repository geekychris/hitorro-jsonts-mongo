/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.mongo;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.codec.JVSCodecConfig;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JVSReactiveMongoClient against a real MongoDB instance.
 * 
 * These tests require MongoDB to be running on localhost:27017.
 * Start MongoDB using: ./start-mongodb.sh
 * 
 * Tests are tagged with @Tag("integration") and can be run separately:
 * mvn test -Dgroups="integration"
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MongoIntegrationTest {
    
    private JVSReactiveMongoClient client;
    private static final String TEST_COLLECTION = "integration_test";
    private static final String TEST_DATABASE = "testdb";
    
    @BeforeAll
    void setUp() {
        try {
            // Connect to Docker MongoDB instance
            client = JVSReactiveMongoClient.builder()
                .connectionString("mongodb://localhost:27017")
                .database(TEST_DATABASE)
                .codecConfig(JVSCodecConfig.defaultConfig())
                .build();
            
            // Verify connection by counting documents
            client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
            System.out.println("✅ Connected to MongoDB on localhost:27017");
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to MongoDB on localhost:27017");
            System.err.println("   Please start MongoDB using: ./start-mongodb.sh");
            throw new RuntimeException("MongoDB not available", e);
        }
    }
    
    @AfterAll
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }
    
    @BeforeEach
    void cleanCollection() {
        // Clean up test collection before each test
        if (client != null) {
            client.dropCollection(TEST_COLLECTION).block(Duration.ofSeconds(5));
        }
    }
    
    @Test
    @DisplayName("Should insert and find a document")
    void testInsertAndFind() {
        JVS doc = new JVS()
            .set("name", "Alice")
            .set("age", 30)
            .set("email", "alice@example.com");
        
        StepVerifier.create(
            client.insertOne(TEST_COLLECTION, doc)
                .flatMap(result -> client.findById(TEST_COLLECTION, result.getInsertedId()))
        )
        .assertNext(found -> {
            assertEquals("Alice", found.getString("name"));
            assertEquals(30, found.getLong("age"));
            assertEquals("alice@example.com", found.getString("email"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should insert multiple documents and find all")
    void testInsertManyAndFindAll() {
        JVS doc1 = new JVS().set("name", "Alice").set("age", 30);
        JVS doc2 = new JVS().set("name", "Bob").set("age", 25);
        JVS doc3 = new JVS().set("name", "Charlie").set("age", 35);
        
        StepVerifier.create(
            client.insertMany(TEST_COLLECTION, java.util.Arrays.asList(doc1, doc2, doc3))
                .thenMany(client.findAll(TEST_COLLECTION))
        )
        .expectNextCount(3)
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should query documents with JVS filter")
    void testQueryWithJVSFilter() {
        // Insert test data
        JVS young = new JVS().set("name", "Young").set("age", 20);
        JVS middle = new JVS().set("name", "Middle").set("age", 30);
        JVS old = new JVS().set("name", "Old").set("age", 40);
        
        client.insertMany(TEST_COLLECTION, java.util.Arrays.asList(young, middle, old))
            .block(Duration.ofSeconds(5));
        
        // Query for age >= 30
        JVS query = new JVS().set("age.$gte", 30);
        
        StepVerifier.create(client.find(TEST_COLLECTION, query))
            .expectNextCount(2)  // Should find Middle and Old
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should update a document")
    void testUpdate() {
        JVS doc = new JVS().set("name", "Alice").set("age", 30);
        
        StepVerifier.create(
            client.insertOne(TEST_COLLECTION, doc)
                .flatMap(result -> client.updateOne(TEST_COLLECTION,
                    com.mongodb.client.model.Filters.eq("name", "Alice"),
                    com.mongodb.client.model.Updates.set("age", 31)))
                .doOnNext(updateResult -> assertEquals(1L, updateResult.getModifiedCount()))
                .flatMap(updateResult -> client.findOne(TEST_COLLECTION, 
                    com.mongodb.client.model.Filters.eq("name", "Alice")))
        )
        .assertNext(updated -> {
            assertEquals("Alice", updated.getString("name"));
            assertEquals(31, updated.getLong("age"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should delete a document")
    void testDelete() {
        JVS doc = new JVS().set("name", "ToDelete").set("age", 30);
        
        StepVerifier.create(
            client.insertOne(TEST_COLLECTION, doc)
                .flatMap(result -> client.deleteOne(TEST_COLLECTION, 
                    com.mongodb.client.model.Filters.eq("name", "ToDelete")))
                .doOnNext(deleteResult -> assertEquals(1L, deleteResult.getDeletedCount()))
                .flatMap(deleteResult -> client.count(TEST_COLLECTION))
        )
        .expectNext(0L)
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should handle nested objects")
    void testNestedObjects() {
        JVS doc = new JVS()
            .set("user.name", "Alice")
            .set("user.email", "alice@example.com")
            .set("user.age", 30);
        
        StepVerifier.create(
            client.insertOne(TEST_COLLECTION, doc)
                .flatMap(result -> client.findById(TEST_COLLECTION, result.getInsertedId()))
        )
        .assertNext(found -> {
            assertEquals("Alice", found.getString("user.name"));
            assertEquals("alice@example.com", found.getString("user.email"));
            assertEquals(30, found.getLong("user.age"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should count documents")
    void testCount() {
        JVS doc1 = new JVS().set("status", "active");
        JVS doc2 = new JVS().set("status", "active");
        JVS doc3 = new JVS().set("status", "inactive");
        
        client.insertMany(TEST_COLLECTION, java.util.Arrays.asList(doc1, doc2, doc3))
            .block(Duration.ofSeconds(5));
        
        // Count all
        Long totalCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(3L, totalCount);
        
        // Count with filter
        Long activeCount = client.count(TEST_COLLECTION, 
            com.mongodb.client.model.Filters.eq("status", "active"))
            .block(Duration.ofSeconds(5));
        assertEquals(2L, activeCount);
    }
    
    @Test
    @DisplayName("Should replace a document")
    void testReplace() {
        JVS original = new JVS().set("name", "Alice").set("age", 30);
        
        StepVerifier.create(
            client.insertOne(TEST_COLLECTION, original)
                .flatMap(result -> {
                    JVS replacement = new JVS()
                        .set("name", "Alice")
                        .set("age", 31)
                        .set("email", "alice@example.com");
                    return client.replaceById(TEST_COLLECTION, result.getInsertedId(), replacement);
                })
                .flatMap(updateResult -> client.findOne(TEST_COLLECTION, 
                    new JVS().set("name", "Alice")))
        )
        .assertNext(replaced -> {
            assertEquals("Alice", replaced.getString("name"));
            assertEquals(31, replaced.getLong("age"));
            assertEquals("alice@example.com", replaced.getString("email"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should save document without _id (insert)")
    void testSaveWithoutId() {
        JVS doc = new JVS()
            .set("name", "NewUser")
            .set("email", "new@example.com");
        
        StepVerifier.create(
            client.save(TEST_COLLECTION, doc)
                .flatMap(result -> {
                    assertNotNull(result.getUpsertedId());
                    return client.findById(TEST_COLLECTION, result.getUpsertedId().asObjectId().getValue());
                })
        )
        .assertNext(found -> {
            assertEquals("NewUser", found.getString("name"));
            assertEquals("new@example.com", found.getString("email"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should save document with existing _id (replace)")
    void testSaveWithExistingId() {
        JVS original = new JVS()
            .set("name", "Alice")
            .set("age", 30);
        
        StepVerifier.create(
            // First insert
            client.insertOne(TEST_COLLECTION, original)
                .flatMap(insertResult -> {
                    // Retrieve the document
                    return client.findById(TEST_COLLECTION, insertResult.getInsertedId())
                        .flatMap(retrieved -> {
                            // Modify it
                            retrieved.set("age", 31);
                            retrieved.set("email", "alice@example.com");
                            // Save (should replace)
                            return client.save(TEST_COLLECTION, retrieved);
                        });
                })
                .flatMap(saveResult -> {
                    // Verify only 1 document exists (not duplicated)
                    return client.count(TEST_COLLECTION);
                })
        )
        .assertNext(count -> {
            assertEquals(1L, count, "Should have exactly 1 document (no duplicates)");
        })
        .verifyComplete();
        
        // Verify the modifications were saved
        StepVerifier.create(client.findOne(TEST_COLLECTION, new JVS().set("name", "Alice")))
            .assertNext(found -> {
                assertEquals("Alice", found.getString("name"));
                assertEquals(31, found.getLong("age"));
                assertEquals("alice@example.com", found.getString("email"));
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should handle full create-retrieve-modify-save workflow")
    void testCreateRetrieveModifySaveWorkflow() {
        // 1. Create a document
        JVS original = new JVS()
            .set("name", "Bob")
            .set("status", "pending")
            .set("score", 0);
        
        StepVerifier.create(
            // Insert the document
            client.insertOne(TEST_COLLECTION, original)
                .flatMap(insertResult -> {
                    Object docId = insertResult.getInsertedId();
                    
                    // 2. Retrieve it
                    return client.findById(TEST_COLLECTION, docId)
                        .flatMap(retrieved -> {
                            // 3. Modify it
                            retrieved.set("status", "approved");
                            retrieved.set("score", 100);
                            retrieved.set("approvedBy", "admin");
                            
                            // 4. Save it (should replace, not duplicate)
                            return client.save(TEST_COLLECTION, retrieved)
                                .then(Mono.just(docId));
                        });
                })
                .flatMap(docId -> {
                    // Verify count = 1 (no duplication)
                    return client.count(TEST_COLLECTION)
                        .flatMap(count -> {
                            assertEquals(1L, count, "Should have exactly 1 document");
                            // Retrieve and verify changes
                            return client.findById(TEST_COLLECTION, docId);
                        });
                })
        )
        .assertNext(finalDoc -> {
            assertEquals("Bob", finalDoc.getString("name"));
            assertEquals("approved", finalDoc.getString("status"));
            assertEquals(100, finalDoc.getLong("score"));
            assertEquals("admin", finalDoc.getString("approvedBy"));
        })
        .verifyComplete();
    }
    
    @Test
    @DisplayName("Should save multiple times without creating duplicates")
    void testMultipleSavesNoDuplicates() {
        JVS doc = new JVS()
            .set("name", "Counter")
            .set("value", 1);
        
        StepVerifier.create(
            // First save (insert)
            client.save(TEST_COLLECTION, doc)
                .flatMap(result1 -> {
                    Object id = result1.getUpsertedId();
                    return client.findById(TEST_COLLECTION, id)
                        .flatMap(retrieved1 -> {
                            // Modify and save again
                            retrieved1.set("value", 2);
                            return client.save(TEST_COLLECTION, retrieved1)
                                .then(client.findById(TEST_COLLECTION, id));
                        })
                        .flatMap(retrieved2 -> {
                            // Modify and save a third time
                            retrieved2.set("value", 3);
                            return client.save(TEST_COLLECTION, retrieved2)
                                .then(client.count(TEST_COLLECTION));
                        });
                })
        )
        .assertNext(count -> {
            assertEquals(1L, count, "Should still have exactly 1 document after multiple saves");
        })
        .verifyComplete();
        
        // Verify final value
        StepVerifier.create(client.findOne(TEST_COLLECTION, new JVS().set("name", "Counter")))
            .assertNext(finalDoc -> {
                assertEquals(3, finalDoc.getLong("value"));
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should save with manual _id without creating duplicates")
    void testSaveWithManualId() {
        String customId = "custom-id-12345";
        
        JVS doc1 = new JVS()
            .set("_id", customId)
            .set("name", "Manual ID Doc")
            .set("version", 1);
        
        StepVerifier.create(
            // First save with custom ID
            client.save(TEST_COLLECTION, doc1)
                .flatMap(result -> {
                    // Modify and save again with same ID
                    JVS doc2 = new JVS()
                        .set("_id", customId)
                        .set("name", "Manual ID Doc")
                        .set("version", 2);
                    return client.save(TEST_COLLECTION, doc2);
                })
                .flatMap(result -> client.count(TEST_COLLECTION))
        )
        .assertNext(count -> {
            assertEquals(1L, count, "Should have exactly 1 document with custom ID");
        })
        .verifyComplete();
        
        // Verify it was updated, not duplicated
        StepVerifier.create(client.findById(TEST_COLLECTION, customId))
            .assertNext(found -> {
                assertEquals(customId, found.getString("_id"));
                assertEquals(2, found.getLong("version"));
            })
            .verifyComplete();
    }
}
