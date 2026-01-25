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

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.util.core.iterator.AbstractIterator;
import reactor.core.publisher.Flux;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Iterator implementation for MongoDB query results based on AbstractIterator.
 * Bridges reactive Flux streams to synchronous iterator pattern.
 * 
 * This iterator consumes a Flux<JVS> and provides it via the standard Iterator interface,
 * allowing use of all AbstractIterator chaining methods (map, filter, reduce, etc.).
 * 
 * Example usage:
 * <pre>
 * JVSMongoIterator iter = client.findAsIterator("users", query);
 * iter.filter(jvs -> jvs.getLong("age") > 18)
 *     .map(jvs -> jvs.getString("name"))
 *     .forEach(name -> System.out.println(name));
 * </pre>
 */
public class JVSMongoIterator extends AbstractIterator<JVS> {
    
    private static final JVS SENTINEL = new JVS(); // Sentinel to mark end of stream
    private final BlockingQueue<JVS> queue;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private JVS nextItem = null;
    private boolean nextFetched = false;
    
    /**
     * Create iterator from a Flux of JVS objects
     * 
     * @param flux Reactive flux of MongoDB query results
     */
    public JVSMongoIterator(Flux<JVS> flux) {
        this(flux, 100); // Default queue size
    }
    
    /**
     * Create iterator with custom queue size for buffering
     * 
     * @param flux Reactive flux of MongoDB query results
     * @param queueSize Size of internal buffer queue
     */
    public JVSMongoIterator(Flux<JVS> flux, int queueSize) {
        this.queue = new LinkedBlockingQueue<>(queueSize);
        
        // Subscribe to flux once and populate queue
        if (subscribed.compareAndSet(false, true)) {
            flux.subscribe(
                item -> {
                    try {
                        queue.put(item);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while queuing item", e);
                    }
                },
                throwable -> {
                    error.set(throwable);
                    completed.set(true);
                    try {
                        queue.put(SENTINEL); // Signal completion
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                () -> {
                    completed.set(true);
                    try {
                        queue.put(SENTINEL); // Signal completion
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            );
        }
    }
    
    @Override
    public boolean hasNext() {
        if (nextFetched) {
            return nextItem != SENTINEL;
        }
        
        // Check for previous error
        Throwable err = error.get();
        if (err != null) {
            throw new RuntimeException("Error during flux processing", err);
        }
        
        try {
            nextItem = queue.take();
            nextFetched = true;
            return nextItem != SENTINEL;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for next item", e);
        }
    }
    
    @Override
    public JVS next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        
        nextFetched = false;
        JVS result = nextItem;
        nextItem = null;
        return result;
    }
    
    @Override
    public void close() throws Exception {
        // Mark as completed to stop processing
        completed.set(true);
        // Clear queue
        queue.clear();
    }
    
    /**
     * Check if the flux has completed
     */
    public boolean isCompleted() {
        return completed.get();
    }
    
    /**
     * Check if there was an error during processing
     */
    public Throwable getError() {
        return error.get();
    }
}
