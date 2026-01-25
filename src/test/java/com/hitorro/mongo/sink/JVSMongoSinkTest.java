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
package com.hitorro.mongo.sink;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import com.hitorro.mongo.codec.JVSCodecConfig;
import com.hitorro.mongo.iterator.JVSMongoIterator;
import com.hitorro.util.core.iterator.AbstractIterator;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JVSMongoSink - sinking iterators to MongoDB.
 * 
 * These tests require MongoDB to be running on localhost:27017.
 * Start MongoDB using: ./start-mongodb.sh
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JVSMongoSinkTest {
    
    /**
     * Simple list-backed iterator for testing.
     */
    private static class ListIterator<E> extends AbstractIterator<E> {
        private final List<E> list;
        private int pos = 0;
        
        public ListIterator(List<E> list) {
            this.list = list;
        }
        
        @Override
        public boolean hasNext() {
            return pos < list.size();
        }
        
        @Override
        public E next() {
            return list.get(pos++);
        }
        
        @Override
        public void close() throws Exception {
            // No resources to close
        }
        
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    private JVSReactiveMongoClient client;
    private static final String TEST_COLLECTION = "sink_test";
    private static final String TEST_DATABASE = "testdb";
    
    @BeforeAll
    void setUp() {
        try {
            client = JVSReactiveMongoClient.builder()
                .connectionString("mongodb://localhost:27017")
                .database(TEST_DATABASE)
                .codecConfig(JVSCodecConfig.defaultConfig())
                .build();
            
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
        if (client != null) {
            client.dropCollection(TEST_COLLECTION).block(Duration.ofSeconds(5));
        }
    }
    
    @Test
    @DisplayName("Should sink documents from iterator to MongoDB")
    void testBasicSink() throws Exception {
        // Create test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Alice").set("age", 30),
            new JVS().set("name", "Bob").set("age", 25),
            new JVS().set("name", "Charlie").set("age", 35)
        );
        
        // Create iterator
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        
        // Create sink and sink data
        JVSMongoSink sink = client.createSink(TEST_COLLECTION);
        int count = iter.sink(sink);
        
        assertEquals(3, count);
        assertEquals(3, sink.getTotalInserted());
        assertEquals(0, sink.getTotalErrors());
        
        // Verify data was inserted
        Long docCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(3L, docCount);
    }
    
    @Test
    @DisplayName("Should handle batching correctly")
    void testBatching() throws Exception {
        // Create 250 documents (will require multiple batches with batch size 100)
        List<JVS> testDocs = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            testDocs.add(new JVS().set("id", i).set("value", "doc" + i));
        }
        
        // Create iterator and sink with batch size 100
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        JVSMongoSink sink = client.createSink(TEST_COLLECTION, 100);
        
        int count = iter.sink(sink);
        
        assertEquals(250, count);
        assertEquals(250, sink.getTotalInserted());
        
        // Verify all documents were inserted
        Long docCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(250L, docCount);
    }
    
    @Test
    @DisplayName("Should handle empty iterator")
    void testEmptyIterator() throws Exception {
        List<JVS> testDocs = new ArrayList<>();
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        
        JVSMongoSink sink = client.createSink(TEST_COLLECTION);
        int count = iter.sink(sink);
        
        assertEquals(0, count);
        assertEquals(0, sink.getTotalInserted());
        
        Long docCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(0L, docCount);
    }
    
    @Test
    @DisplayName("Should chain iterator operations before sinking")
    void testChainedOperations() throws Exception {
        // Create test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Alice").set("age", 30).set("status", 1),
            new JVS().set("name", "Bob").set("age", 25).set("status", 0),
            new JVS().set("name", "Charlie").set("age", 35).set("status", 1),
            new JVS().set("name", "David").set("age", 28).set("status", 1),
            new JVS().set("name", "Eve").set("age", 40).set("status", 0)
        );
        
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        
        // Filter only active users (status=1) and age >= 30, then sink
        JVSMongoSink sink = client.createSink(TEST_COLLECTION);
        
        int count = iter
            .<JVS>filter(jvs -> jvs.getLong("status") == 1)
            .<JVS>filter(jvs -> jvs.getLong("age") >= 30)
            .sink(sink);
        
        assertEquals(2, count); // Alice and Charlie
        assertEquals(2, sink.getTotalInserted());
        
        // Verify only filtered documents were inserted
        Long docCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(2L, docCount);
        
        // Check the documents
        List<JVS> results = new ArrayList<>();
        client.findAllAsIterator(TEST_COLLECTION).forEach(results::add);
        
        assertTrue(results.stream().anyMatch(jvs -> "Alice".equals(jvs.getString("name"))));
        assertTrue(results.stream().anyMatch(jvs -> "Charlie".equals(jvs.getString("name"))));
        assertFalse(results.stream().anyMatch(jvs -> "Bob".equals(jvs.getString("name"))));
        assertFalse(results.stream().anyMatch(jvs -> "David".equals(jvs.getString("name"))));
    }
    
    @Test
    @DisplayName("Should sink data from MongoDB query to another collection")
    void testCopyBetweenCollections() throws Exception {
        String sourceCollection = "source_collection";
        String targetCollection = "target_collection";
        
        try {
            // Insert data into source
            List<JVS> testDocs = Arrays.asList(
                new JVS().set("category", "A").set("value", 100),
                new JVS().set("category", "B").set("value", 200),
                new JVS().set("category", "A").set("value", 150),
                new JVS().set("category", "C").set("value", 300)
            );
            client.insertMany(sourceCollection, testDocs).block(Duration.ofSeconds(5));
            
            // Query source and sink to target (only category A)
            JVS query = new JVS().set("category", "A");
            JVSMongoIterator iter = client.findAsIterator(sourceCollection, query);
            JVSMongoSink sink = client.createSink(targetCollection);
            
            int count = iter.sink(sink);
            
            assertEquals(2, count);
            assertEquals(2, sink.getTotalInserted());
            
            // Verify target has only category A documents
            Long targetCount = client.count(targetCollection).block(Duration.ofSeconds(5));
            assertEquals(2L, targetCount);
            
            List<JVS> results = new ArrayList<>();
            client.findAllAsIterator(targetCollection).forEach(results::add);
            assertTrue(results.stream().allMatch(jvs -> "A".equals(jvs.getString("category"))));
            
        } finally {
            client.dropCollection(sourceCollection).block(Duration.ofSeconds(5));
            client.dropCollection(targetCollection).block(Duration.ofSeconds(5));
        }
    }
    
    @Test
    @DisplayName("Should preserve document identity (_id)")
    void testIdentityPreservation() throws Exception {
        // Create documents without _id (MongoDB will generate them)
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Doc1"),
            new JVS().set("name", "Doc2")
        );
        
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        JVSMongoSink sink = client.createSink(TEST_COLLECTION);
        
        iter.sink(sink);
        
        // Verify documents have _id assigned
        List<JVS> results = new ArrayList<>();
        client.findAllAsIterator(TEST_COLLECTION).forEach(results::add);
        
        assertEquals(2, results.size());
        for (JVS doc : results) {
            assertNotNull(doc.get("_id"));
            System.out.println("Document _id: " + doc.get("_id"));
        }
    }
    
    @Test
    @DisplayName("Should handle transformation before sinking")
    void testTransformation() throws Exception {
        // Create source data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("firstName", "Alice").set("lastName", "Smith"),
            new JVS().set("firstName", "Bob").set("lastName", "Jones"),
            new JVS().set("firstName", "Charlie").set("lastName", "Brown")
        );
        
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        
        // Transform: add fullName field before sinking
        JVSMongoSink sink = client.createSink(TEST_COLLECTION);
        
        int count = iter.map(jvs -> {
            String fullName = jvs.getString("firstName") + " " + jvs.getString("lastName");
            return jvs.set("fullName", fullName);
        }).sink(sink);
        
        assertEquals(3, count);
        
        // Verify transformation
        List<JVS> results = new ArrayList<>();
        client.findAllAsIterator(TEST_COLLECTION).forEach(results::add);
        
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(jvs -> "Alice Smith".equals(jvs.getString("fullName"))));
        assertTrue(results.stream().anyMatch(jvs -> "Bob Jones".equals(jvs.getString("fullName"))));
        assertTrue(results.stream().anyMatch(jvs -> "Charlie Brown".equals(jvs.getString("fullName"))));
    }
    
    @Test
    @DisplayName("Should support small batch sizes")
    void testSmallBatchSize() throws Exception {
        List<JVS> testDocs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testDocs.add(new JVS().set("id", i));
        }
        
        AbstractIterator<JVS> iter = new ListIterator<>(testDocs);
        
        // Use very small batch size
        JVSMongoSink sink = client.createSink(TEST_COLLECTION, 2);
        int count = iter.sink(sink);
        
        assertEquals(10, count);
        assertEquals(10, sink.getTotalInserted());
        
        Long docCount = client.count(TEST_COLLECTION).block(Duration.ofSeconds(5));
        assertEquals(10L, docCount);
    }
}
