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

/**
 * Configuration for JVS-BSON codec behavior.
 * Controls how JVS objects are mapped to/from MongoDB BSON format.
 */
public class JVSCodecConfig {
    
    private final boolean mapIdField;
    private final String jvsIdField;
    private final boolean convertToObjectId;
    private final boolean preserveObjectIdAsString;
    private final boolean preserveDateAsTimestamp;
    private final boolean preserveBinaryAsBase64;
    private final boolean detectBsonTypes;
    
    private JVSCodecConfig(Builder builder) {
        this.mapIdField = builder.mapIdField;
        this.jvsIdField = builder.jvsIdField;
        this.convertToObjectId = builder.convertToObjectId;
        this.preserveObjectIdAsString = builder.preserveObjectIdAsString;
        this.preserveDateAsTimestamp = builder.preserveDateAsTimestamp;
        this.preserveBinaryAsBase64 = builder.preserveBinaryAsBase64;
        this.detectBsonTypes = builder.detectBsonTypes;
    }
    
    /**
     * Default configuration:
     * - No automatic _id field mapping (use _id directly)
     * - Preserve ObjectId as string for simplicity
     * - Preserve dates as timestamps
     * - Preserve binary as base64
     * - Auto-detect BSON types in strings
     */
    public static JVSCodecConfig defaultConfig() {
        return builder().build();
    }
    
    /**
     * Configuration optimized for JVS standard fields (id.id, id.did, etc.)
     */
    public static JVSCodecConfig jvsStandardConfig() {
        return builder()
                .mapIdField(true)
                .jvsIdField("id.id")
                .convertToObjectId(false)
                .build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean shouldMapIdField() {
        return mapIdField;
    }
    
    public String getJvsIdField() {
        return jvsIdField;
    }
    
    public boolean shouldConvertToObjectId() {
        return convertToObjectId;
    }
    
    public boolean shouldPreserveObjectIdAsString() {
        return preserveObjectIdAsString;
    }
    
    public boolean shouldPreserveDateAsTimestamp() {
        return preserveDateAsTimestamp;
    }
    
    public boolean shouldPreserveBinaryAsBase64() {
        return preserveBinaryAsBase64;
    }
    
    public boolean shouldDetectBsonTypes() {
        return detectBsonTypes;
    }
    
    public static class Builder {
        private boolean mapIdField = false;
        private String jvsIdField = "id.id";
        private boolean convertToObjectId = false;
        private boolean preserveObjectIdAsString = true;
        private boolean preserveDateAsTimestamp = true;
        private boolean preserveBinaryAsBase64 = true;
        private boolean detectBsonTypes = true;
        
        /**
         * Enable automatic mapping between MongoDB _id and a JVS field
         * @param enable true to enable mapping
         */
        public Builder mapIdField(boolean enable) {
            this.mapIdField = enable;
            return this;
        }
        
        /**
         * Set the JVS field path to map to/from MongoDB _id
         * @param fieldPath JVS field path (e.g., "id.id", "id.did")
         */
        public Builder jvsIdField(String fieldPath) {
            this.jvsIdField = fieldPath;
            return this;
        }
        
        /**
         * Automatically convert string IDs to ObjectId when writing
         * @param convert true to auto-convert
         */
        public Builder convertToObjectId(boolean convert) {
            this.convertToObjectId = convert;
            return this;
        }
        
        /**
         * Store ObjectId as plain string instead of {$oid: "..."} object
         * @param preserve true to use plain string
         */
        public Builder preserveObjectIdAsString(boolean preserve) {
            this.preserveObjectIdAsString = preserve;
            return this;
        }
        
        /**
         * Store BSON dates as numeric timestamps instead of string representation
         * @param preserve true to use timestamps
         */
        public Builder preserveDateAsTimestamp(boolean preserve) {
            this.preserveDateAsTimestamp = preserve;
            return this;
        }
        
        /**
         * Store BSON binary as base64 string instead of {$binary: "..."} object
         * @param preserve true to use base64
         */
        public Builder preserveBinaryAsBase64(boolean preserve) {
            this.preserveBinaryAsBase64 = preserve;
            return this;
        }
        
        /**
         * Auto-detect BSON types (ObjectId, etc.) in string values
         * @param detect true to enable detection
         */
        public Builder detectBsonTypes(boolean detect) {
            this.detectBsonTypes = detect;
            return this;
        }
        
        public JVSCodecConfig build() {
            return new JVSCodecConfig(this);
        }
    }
}
