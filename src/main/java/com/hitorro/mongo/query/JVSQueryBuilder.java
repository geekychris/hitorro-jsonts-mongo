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
package com.hitorro.mongo.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.jsontypesystem.JVS;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Utility for converting JVS query objects to MongoDB BSON filters.
 * Supports both simple equality queries and MongoDB query operators.
 * 
 * Simple queries:
 *   JVS query = new JVS().set("name", "John").set("age", 30);
 *   // Becomes: {name: "John", age: 30}
 * 
 * Operator queries:
 *   JVS query = new JVS().set("age.$gte", 18).set("age.$lt", 65);
 *   // Becomes: {age: {$gte: 18, $lt: 65}}
 */
public class JVSQueryBuilder {
    
    /**
     * Convert JVS object to MongoDB BSON filter
     */
    public static Bson fromJVS(JVS query) {
        if (query == null) {
            return Filters.empty();
        }
        
        JsonNode node = query.getJsonNode();
        if (!node.isObject()) {
            throw new IllegalArgumentException("Query must be a JVS object");
        }
        
        return buildFilter((ObjectNode) node);
    }
    
    /**
     * Build BSON filter from ObjectNode
     */
    private static Bson buildFilter(ObjectNode queryNode) {
        List<Bson> filters = new ArrayList<>();
        
        Iterator<Map.Entry<String, JsonNode>> fields = queryNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            // Handle MongoDB operators
            if (key.startsWith("$")) {
                filters.add(handleOperator(key, value));
            } else if (key.contains(".$")) {
                // Handle field.operator syntax (e.g., "age.$gte")
                filters.add(handleFieldOperator(key, value));
            } else if (value.isObject()) {
                // Check if nested object contains operators
                ObjectNode objValue = (ObjectNode) value;
                if (hasOperators(objValue)) {
                    filters.add(buildFieldFilter(key, objValue));
                } else {
                    // Simple equality for nested object
                    filters.add(Filters.eq(key, convertJsonNode(value)));
                }
            } else {
                // Simple equality
                filters.add(Filters.eq(key, convertJsonNode(value)));
            }
        }
        
        if (filters.isEmpty()) {
            return Filters.empty();
        } else if (filters.size() == 1) {
            return filters.get(0);
        } else {
            return Filters.and(filters);
        }
    }
    
    /**
     * Check if ObjectNode contains MongoDB operators
     */
    private static boolean hasOperators(ObjectNode node) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            if (fieldNames.next().startsWith("$")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Build filter for a field with operators
     */
    private static Bson buildFieldFilter(String field, ObjectNode operators) {
        List<Bson> fieldFilters = new ArrayList<>();
        
        Iterator<Map.Entry<String, JsonNode>> ops = operators.fields();
        while (ops.hasNext()) {
            Map.Entry<String, JsonNode> entry = ops.next();
            String operator = entry.getKey();
            JsonNode value = entry.getValue();
            
            if (!operator.startsWith("$")) {
                // Not an operator, treat as nested field
                continue;
            }
            
            fieldFilters.add(applyOperator(field, operator, value));
        }
        
        if (fieldFilters.isEmpty()) {
            return Filters.empty();
        } else if (fieldFilters.size() == 1) {
            return fieldFilters.get(0);
        } else {
            return Filters.and(fieldFilters);
        }
    }
    
    /**
     * Handle field.operator syntax (e.g., "age.$gte")
     */
    private static Bson handleFieldOperator(String fieldOp, JsonNode value) {
        int dotIndex = fieldOp.indexOf(".$");
        String field = fieldOp.substring(0, dotIndex);
        String operator = fieldOp.substring(dotIndex + 1); // includes the $
        
        return applyOperator(field, operator, value);
    }
    
    /**
     * Handle top-level operators like $and, $or, $nor
     */
    private static Bson handleOperator(String operator, JsonNode value) {
        return switch (operator) {
            case "$and" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$and requires array value");
                }
                List<Bson> filters = new ArrayList<>();
                for (JsonNode elem : value) {
                    if (elem.isObject()) {
                        filters.add(buildFilter((ObjectNode) elem));
                    }
                }
                yield Filters.and(filters);
            }
            case "$or" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$or requires array value");
                }
                List<Bson> filters = new ArrayList<>();
                for (JsonNode elem : value) {
                    if (elem.isObject()) {
                        filters.add(buildFilter((ObjectNode) elem));
                    }
                }
                yield Filters.or(filters);
            }
            case "$nor" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$nor requires array value");
                }
                List<Bson> filters = new ArrayList<>();
                for (JsonNode elem : value) {
                    if (elem.isObject()) {
                        filters.add(buildFilter((ObjectNode) elem));
                    }
                }
                yield Filters.nor(filters);
            }
            default -> Filters.empty();
        };
    }
    
    /**
     * Apply MongoDB operator to field
     */
    private static Bson applyOperator(String field, String operator, JsonNode value) {
        return switch (operator) {
            case "$eq" -> Filters.eq(field, convertJsonNode(value));
            case "$ne" -> Filters.ne(field, convertJsonNode(value));
            case "$gt" -> Filters.gt(field, convertJsonNode(value));
            case "$gte" -> Filters.gte(field, convertJsonNode(value));
            case "$lt" -> Filters.lt(field, convertJsonNode(value));
            case "$lte" -> Filters.lte(field, convertJsonNode(value));
            case "$in" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$in requires array value");
                }
                List<Object> values = new ArrayList<>();
                for (JsonNode elem : value) {
                    values.add(convertJsonNode(elem));
                }
                yield Filters.in(field, values);
            }
            case "$nin" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$nin requires array value");
                }
                List<Object> values = new ArrayList<>();
                for (JsonNode elem : value) {
                    values.add(convertJsonNode(elem));
                }
                yield Filters.nin(field, values);
            }
            case "$exists" -> Filters.exists(field, value.asBoolean());
            case "$regex" -> Filters.regex(field, value.asText());
            case "$type" -> Filters.type(field, value.asText());
            case "$all" -> {
                if (!value.isArray()) {
                    throw new IllegalArgumentException("$all requires array value");
                }
                List<Object> values = new ArrayList<>();
                for (JsonNode elem : value) {
                    values.add(convertJsonNode(elem));
                }
                yield Filters.all(field, values);
            }
            case "$size" -> Filters.size(field, value.asInt());
            default -> Filters.empty();
        };
    }
    
    /**
     * Convert JsonNode to appropriate Java object for MongoDB
     */
    private static Object convertJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode elem : node) {
                list.add(convertJsonNode(elem));
            }
            return list;
        }
        // For complex objects, return as-is (codec will handle)
        return node;
    }
    
    /**
     * Convert JVS update object to MongoDB update operations
     */
    public static List<Bson> toUpdateBson(JVS update) {
        List<Bson> updates = new ArrayList<>();
        
        JsonNode node = update.getJsonNode();
        if (!node.isObject()) {
            throw new IllegalArgumentException("Update must be a JVS object");
        }
        
        ObjectNode updateNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> fields = updateNode.fields();
        
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            if (key.startsWith("$set.")) {
                updates.add(Updates.set(key.substring(5), convertJsonNode(value)));
            } else if (key.startsWith("$inc.")) {
                updates.add(Updates.inc(key.substring(5), value.asInt()));
            } else if (key.startsWith("$push.")) {
                updates.add(Updates.push(key.substring(5), convertJsonNode(value)));
            } else if (key.startsWith("$pull.")) {
                updates.add(Updates.pull(key.substring(5), convertJsonNode(value)));
            } else if (key.startsWith("$unset.")) {
                updates.add(Updates.unset(key.substring(7)));
            } else {
                // Default to $set
                updates.add(Updates.set(key, convertJsonNode(value)));
            }
        }
        
        return updates;
    }
}
