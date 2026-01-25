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

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.mongo.JVSReactiveMongoClient;
import com.hitorro.util.core.iterator.sinks.BaseSink;
import com.hitorro.util.io.StoreException;
import com.mongodb.client.result.InsertManyResult;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sink implementation for writing JVS objects to MongoDB.
 * 
 * This sink allows you to drain an iterator of JVS objects directly into a MongoDB collection.
 * Documents are batched for efficiency - accumulates documents in memory and writes them in batches.
 * 
 * Identity Handling:
 * - If a document doesn't have an _id field, MongoDB will generate one automatically
 * - If a document has an _id field, it will be used as the unique identifier
 * - Duplicate _id values will cause an error (can be configured to continue on error)
 * - The codec configuration determines how _id is mapped to/from JVS fields
 * 
 * Example usage:
 * <pre>
 * JVSMongoSink sink = new JVSMongoSink(client, "users", 100);
 * sink.start();
 * 
 * // Sink from an iterator
 * someIterator.sink(sink);
 * 
 * sink.stop();
 * sink.close();
 * </pre>
 */
public class JVSMongoSink extends BaseSink<JVS> {
    
    private final JVSReactiveMongoClient client;
    private final String collectionName;
    private final int batchSize;
    private final boolean continueOnError;
    private final Duration timeout;
    
    private final List<JVS> buffer;
    private long totalInserted = 0;
    private long totalErrors = 0;
    
    /**
     * Create a MongoDB sink with default settings
     * 
     * @param client MongoDB client
     * @param collectionName Target collection name
     */
    public JVSMongoSink(JVSReactiveMongoClient client, String collectionName) {
        this(client, collectionName, 100, false, Duration.ofSeconds(30));
    }
    
    /**
     * Create a MongoDB sink with custom batch size
     * 
     * @param client MongoDB client
     * @param collectionName Target collection name
     * @param batchSize Number of documents to accumulate before writing
     */
    public JVSMongoSink(JVSReactiveMongoClient client, String collectionName, int batchSize) {
        this(client, collectionName, batchSize, false, Duration.ofSeconds(30));
    }
    
    /**
     * Create a MongoDB sink with full configuration
     * 
     * @param client MongoDB client
     * @param collectionName Target collection name
     * @param batchSize Number of documents to accumulate before writing
     * @param continueOnError If true, continue processing even if a batch fails
     * @param timeout Timeout for each batch write operation
     */
    public JVSMongoSink(JVSReactiveMongoClient client, String collectionName, int batchSize, 
                        boolean continueOnError, Duration timeout) {
        this.client = client;
        this.collectionName = collectionName;
        this.batchSize = batchSize;
        this.continueOnError = continueOnError;
        this.timeout = timeout;
        this.buffer = new ArrayList<>(batchSize);
    }
    
    @Override
    public boolean init(JsonNode map) {
        // No initialization needed from JSON config
        return true;
    }
    
    @Override
    public boolean start() throws IOException {
        buffer.clear();
        totalInserted = 0;
        totalErrors = 0;
        return true;
    }
    
    @Override
    public boolean add(JVS document) throws IOException, StoreException {
        if (document == null) {
            return false;
        }
        
        buffer.add(document);
        
        // Flush if batch is full
        if (buffer.size() >= batchSize) {
            return flush();
        }
        
        return true;
    }
    
    @Override
    public boolean stop() throws IOException {
        // Flush any remaining documents
        if (!buffer.isEmpty()) {
            try {
                flush();
            } catch (StoreException e) {
                throw new IOException("Error flushing during stop", e);
            }
        }
        return true;
    }
    
    @Override
    public void close() throws IOException {
        // Ensure everything is flushed
        stop();
    }
    
    /**
     * Flush accumulated documents to MongoDB
     */
    private boolean flush() throws IOException, StoreException {
        if (buffer.isEmpty()) {
            return true;
        }
        
        try {
            List<JVS> toInsert = new ArrayList<>(buffer);
            buffer.clear();
            
            InsertManyResult result = client.insertMany(collectionName, toInsert)
                .block(timeout);
            
            if (result != null) {
                totalInserted += toInsert.size();
                return true;
            } else {
                totalErrors += toInsert.size();
                if (!continueOnError) {
                    throw new StoreException("Failed to insert batch into MongoDB");
                }
                return false;
            }
        } catch (Exception e) {
            totalErrors += buffer.size();
            buffer.clear();
            
            if (continueOnError) {
                // Log error but continue
                System.err.println("Error inserting batch: " + e.getMessage());
                return false;
            } else {
                throw new StoreException("Error inserting into MongoDB: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get total number of documents successfully inserted
     */
    public long getTotalInserted() {
        return totalInserted;
    }
    
    /**
     * Get total number of documents that failed to insert
     */
    public long getTotalErrors() {
        return totalErrors;
    }
    
    /**
     * Get current buffer size
     */
    public int getBufferSize() {
        return buffer.size();
    }
}
