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

import tools.jackson.databind.*; import tools.jackson.databind.node.*;
import com.hitorro.mongo.codec.*; import com.hitorro.mongo.query.JVSQueryBuilder; import com.hitorro.jsontypesystem.JVS;
import org.bson.*; import org.bson.codecs.*; import org.bson.conversions.Bson; import org.bson.json.*; import org.bson.types.*;
import java.io.*; import java.nio.file.*; import java.util.*;
/**
 * Pins what hitorro-jsonts-mongo stores in and sends to MongoDB to what 3.0.2 did on Jackson 2.18:
 * the BSON JVSCodec encodes (Int32/Int64/Double, _id mapping and ObjectId conversion, nested
 * documents and arrays; BigInteger/BigDecimal nodes become null, as in 3.x) for each codec config,
 * decoding every BSON type into JVS, and the filters and updates JVSQueryBuilder builds (3.x
 * quirks such as nested objects turning into arrays are pinned, not fixed). Needs no MongoDB.
 * {@code jackson2-mongo.txt} is the output of the same code run on the 3.0.2 sources
 * (generator: ~/hitorro-j3/tools/Jackson2JsontsMongoGolden.java.txt in the migration workspace).
 * {@link #decodesBsonEncodedBy3x} decodes the BSON 3.0.2 encoded with the 4.0.0 codec.
 * Jackson 3 node and exception names are mapped back before comparing (StringNode = TextNode).
 */
class Jackson2FormatGoldenTest {
  static final Map<String, String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static boolean jackson(Throwable t){ return t.getClass().getName().startsWith("com.fasterxml.jackson") || t.getClass().getName().startsWith("tools.jackson"); }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { return String.valueOf(c.call()); } catch(Throwable t){ return "!"+(t instanceof IOException ? "IOE:" : "RTE:")+(jackson(t) ? "jackson" : t.getClass().getSimpleName()); } }
  static String types(JsonNode n){ StringBuilder t=new StringBuilder(); if (n == null) return "null"; if (n.isArray()) { t.append('['); for (JsonNode e : n) t.append(types(e)).append(','); return t.append(']').toString(); }
    if (!n.isObject()) return n.getClass().getSimpleName(); t.append('{'); Iterator<String> it=n.propertyNames().iterator(); while(it.hasNext()){String k=it.next(); t.append(k+":"+types(n.get(k))+",");} return t.append('}').toString(); }
  static final JsonWriterSettings REL = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
  public static final Map<String, JVSCodecConfig> CONFIGS = new LinkedHashMap<>();
  static { CONFIGS.put("default", JVSCodecConfig.defaultConfig()); CONFIGS.put("jvs", JVSCodecConfig.jvsStandardConfig());
    CONFIGS.put("all", JVSCodecConfig.builder().mapIdField(true).jvsIdField("key").convertToObjectId(true).preserveObjectIdAsString(false).preserveDateAsTimestamp(false).preserveBinaryAsBase64(false).detectBsonTypes(true).build());
    CONFIGS.put("none", JVSCodecConfig.builder().mapIdField(false).convertToObjectId(false).preserveObjectIdAsString(true).preserveDateAsTimestamp(true).preserveBinaryAsBase64(true).detectBsonTypes(false).build()); }
  static final String[] DOCS = {
    "{\"id\":{\"id\":\"news:1\",\"did\":\"1\",\"domain\":\"news\"},\"title\":\"T \u00e9/\u2028\",\"i\":12,\"l\":12345678901,\"d\":1.10,\"neg0\":-0.0,\"exp\":1e30,\"big\":12345678901234567890,\"dec\":123456789012345678901234567890.5,\"b\":true,\"n\":null,\"a\":[1,\"x\",null,{\"k\":[]}],\"o\":{\"_id\":\"inner\",\"z\":1}}",
    "{\"_id\":\"5f43a1b2c3d4e5f6a7b8c9d0\",\"key\":\"5f43a1b2c3d4e5f6a7b8c9d1\",\"hex\":\"5f43a1b2c3d4e5f6a7b8c9d2\",\"nothex\":\"5f43a1b2c3d4e5f6a7b8c9dZ\"}",
    "{\"_id\":7,\"key\":{\"x\":1},\"id\":{\"id\":42}}", "{\"_id\":null,\"key\":null,\"id\":null}", "{}", "{\"id\":\"plain\",\"key\":[1,2]}"};
  static JVS jvs(ObjectMapper om, String s) throws Exception { return new JVS(om.readTree(s)); }
  static String encode(JVSCodec c, JVS j){ BsonDocument d = new BsonDocument(); c.encode(new BsonDocumentWriter(d), j, EncoderContext.builder().build()); return d.toJson(REL); }
  static String decode(JVSCodec c, BsonDocument d){ JVS j = c.decode(new BsonDocumentReader(d), DecoderContext.builder().build()); return j.getJsonNode() + " " + types(j.getJsonNode()); }
  public static Map<String, String> compute(Path dir) throws Exception {
    // JVSCodec writes dates with Date.toString() (preserveDateAsTimestamp=false): fix the zone.
    TimeZone tz = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try { return computeUtc(dir); } finally { TimeZone.setDefault(tz); }
  }
  static Map<String, String> computeUtc(Path dir) throws Exception {
    out.clear();
    ObjectMapper om = com.hitorro.util.json.JsonMappers.json();
    BsonDocument rich = new BsonDocument().append("_id", new BsonObjectId(new ObjectId("5f43a1b2c3d4e5f6a7b8c9d0"))).append("i32", new BsonInt32(-5)).append("i64", new BsonInt64(1L << 40))
      .append("dbl", new BsonDouble(2.5)).append("dblInt", new BsonDouble(3.0)).append("nan", new BsonDouble(Double.NaN)).append("str", new BsonString("s \u00e9")).append("t", BsonBoolean.TRUE).append("nul", BsonNull.VALUE)
      .append("date", new BsonDateTime(1700000000123L)).append("bin", new BsonBinary(new byte[]{0, 1, (byte)255})).append("dec", new BsonDecimal128(Decimal128.parse("1234567890.123456789")))
      .append("decInt", new BsonDecimal128(Decimal128.parse("10"))).append("ts", new BsonTimestamp(1700000000, 3)).append("oid", new BsonObjectId(new ObjectId("5f43a1b2c3d4e5f6a7b8c9d1")))
      .append("arr", new BsonArray(Arrays.asList(new BsonInt32(1), new BsonString("x"), new BsonDocument("k", new BsonDateTime(5))))).append("doc", new BsonDocument("a", new BsonDocument("b", new BsonInt64(2))))
      .append("regex", new BsonRegularExpression("^a", "i")).append("sym", new BsonSymbol("sym")).append("minKey", new BsonMinKey()).append("undef", new BsonUndefined()).append("js", new BsonJavaScript("x"));
    for (Map.Entry<String, JVSCodecConfig> cfg : CONFIGS.entrySet()) {
      JVSCodec c = new JVSCodec(cfg.getValue());
      for (int i = 0; i < DOCS.length; i++) { final int k = i;
        p("encode." + cfg.getKey() + "." + i, tryIt(() -> encode(c, jvs(om, DOCS[k]))));
        p("roundtrip." + cfg.getKey() + "." + i, tryIt(() -> decode(c, BsonDocument.parse(encode(c, jvs(om, DOCS[k]))))));
      }
      p("encode." + cfg.getKey() + ".arrayRoot", tryIt(() -> encode(c, jvs(om, "[1]"))));
      p("decode." + cfg.getKey() + ".rich", tryIt(() -> decode(c, rich)));
      p("decode." + cfg.getKey() + ".idDoc", tryIt(() -> decode(c, new BsonDocument("_id", new BsonDocument("a", new BsonInt32(1))).append("key", new BsonString("k")))));
    }
    // queries and updates sent to MongoDB
    String[] queries = {"{}", "{\"a\":1}", "{\"a\":\"x\",\"b\":true,\"c\":null,\"d\":1.5,\"e\":12345678901}", "{\"a\":{\"$gt\":5,\"$lte\":10.5}}", "{\"a\":{\"$in\":[1,\"x\",true]},\"b\":{\"$nin\":[]}}",
      "{\"a\":{\"$regex\":\"^x\"},\"b\":{\"$exists\":true},\"c\":{\"$type\":\"string\"},\"d\":{\"$ne\":\"q\"}}", "{\"$or\":[{\"a\":1},{\"b\":{\"$lt\":2}}],\"$and\":[{\"c\":3}]}", "{\"a\":{\"x\":1,\"y\":[1]}}",
      "{\"a\":{\"$size\":2},\"b\":{\"$all\":[1,2]},\"c\":{\"$elemMatch\":{\"q\":1}}}", "{\"a\":{\"$mod\":[3,1]}}", "{\"a\":{\"$exists\":\"yes\"},\"b\":{\"$size\":\"2\"},\"c\":{\"$regex\":5}}",
      "{\"a\":[1,2],\"b\":{\"$unknown\":1}}", "{\"a\":12345678901234567890,\"b\":1e400}", "{\"$nor\":[{\"a\":1}]}", "{\"a\":{\"$not\":{\"$gt\":1}}}"};
    for (String q : queries) p("query." + q, tryIt(() -> { Bson b = JVSQueryBuilder.fromJVS(jvs(om, q)); return b == null ? "null" : b.toBsonDocument(BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson(REL); }));
    String[] updates = {"{\"$set\":{\"a\":1,\"b\":\"x\",\"c\":{\"d\":[1]}}}", "{\"$inc\":{\"n\":2,\"m\":1.5}}", "{\"$unset\":{\"a\":\"\"},\"$push\":{\"l\":5}}", "{\"a\":1}", "{\"$set\":{\"n\":null,\"big\":12345678901}}",
      "{\"$addToSet\":{\"s\":\"v\"},\"$pull\":{\"p\":1},\"$rename\":{\"o\":\"n\"},\"$mul\":{\"m\":2},\"$min\":{\"x\":1},\"$max\":{\"y\":2},\"$currentDate\":{\"t\":true}}"};
    for (String u : updates) p("update." + u, tryIt(() -> { List<String> l = new ArrayList<>(); for (Bson b : JVSQueryBuilder.toUpdateBson(jvs(om, u))) l.add(b.toBsonDocument(BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson(REL)); return l; }));
    return out;
  }

  static Map<String, String> load(String name) throws IOException {
    Map<String, String> m = new LinkedHashMap<>();
    try (InputStream in = Jackson2FormatGoldenTest.class.getResourceAsStream(name)) {
      if (in == null) return m;
      for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        if (line.isEmpty()) continue;
        int i = line.indexOf('=');
        m.put(line.substring(0, i), line.substring(i + 1));
      }
    }
    return m;
  }

  @org.junit.jupiter.api.Test
  void matchesJackson2Output(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Map<String, String> expected = load("jackson2-mongo.txt");
    
    Map<String, String> actual = new LinkedHashMap<>();
    compute(dir).forEach((k, v) -> actual.put(k, v.replace("StringNode", "TextNode")));
    org.assertj.core.api.Assertions.assertThat(expected).hasSize(81);
    org.assertj.core.api.Assertions.assertThat(actual).containsExactlyEntriesOf(expected);
  }

  /** The BSON 3.0.2 encoded (encode.*), decoded by the 4.0.0 codec, gives what 3.0.2 decoded (roundtrip.*). */
  @org.junit.jupiter.api.Test
  void decodesBsonEncodedBy3x() throws Exception {
    Map<String, String> expected = load("jackson2-mongo.txt");
    int n = 0;
    for (Map.Entry<String, JVSCodecConfig> cfg : CONFIGS.entrySet()) {
      JVSCodec c = new JVSCodec(cfg.getValue());
      for (int i = 0; i < DOCS.length; i++) {
        String bson = expected.get("encode." + cfg.getKey() + "." + i);
        String json = bson.replace("\\\\", "\\");
        org.assertj.core.api.Assertions.assertThat(decode(c, BsonDocument.parse(json)).replace("StringNode", "TextNode").replace("\\", "\\\\"))
            .as(cfg.getKey() + "." + i).isEqualTo(expected.get("roundtrip." + cfg.getKey() + "." + i));
        n++;
      }
    }
    org.assertj.core.api.Assertions.assertThat(n).isEqualTo(CONFIGS.size() * DOCS.length);
  }
}
