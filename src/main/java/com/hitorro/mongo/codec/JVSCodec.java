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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.jsontypesystem.JVS;
import org.bson.*;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * MongoDB Codec for JVS (JSON Value System) objects.
 * Provides efficient bidirectional conversion between JVS (Jackson JsonNode)
 * and MongoDB BSON format, preserving types and structure.
 * 
 * Features:
 * - Automatic _id mapping to/from JVS fields
 * - Type preservation for BSON-specific types (ObjectId, Date, Binary)
 * - Support for nested objects and arrays
 * - Configurable _id field mapping
 */
public class JVSCodec implements Codec<JVS> {
    
    private final JVSCodecConfig config;
    
    public JVSCodec() {
        this(JVSCodecConfig.defaultConfig());
    }
    
    public JVSCodec(JVSCodecConfig config) {
        this.config = config;
    }
    
    @Override
    public JVS decode(BsonReader reader, DecoderContext decoderContext) {
        BsonDocument bsonDoc = new BsonDocumentCodec().decode(reader, decoderContext);
        JsonNode jsonNode = bsonToJsonNode(bsonDoc);
        return new JVS(jsonNode);
    }
    
    @Override
    public void encode(BsonWriter writer, JVS value, EncoderContext encoderContext) {
        BsonDocument bsonDoc = jsonNodeToBson(value.getJsonNode());
        new BsonDocumentCodec().encode(writer, bsonDoc, encoderContext);
    }
    
    @Override
    public Class<JVS> getEncoderClass() {
        return JVS.class;
    }
    
    /**
     * Convert Jackson JsonNode to BSON Document
     */
    private BsonDocument jsonNodeToBson(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Root JVS must be an object/document");
        }
        
        BsonDocument doc = new BsonDocument();
        ObjectNode objNode = (ObjectNode) node;
        
        Iterator<Map.Entry<String, JsonNode>> fields = objNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            // Handle _id mapping from JVS field
            if (config.shouldMapIdField() && key.equals(config.getJvsIdField())) {
                BsonValue idValue = jsonNodeToBsonValue(value);
                if (config.shouldConvertToObjectId() && value.isTextual()) {
                    try {
                        idValue = new BsonObjectId(new ObjectId(value.asText()));
                    } catch (IllegalArgumentException e) {
                        // Not a valid ObjectId, use as string
                    }
                }
                doc.put("_id", idValue);
            } else if (!key.equals("_id")) {
                doc.put(key, jsonNodeToBsonValue(value));
            }
        }
        
        // Handle _id if present directly in JSON
        if (objNode.has("_id")) {
            doc.put("_id", jsonNodeToBsonValue(objNode.get("_id")));
        }
        
        return doc;
    }
    
    /**
     * Convert JsonNode value to appropriate BSON type
     */
    private BsonValue jsonNodeToBsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return new BsonNull();
        }
        
        if (node.isBoolean()) {
            return new BsonBoolean(node.asBoolean());
        }
        
        if (node.isInt()) {
            return new BsonInt32(node.asInt());
        }
        
        if (node.isLong()) {
            return new BsonInt64(node.asLong());
        }
        
        if (node.isDouble() || node.isFloat()) {
            return new BsonDouble(node.asDouble());
        }
        
        if (node.isTextual()) {
            String text = node.asText();
            
            // Check for special BSON type markers if configured
            if (config.shouldDetectBsonTypes()) {
                // ObjectId detection: 24 hex characters
                if (text.matches("^[0-9a-fA-F]{24}$")) {
                    try {
                        return new BsonObjectId(new ObjectId(text));
                    } catch (IllegalArgumentException e) {
                        // Not a valid ObjectId
                    }
                }
            }
            
            return new BsonString(text);
        }
        
        if (node.isArray()) {
            BsonArray array = new BsonArray();
            for (JsonNode element : node) {
                array.add(jsonNodeToBsonValue(element));
            }
            return array;
        }
        
        if (node.isObject()) {
            BsonDocument doc = new BsonDocument();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                doc.put(entry.getKey(), jsonNodeToBsonValue(entry.getValue()));
            }
            return doc;
        }
        
        return new BsonNull();
    }
    
    /**
     * Convert BSON Document to Jackson JsonNode
     */
    private JsonNode bsonToJsonNode(BsonDocument doc) {
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        
        for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
            String key = entry.getKey();
            BsonValue value = entry.getValue();
            
            // Handle _id to JVS field mapping
            if (key.equals("_id") && config.shouldMapIdField()) {
                objNode.set(config.getJvsIdField(), bsonValueToJsonNode(value));
            } else {
                objNode.set(key, bsonValueToJsonNode(value));
            }
        }
        
        return objNode;
    }
    
    /**
     * Convert BSON value to JsonNode
     */
    private JsonNode bsonValueToJsonNode(BsonValue value) {
        return switch (value.getBsonType()) {
            case NULL -> JsonNodeFactory.instance.nullNode();
            case BOOLEAN -> JsonNodeFactory.instance.booleanNode(value.asBoolean().getValue());
            case INT32 -> JsonNodeFactory.instance.numberNode(value.asInt32().getValue());
            case INT64 -> JsonNodeFactory.instance.numberNode(value.asInt64().getValue());
            case DOUBLE -> JsonNodeFactory.instance.numberNode(value.asDouble().getValue());
            case STRING -> JsonNodeFactory.instance.textNode(value.asString().getValue());
            case OBJECT_ID -> {
                ObjectId oid = value.asObjectId().getValue();
                if (config.shouldPreserveObjectIdAsString()) {
                    yield JsonNodeFactory.instance.textNode(oid.toHexString());
                } else {
                    // Store as object with type info
                    ObjectNode oidNode = JsonNodeFactory.instance.objectNode();
                    oidNode.put("$oid", oid.toHexString());
                    yield oidNode;
                }
            }
            case DATE_TIME -> {
                long timestamp = value.asDateTime().getValue();
                if (config.shouldPreserveDateAsTimestamp()) {
                    yield JsonNodeFactory.instance.numberNode(timestamp);
                } else {
                    // Store as ISO string
                    yield JsonNodeFactory.instance.textNode(new Date(timestamp).toString());
                }
            }
            case BINARY -> {
                byte[] data = value.asBinary().getData();
                if (config.shouldPreserveBinaryAsBase64()) {
                    yield JsonNodeFactory.instance.textNode(
                        java.util.Base64.getEncoder().encodeToString(data)
                    );
                } else {
                    ObjectNode binNode = JsonNodeFactory.instance.objectNode();
                    binNode.put("$binary", java.util.Base64.getEncoder().encodeToString(data));
                    yield binNode;
                }
            }
            case ARRAY -> {
                ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
                for (BsonValue element : value.asArray()) {
                    arrayNode.add(bsonValueToJsonNode(element));
                }
                yield arrayNode;
            }
            case DOCUMENT -> {
                ObjectNode docNode = JsonNodeFactory.instance.objectNode();
                BsonDocument doc = value.asDocument();
                for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
                    docNode.set(entry.getKey(), bsonValueToJsonNode(entry.getValue()));
                }
                yield docNode;
            }
            case DECIMAL128 -> JsonNodeFactory.instance.numberNode(
                value.asDecimal128().getValue().bigDecimalValue()
            );
            case TIMESTAMP -> JsonNodeFactory.instance.numberNode(
                value.asTimestamp().getTime()
            );
            default -> JsonNodeFactory.instance.nullNode();
        };
    }
}
