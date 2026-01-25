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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.codec.JVSCodecConfig;
import com.hitorro.mongo.codec.JVSCodecProvider;
import com.hitorro.mongo.iterator.JVSMongoIterator;
import com.hitorro.mongo.query.JVSQueryBuilder;
import com.hitorro.mongo.sink.JVSMongoSink;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reactive MongoDB client for JVS objects.
 * Provides high-level reactive operations for storing, querying, and managing JVS documents.
 * 
 * Features:
 * - Reactive streams API using Project Reactor
 * - Automatic JVS ↔ BSON conversion via codec
 * - Connection pooling with configurable settings
 * - Support for CRUD operations, aggregation, transactions
 * - JVS-based and native BSON query support
 */
public class JVSReactiveMongoClient implements AutoCloseable {
    
    private final MongoClient mongoClient;
    private final String databaseName;
    private final JVSCodecConfig codecConfig;
    
    private JVSReactiveMongoClient(Builder builder) {
        this.codecConfig = builder.codecConfig;
        this.databaseName = builder.databaseName;
        
        // Build codec registry with JVS support
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(new JVSCodecProvider(codecConfig))
        );
        
        // Build MongoDB client settings
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(builder.connectionString))
            .codecRegistry(codecRegistry);
        
        // Apply connection pool settings if configured
        if (builder.maxConnectionPoolSize > 0) {
            settingsBuilder.applyToConnectionPoolSettings(poolBuilder ->
                poolBuilder
                    .maxSize(builder.maxConnectionPoolSize)
                    .minSize(builder.minConnectionPoolSize)
                    .maxWaitTime(builder.maxWaitTimeMs, TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(builder.maxConnectionIdleTimeMs, TimeUnit.MILLISECONDS)
            );
        }
        
        this.mongoClient = MongoClients.create(settingsBuilder.build());
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Get a collection typed to JVS documents
     */
    public MongoCollection<JVS> getCollection(String collectionName) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return database.getCollection(collectionName, JVS.class);
    }
    
    /**
     * Insert a single JVS document
     */
    public Mono<InsertOneResult> insertOne(String collectionName, JVS document) {
        return Mono.from(getCollection(collectionName).insertOne(document));
    }
    
    /**
     * Insert multiple JVS documents
     */
    public Mono<InsertManyResult> insertMany(String collectionName, List<JVS> documents) {
        return Mono.from(getCollection(collectionName).insertMany(documents));
    }
    
    /**
     * Find documents using native BSON filter
     */
    public Flux<JVS> find(String collectionName, Bson filter) {
        return Flux.from(getCollection(collectionName).find(filter));
    }
    
    /**
     * Find documents using JVS-based query
     */
    public Flux<JVS> find(String collectionName, JVS query) {
        Bson filter = JVSQueryBuilder.fromJVS(query);
        return find(collectionName, filter);
    }
    
    /**
     * Find all documents in a collection
     */
    public Flux<JVS> findAll(String collectionName) {
        return Flux.from(getCollection(collectionName).find());
    }
    
    /**
     * Find a single document by ID
     */
    public Mono<JVS> findById(String collectionName, Object id) {
        return Mono.from(getCollection(collectionName).find(Filters.eq("_id", id)).first());
    }
    
    /**
     * Find one document matching filter
     */
    public Mono<JVS> findOne(String collectionName, Bson filter) {
        return Mono.from(getCollection(collectionName).find(filter).first());
    }
    
    /**
     * Find one document using JVS query
     */
    public Mono<JVS> findOne(String collectionName, JVS query) {
        Bson filter = JVSQueryBuilder.fromJVS(query);
        return findOne(collectionName, filter);
    }
    
    /**
     * Count documents matching filter
     */
    public Mono<Long> count(String collectionName, Bson filter) {
        return Mono.from(getCollection(collectionName).countDocuments(filter));
    }
    
    /**
     * Count all documents
     */
    public Mono<Long> count(String collectionName) {
        return Mono.from(getCollection(collectionName).countDocuments());
    }
    
    /**
     * Update a single document
     */
    public Mono<UpdateResult> updateOne(String collectionName, Bson filter, Bson update) {
        return Mono.from(getCollection(collectionName).updateOne(filter, update));
    }
    
    /**
     * Update a single document using JVS
     */
    public Mono<UpdateResult> updateOne(String collectionName, JVS filter, JVS update) {
        Bson filterBson = JVSQueryBuilder.fromJVS(filter);
        Bson updateBson = Updates.combine(JVSQueryBuilder.toUpdateBson(update));
        return updateOne(collectionName, filterBson, updateBson);
    }
    
    /**
     * Update multiple documents
     */
    public Mono<UpdateResult> updateMany(String collectionName, Bson filter, Bson update) {
        return Mono.from(getCollection(collectionName).updateMany(filter, update));
    }
    
    /**
     * Replace a document
     */
    public Mono<UpdateResult> replaceOne(String collectionName, Bson filter, JVS replacement) {
        return Mono.from(getCollection(collectionName).replaceOne(filter, replacement));
    }
    
    /**
     * Replace a document by ID
     */
    public Mono<UpdateResult> replaceById(String collectionName, Object id, JVS replacement) {
        return replaceOne(collectionName, Filters.eq("_id", id), replacement);
    }
    
    /**
     * Save a document - replaces if _id exists, inserts if not.
     * This is the recommended method for the create-retrieve-modify-save workflow.
     * 
     * If the document has an _id field:
     * - Attempts to replace the existing document with that _id
     * - If no document with that _id exists, inserts it as a new document
     * 
     * If the document has no _id field:
     * - MongoDB generates a new _id and inserts the document
     * 
     * @param collectionName Target collection
     * @param document JVS document to save
     * @return Mono containing the update result (which may be an insert or replace)
     */
    public Mono<UpdateResult> save(String collectionName, JVS document) {
        // Extract _id from the document if it exists
        Object id = extractId(document);
        
        if (id != null) {
            // Document has an _id - remove it before replacing
            // (MongoDB's _id is immutable and shouldn't be in the replacement)
            JVS documentWithoutId = removeIdField(document);
            
            // Use replaceOne with upsert=true
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            return Mono.from(getCollection(collectionName).replaceOne(
                Filters.eq("_id", id), 
                documentWithoutId, 
                options
            ));
        } else {
            // No _id - just insert (MongoDB will generate one)
            return insertOne(collectionName, document)
                .map(insertResult -> {
                    // Convert InsertOneResult to UpdateResult for consistency
                    // Upserted ID will be available in the response
                    return UpdateResult.acknowledged(0, 0L, insertResult.getInsertedId());
                });
        }
    }
    
    /**
     * Save multiple documents using upsert logic.
     * Each document with an _id will replace the existing document or insert if not found.
     * Documents without _id will be inserted with generated IDs.
     * 
     * Note: This processes documents individually rather than as a true bulk operation
     * to support the mixed insert/replace logic.
     * 
     * @param collectionName Target collection
     * @param documents List of JVS documents to save
     * @return Flux of update results
     */
    public Flux<UpdateResult> saveMany(String collectionName, List<JVS> documents) {
        return Flux.fromIterable(documents)
            .flatMap(document -> save(collectionName, document));
    }
    
    /**
     * Upsert a document using a custom filter.
     * Updates the document if it matches the filter, inserts if no match found.
     * 
     * @param collectionName Target collection
     * @param filter BSON filter to find existing document
     * @param document Document to upsert (can be update operations or full replacement)
     * @param useReplace If true, uses replaceOne; if false, uses updateOne
     * @return Mono containing the update result
     */
    public Mono<UpdateResult> upsert(String collectionName, Bson filter, JVS document, boolean useReplace) {
        if (useReplace) {
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            return Mono.from(getCollection(collectionName).replaceOne(filter, document, options));
        } else {
            UpdateOptions options = new UpdateOptions().upsert(true);
            Bson update = Updates.combine(JVSQueryBuilder.toUpdateBson(document));
            return Mono.from(getCollection(collectionName).updateOne(filter, update, options));
        }
    }
    
    /**
     * Upsert using JVS-based filter (convenience method).
     * 
     * @param collectionName Target collection
     * @param filter JVS filter object
     * @param document Document to upsert
     * @return Mono containing the update result
     */
    public Mono<UpdateResult> upsert(String collectionName, JVS filter, JVS document) {
        Bson filterBson = JVSQueryBuilder.fromJVS(filter);
        return upsert(collectionName, filterBson, document, true);
    }
    
    /**
     * Extract _id from a JVS document.
     * Returns null if no _id field exists.
     * Properly handles ObjectId values stored as {$oid: "..."} format.
     */
    private Object extractId(JVS document) {
        if (document == null) {
            return null;
        }
        
        // Check if codec config maps a JVS field to _id
        if (codecConfig.shouldMapIdField()) {
            String idFieldPath = codecConfig.getJvsIdField();
            // Navigate the field path (e.g., "id.id")
            String[] parts = idFieldPath.split("\\.");
            JsonNode current = document.getJsonNode();
            for (String part : parts) {
                if (current == null || !current.has(part)) {
                    return null;
                }
                current = current.get(part);
            }
            return current != null && !current.isNull() ? parseIdValue(current) : null;
        } else {
            // Check for direct _id field
            JsonNode idNode = document.getJsonNode().get("_id");
            if (idNode != null && !idNode.isNull()) {
                return parseIdValue(idNode);
            }
        }
        
        return null;
    }
    
    /**
     * Parse an ID value from a JsonNode, handling various formats:
     * - String values
     * - ObjectId format: {$oid: "..."}
     * - Plain objects
     */
    private Object parseIdValue(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        
        if (idNode.isTextual()) {
            String textValue = idNode.asText();
            // Try to parse as ObjectId if it looks like one (24 hex chars)
            if (textValue.matches("[0-9a-fA-F]{24}")) {
                try {
                    return new org.bson.types.ObjectId(textValue);
                } catch (Exception e) {
                    return textValue;
                }
            }
            return textValue;
        } else if (idNode.isObject() && idNode.has("$oid")) {
            // Extended JSON format for ObjectId
            String oidValue = idNode.get("$oid").asText();
            try {
                return new org.bson.types.ObjectId(oidValue);
            } catch (Exception e) {
                return oidValue;
            }
        } else if (idNode.isNumber()) {
            return idNode.asLong();
        }
        
        // Fallback: return as is
        return idNode;
    }
    
    /**
     * Remove the _id field from a JVS document.
     * Creates a copy of the document without the _id field.
     * MongoDB's _id field is immutable and must not be included in replace operations.
     */
    private JVS removeIdField(JVS document) {
        if (document == null) {
            return null;
        }
        
        JsonNode originalNode = document.getJsonNode();
        if (!(originalNode instanceof ObjectNode)) {
            return document; // Not an object, can't remove _id
        }
        
        // Clone the node
        ObjectNode clonedNode = ((ObjectNode) originalNode).deepCopy();
        
        // Remove the _id field (direct field, not mapped field)
        clonedNode.remove("_id");
        
        // If codec config maps a JVS field to _id, we keep that field
        // (only remove the actual MongoDB _id field)
        
        // Create new JVS from the cloned node
        return new JVS(clonedNode);
    }

    /**
     * Delete a single document
     */
    public Mono<DeleteResult> deleteOne(String collectionName, Bson filter) {
        return Mono.from(getCollection(collectionName).deleteOne(filter));
    }
    
    /**
     * Delete a document by ID
     */
    public Mono<DeleteResult> deleteById(String collectionName, Object id) {
        return deleteOne(collectionName, Filters.eq("_id", id));
    }
    
    /**
     * Delete multiple documents
     */
    public Mono<DeleteResult> deleteMany(String collectionName, Bson filter) {
        return Mono.from(getCollection(collectionName).deleteMany(filter));
    }
    
    /**
     * Execute aggregation pipeline
     */
    public Flux<JVS> aggregate(String collectionName, List<Bson> pipeline) {
        return Flux.from(getCollection(collectionName).aggregate(pipeline));
    }
    
    /**
     * Create an index
     */
    public Mono<String> createIndex(String collectionName, Bson keys) {
        return Mono.from(getCollection(collectionName).createIndex(keys));
    }
    
    /**
     * Create an index with options
     */
    public Mono<String> createIndex(String collectionName, Bson keys, IndexOptions options) {
        return Mono.from(getCollection(collectionName).createIndex(keys, options));
    }
    
    /**
     * Drop an index
     */
    public Mono<Void> dropIndex(String collectionName, String indexName) {
        return Mono.from(getCollection(collectionName).dropIndex(indexName));
    }
    
    /**
     * Drop a collection
     */
    public Mono<Void> dropCollection(String collectionName) {
        return Mono.from(getCollection(collectionName).drop());
    }
    
    // ========== Iterator-based Query Methods ==========
    
    /**
     * Find documents and return as an AbstractIterator.
     * This allows using all AbstractIterator chaining methods (map, filter, reduce, etc.)
     * 
     * @param collectionName Collection to query
     * @param filter BSON filter
     * @return Iterator of JVS documents
     */
    public JVSMongoIterator findAsIterator(String collectionName, Bson filter) {
        Flux<JVS> flux = find(collectionName, filter);
        return new JVSMongoIterator(flux);
    }
    
    /**
     * Find documents using JVS query and return as an AbstractIterator
     * 
     * @param collectionName Collection to query
     * @param query JVS query object
     * @return Iterator of JVS documents
     */
    public JVSMongoIterator findAsIterator(String collectionName, JVS query) {
        Flux<JVS> flux = find(collectionName, query);
        return new JVSMongoIterator(flux);
    }
    
    /**
     * Find all documents and return as an AbstractIterator
     * 
     * @param collectionName Collection to query
     * @return Iterator of all JVS documents
     */
    public JVSMongoIterator findAllAsIterator(String collectionName) {
        Flux<JVS> flux = findAll(collectionName);
        return new JVSMongoIterator(flux);
    }
    
    /**
     * Find documents with custom queue size for iterator buffering
     * 
     * @param collectionName Collection to query
     * @param filter BSON filter
     * @param queueSize Size of internal buffer queue
     * @return Iterator of JVS documents
     */
    public JVSMongoIterator findAsIterator(String collectionName, Bson filter, int queueSize) {
        Flux<JVS> flux = find(collectionName, filter);
        return new JVSMongoIterator(flux, queueSize);
    }
    
    /**
     * Execute aggregation and return as an AbstractIterator
     * 
     * @param collectionName Collection to aggregate
     * @param pipeline Aggregation pipeline
     * @return Iterator of aggregation results
     */
    public JVSMongoIterator aggregateAsIterator(String collectionName, List<Bson> pipeline) {
        Flux<JVS> flux = aggregate(collectionName, pipeline);
        return new JVSMongoIterator(flux);
    }
    
    // ========== Sink Methods ==========
    
    /**
     * Create a sink for writing JVS documents to MongoDB.
     * Documents are batched for efficient bulk insertion.
     * 
     * @param collectionName Target collection
     * @return Configured sink
     */
    public JVSMongoSink createSink(String collectionName) {
        return new JVSMongoSink(this, collectionName);
    }
    
    /**
     * Create a sink with custom batch size
     * 
     * @param collectionName Target collection
     * @param batchSize Number of documents per batch
     * @return Configured sink
     */
    public JVSMongoSink createSink(String collectionName, int batchSize) {
        return new JVSMongoSink(this, collectionName, batchSize);
    }
    
    /**
     * Get the underlying reactive MongoDB client
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }
    
    /**
     * Get the database name
     */
    public String getDatabaseName() {
        return databaseName;
    }
    
    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
    
    /**
     * Builder for JVSReactiveMongoClient
     */
    public static class Builder {
        private String connectionString = "mongodb://localhost:27017";
        private String databaseName;
        private JVSCodecConfig codecConfig = JVSCodecConfig.defaultConfig();
        private int maxConnectionPoolSize = 100;
        private int minConnectionPoolSize = 0;
        private long maxWaitTimeMs = 120000; // 2 minutes
        private long maxConnectionIdleTimeMs = 0; // unlimited
        
        /**
         * Set MongoDB connection string
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }
        
        /**
         * Set database name
         */
        public Builder database(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }
        
        /**
         * Set JVS codec configuration
         */
        public Builder codecConfig(JVSCodecConfig config) {
            this.codecConfig = config;
            return this;
        }
        
        /**
         * Set maximum connection pool size
         */
        public Builder maxConnectionPoolSize(int size) {
            this.maxConnectionPoolSize = size;
            return this;
        }
        
        /**
         * Set minimum connection pool size
         */
        public Builder minConnectionPoolSize(int size) {
            this.minConnectionPoolSize = size;
            return this;
        }
        
        /**
         * Set maximum wait time for connection from pool (milliseconds)
         */
        public Builder maxWaitTime(long milliseconds) {
            this.maxWaitTimeMs = milliseconds;
            return this;
        }
        
        /**
         * Set maximum idle time for pooled connections (milliseconds)
         */
        public Builder maxConnectionIdleTime(long milliseconds) {
            this.maxConnectionIdleTimeMs = milliseconds;
            return this;
        }
        
        public JVSReactiveMongoClient build() {
            if (databaseName == null || databaseName.isEmpty()) {
                throw new IllegalArgumentException("Database name is required");
            }
            return new JVSReactiveMongoClient(this);
        }
    }
}
