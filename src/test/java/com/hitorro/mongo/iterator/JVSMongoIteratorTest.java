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
package com.hitorro.mongo.iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import com.hitorro.mongo.codec.JVSCodecConfig;
import com.mongodb.client.model.Filters;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JVSMongoIterator - iterator-based query results.
 * 
 * These tests require MongoDB to be running on localhost:27017.
 * Start MongoDB using: ./start-mongodb.sh
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JVSMongoIteratorTest {
    
    private JVSReactiveMongoClient client;
    private static final String TEST_COLLECTION = "iterator_test";
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
    @DisplayName("Should iterate over query results using AbstractIterator")
    void testBasicIteration() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Alice").set("age", 30),
            new JVS().set("name", "Bob").set("age", 25),
            new JVS().set("name", "Charlie").set("age", 35)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Query and iterate
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<JVS> results = new ArrayList<>();
        while (iter.hasNext()) {
            results.add(iter.next());
        }
        
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(jvs -> "Alice".equals(jvs.getString("name"))));
        assertTrue(results.stream().anyMatch(jvs -> "Bob".equals(jvs.getString("name"))));
        assertTrue(results.stream().anyMatch(jvs -> "Charlie".equals(jvs.getString("name"))));
    }

	@Test
	@DisplayName("Should filter documents using AbstractIterator filter method")
	void testIteratorFilteringBoring() {
		// Insert test data
		List<JVS> testDocs = Arrays.asList(
				new JVS().set("name", "Young").set("age", 20),
				new JVS().set("name", "Middle").set("age", 30),
				new JVS().set("name", "Old").set("age", 40),
				new JVS().set("name", "Elder").set("age", 50)
		);
		client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));

		// Use iterator with filter chaining
		JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);

		while(iter.hasNext()) {
			JVS jvs = iter.next();
			System.out.println();
		}
	}

    @Test
    @DisplayName("Should filter documents using AbstractIterator filter method")
    void testIteratorFiltering() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Young").set("age", 20),
            new JVS().set("name", "Middle").set("age", 30),
            new JVS().set("name", "Old").set("age", 40),
            new JVS().set("name", "Elder").set("age", 50)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Use iterator with filter chaining
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<JVS> filtered = new ArrayList<>();
        iter.<JVS>filter(jvs -> jvs.getLong("age") >= 30)
            .forEach(filtered::add);
        
        assertEquals(3, filtered.size());
        assertTrue(filtered.stream().allMatch(jvs -> jvs.getLong("age") >= 30));
    }
    
    @Test
    @DisplayName("Should map documents using AbstractIterator map method")
    void testIteratorMapping() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Alice").set("age", 30),
            new JVS().set("name", "Bob").set("age", 25),
            new JVS().set("name", "Charlie").set("age", 35)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Use iterator with map chaining
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<String> names = new ArrayList<>();
        iter.map(jvs -> jvs.getString("name"))
            .forEach(names::add);
        
        assertEquals(3, names.size());
        assertTrue(names.contains("Alice"));
        assertTrue(names.contains("Bob"));
        assertTrue(names.contains("Charlie"));
    }
    
    @Test
    @DisplayName("Should chain filter and map operations")
    void testIteratorChaining() {
        // Insert test data - use status 1 for active, 0 for inactive
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Alice").set("age", 30).set("status", 1),
            new JVS().set("name", "Bob").set("age", 25).set("status", 0),
            new JVS().set("name", "Charlie").set("age", 35).set("status", 1),
            new JVS().set("name", "David").set("age", 28).set("status", 1)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Chain filter and map
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<String> activeNames = new ArrayList<>();
        iter.<JVS>filter(jvs -> jvs.getLong("status") == 1) // status 1 = active
            .<JVS>filter(jvs -> jvs.getLong("age") >= 30)
            .map(jvs -> jvs.getString("name"))
            .forEach(activeNames::add);
        
        assertEquals(2, activeNames.size());
        assertTrue(activeNames.contains("Alice"));
        assertTrue(activeNames.contains("Charlie"));
        assertFalse(activeNames.contains("David")); // age < 30
        assertFalse(activeNames.contains("Bob")); // status 0
    }
    
    @Test
    @DisplayName("Should query with filter and return iterator")
    void testQueryWithFilter() {
        // Insert test data with scores
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("name", "Student1").set("score", 85),
            new JVS().set("name", "Student2").set("score", 92),
            new JVS().set("name", "Student3").set("score", 78),
            new JVS().set("name", "Student4").set("score", 95)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Query for high scorers using BSON filter
        JVSMongoIterator iter = client.findAsIterator(TEST_COLLECTION, 
            Filters.gte("score", 90));
        
        List<JVS> highScorers = new ArrayList<>();
        iter.forEach(highScorers::add);
        
        assertEquals(2, highScorers.size());
        assertTrue(highScorers.stream().allMatch(jvs -> jvs.getLong("score") >= 90));
    }
    
    @Test
    @DisplayName("Should query with JVS filter and return iterator")
    void testQueryWithJVSFilter() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("category", "A").set("value", 100),
            new JVS().set("category", "B").set("value", 200),
            new JVS().set("category", "A").set("value", 150),
            new JVS().set("category", "C").set("value", 300)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Query using JVS filter
        JVS query = new JVS().set("category", "A");
        JVSMongoIterator iter = client.findAsIterator(TEST_COLLECTION, query);
        
        List<JVS> categoryA = new ArrayList<>();
        iter.forEach(categoryA::add);
        
        assertEquals(2, categoryA.size());
        assertTrue(categoryA.stream().allMatch(jvs -> "A".equals(jvs.getString("category"))));
    }
    
    @Test
    @DisplayName("Should collect results to list using addAll")
    void testCollectToList() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("id", 1),
            new JVS().set("id", 2),
            new JVS().set("id", 3)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Use addAll to collect
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        List<JVS> collected = new ArrayList<>();
        iter.addAll(collected);
        
        assertEquals(3, collected.size());
    }
    
    @Test
    @DisplayName("Should handle empty results")
    void testEmptyResults() {
        // Query empty collection
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        assertFalse(iter.hasNext());
    }
    
    @Test
    @DisplayName("Should support sort operation")
    void testSort() {
        // Insert test data
        List<JVS> testDocs = Arrays.asList(
            new JVS().set("value", 30),
            new JVS().set("value", 10),
            new JVS().set("value", 20)
        );
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Sort using AbstractIterator
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<Long> sortedValues = new ArrayList<>();
        iter.sort((a, b) -> Long.compare(a.getLong("value"), b.getLong("value")))
            .map(jvs -> jvs.getLong("value"))
            .forEach(sortedValues::add);
        
        assertEquals(3, sortedValues.size());
        assertEquals(10L, sortedValues.get(0));
        assertEquals(20L, sortedValues.get(1));
        assertEquals(30L, sortedValues.get(2));
    }
    
    @Test
    @DisplayName("Should use skipNTakeM to paginate")
    void testPagination() {
        // Insert 10 documents
        List<JVS> testDocs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testDocs.add(new JVS().set("id", i));
        }
        client.insertMany(TEST_COLLECTION, testDocs).block(Duration.ofSeconds(5));
        
        // Skip 3, take 4
        JVSMongoIterator iter = client.findAllAsIterator(TEST_COLLECTION);
        
        List<JVS> page = new ArrayList<>();
        iter.sort((a, b) -> Long.compare(a.getLong("id"), b.getLong("id")))
            .skipNTakeM(3, 4, false)
            .forEach(page::add);
        
        // Should have 4 items after skipping 3 and taking 4
        assertEquals(4, page.size());
    }
}
