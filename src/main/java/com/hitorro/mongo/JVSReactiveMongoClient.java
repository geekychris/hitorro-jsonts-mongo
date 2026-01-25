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
import com.hitorro.mongo.codec.JVSCodecProvider;
import com.hitorro.mongo.query.JVSQueryBuilder;
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
