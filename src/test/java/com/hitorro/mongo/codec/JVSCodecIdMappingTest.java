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
package com.hitorro.mongo.codec;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JVS id.id field mapping to MongoDB _id.
 * Verifies that the codec uses JVS getter to retrieve computed id.id values.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JVSCodecIdMappingTest {
    
    private JVSReactiveMongoClient client;
    private static final String TEST_COLLECTION = "id_mapping_test";
    private static final String TEST_DATABASE = "testdb";
    
    @BeforeAll
    void setUp() {
        try {
            client = JVSReactiveMongoClient.builder()
                .connectionString("mongodb://localhost:27017")
                .database(TEST_DATABASE)
                .codecConfig(JVSCodecConfig.jvsStandardConfig())  // Maps id.id <-> _id
                .build();
            
            client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
            System.out.println("✅ Connected to MongoDB for id mapping tests");
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to MongoDB");
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
        if (client != null) {
            client.dropCollection(TEST_COLLECTION).block(Duration.ofSeconds(5));
        }
    }
    
    @Test
    @DisplayName("Should use JVS getter for id.id when writing to MongoDB")
    void testComputedIdIdMapping() throws Exception {
        // Create JVS with id structure including id.id field
        JVS doc = new JVS()
            .set("id.domain", "test-domain")
            .set("id.value", "12345")
            .set("id.id", "test-domain:12345")  // Manually set id.id
            .set("name", "Test Document");
        
        // Get the id.id value
        String idIdValue = doc.getString("id.id");
        assertNotNull(idIdValue, "id.id should exist");
        System.out.println("id.id value: " + idIdValue);
        
        // Insert document - codec should use getter for id.id -> _id mapping
        client.insertOne(TEST_COLLECTION, doc).block(Duration.ofSeconds(5));
        
        // Read back and verify _id was set to computed id.id value
        JVS retrieved = client.findOne(TEST_COLLECTION, new JVS()).block(Duration.ofSeconds(5));
        assertNotNull(retrieved);
        
        // When reading, _id should be mapped back to id.id
        String retrievedId = retrieved.getString("id.id");
        assertEquals(idIdValue, retrievedId, "Retrieved id.id should match original value");
        
        // Verify the id object still has domain and value
        assertEquals("test-domain", retrieved.getString("id.domain"));
        assertEquals("12345", retrieved.getString("id.value"));
        
        System.out.println("✅ id.id field correctly mapped to/from _id");
    }
    
    @Test
    @DisplayName("Should preserve entire id object structure when mapping id.id")
    void testIdObjectPreservation() throws Exception {
        // Create JVS with full id object
        JVS doc = new JVS()
            .set("id.domain", "mydomain")
            .set("id.value", "abc123")
            .set("id.id", "mydomain:abc123")
            .set("id.type", "custom")
            .set("data", "some data");
        
        String idId = doc.getString("id.id");
        System.out.println("Original id.id: " + idId);
        
        // Insert
        client.insertOne(TEST_COLLECTION, doc).block(Duration.ofSeconds(5));
        
        // Retrieve
        JVS retrieved = client.findOne(TEST_COLLECTION, new JVS()).block(Duration.ofSeconds(5));
        assertNotNull(retrieved);
        
        // Verify all id subfields are preserved
        assertEquals("mydomain", retrieved.getString("id.domain"));
        assertEquals("abc123", retrieved.getString("id.value"));
        assertEquals("custom", retrieved.getString("id.type"));
        assertEquals("some data", retrieved.getString("data"));
        
        // Verify id.id is restored from _id
        assertEquals(idId, retrieved.getString("id.id"));
        
        System.out.println("✅ Entire id object structure preserved");
    }
    
    @Test
    @DisplayName("Should handle documents without id.id gracefully")
    void testMissingIdId() throws Exception {
        // Create JVS without any id field - MongoDB should auto-generate _id
        JVS doc = new JVS()
            .set("name", "No ID")
            .set("value", 42);
        
        // Insert - should succeed with auto-generated _id
        var result = client.insertOne(TEST_COLLECTION, doc).block(Duration.ofSeconds(5));
        assertNotNull(result);
        
        // Retrieve and verify it has an _id mapped to id.id
        JVS retrieved = client.findOne(TEST_COLLECTION, new JVS().set("name", "No ID"))
            .block(Duration.ofSeconds(5));
        assertNotNull(retrieved);
        
        // Should have auto-generated id.id from MongoDB's _id
        assertNotNull(retrieved.get("id.id"), "id.id should exist from MongoDB _id");
        System.out.println("Auto-generated id.id: " + retrieved.getString("id.id"));
        
        System.out.println("✅ Auto-generated _id correctly mapped to id.id");
    }
}
