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
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for JVSCodec BSON conversion
 */
public class JVSCodecTest {
    
    @Test
    public void testBasicEncodeDecode() throws Exception {
        JVSCodec codec = new JVSCodec();
        
        // Create test JVS
        JVS original = new JVS()
            .set("name", "John Doe")
            .set("age", 30)
            .set("active", true);
        
        // Encode to BSON
        BsonDocument bsonDoc = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(bsonDoc);
        codec.encode(writer, original, EncoderContext.builder().build());
        
        // Decode back to JVS
        BsonDocumentReader reader = new BsonDocumentReader(bsonDoc);
        JVS decoded = codec.decode(reader, DecoderContext.builder().build());
        
        // Verify
        assertEquals("John Doe", decoded.getString("name"));
        assertEquals(30, decoded.getLong("age"));
        assertTrue(decoded.getJsonNode().get("active").asBoolean());
    }
    
    @Test
    public void testNestedObjects() throws Exception {
        JVSCodec codec = new JVSCodec();
        
        JVS original = new JVS()
            .set("user.name", "Jane")
            .set("user.email", "jane@example.com")
            .set("tags", new String[]{"admin", "active"});
        
        BsonDocument bsonDoc = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(bsonDoc);
        codec.encode(writer, original, EncoderContext.builder().build());
        
        BsonDocumentReader reader = new BsonDocumentReader(bsonDoc);
        JVS decoded = codec.decode(reader, DecoderContext.builder().build());
        
        assertEquals("Jane", decoded.getString("user.name"));
        assertEquals("jane@example.com", decoded.getString("user.email"));
        assertNotNull(decoded.get("tags"));
    }
    
    @Test
    public void testCodecConfig() {
        JVSCodecConfig config = JVSCodecConfig.builder()
            .mapIdField(true)
            .jvsIdField("id.did")
            .preserveObjectIdAsString(true)
            .build();
        
        assertTrue(config.shouldMapIdField());
        assertEquals("id.did", config.getJvsIdField());
        assertTrue(config.shouldPreserveObjectIdAsString());
    }
    
    @Test
    public void testJvsStandardConfig() {
        JVSCodecConfig config = JVSCodecConfig.jvsStandardConfig();
        
        assertTrue(config.shouldMapIdField());
        assertEquals("id.id", config.getJvsIdField());
    }
}
