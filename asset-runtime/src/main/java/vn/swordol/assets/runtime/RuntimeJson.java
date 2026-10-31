package vn.swordol.assets.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Strict, bounded JSON reading shared by the bundle and map readers. Rejects invalid UTF-8. */
final class RuntimeJson {
  static final int MAX_DEPTH = 64;

  private RuntimeJson() {}

  static JsonElement parse(byte[] bytes, String what) throws IOException {
    try (JsonReader reader =
        new JsonReader(
            new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
      reader.setStrictness(Strictness.STRICT);
      JsonElement value = read(reader, 0, what);
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IOException("Trailing data in " + what);
      }
      return value;
    } catch (IllegalStateException | NumberFormatException ex) {
      throw new IOException("Invalid " + what + ": " + ex.getMessage(), ex);
    }
  }

  // Gson's general tree parser has unbounded nesting. Bound before descending, including unknown
  // fields.
  private static JsonElement read(JsonReader reader, int depth, String what) throws IOException {
    if (depth > MAX_DEPTH) {
      throw new IOException(what + " exceeds nesting depth " + MAX_DEPTH);
    }
    switch (reader.peek()) {
      case BEGIN_OBJECT:
        JsonObject object = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if (object.has(name)) {
            throw new IOException("Duplicate field in " + what + ": " + name);
          }
          object.add(name, read(reader, depth + 1, what));
        }
        reader.endObject();
        return object;
      case BEGIN_ARRAY:
        JsonArray array = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
          array.add(read(reader, depth + 1, what));
        }
        reader.endArray();
        return array;
      case STRING:
        return new JsonPrimitive(reader.nextString());
      case NUMBER:
        return JsonParser.parseString(reader.nextString());
      case BOOLEAN:
        return new JsonPrimitive(reader.nextBoolean());
      case NULL:
        reader.nextNull();
        return JsonNull.INSTANCE;
      default:
        throw new IOException("Invalid JSON value at " + reader.getPath());
    }
  }

  static JsonObject object(JsonElement value, String context) throws IOException {
    if (value == null || !value.isJsonObject()) {
      throw new IOException("Expected object: " + context);
    }
    return value.getAsJsonObject();
  }

  static JsonArray array(JsonElement value, String context) throws IOException {
    if (value == null || !value.isJsonArray()) {
      throw new IOException("Expected array: " + context);
    }
    return value.getAsJsonArray();
  }

  static String string(JsonElement value, String context) throws IOException {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IOException("Expected string: " + context);
    }
    return value.getAsString();
  }

  /** Accepts only canonical nonnegative integer literals, so "1e3" or "01" are errors. */
  static long integer(JsonElement value, String context) throws IOException {
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isNumber()
        || !value.getAsString().matches("0|[1-9][0-9]*")) {
      throw new IOException("Expected nonnegative integer: " + context);
    }
    try {
      return Long.parseLong(value.getAsString());
    } catch (NumberFormatException ex) {
      throw new IOException("Integer out of range: " + context, ex);
    }
  }

  /** Accepts canonical signed integer literals, for documents that store offsets. */
  static long signed(JsonElement value, String context) throws IOException {
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isNumber()
        || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {
      throw new IOException("Expected integer: " + context);
    }
    try {
      return Long.parseLong(value.getAsString());
    } catch (NumberFormatException ex) {
      throw new IOException("Integer out of range: " + context, ex);
    }
  }

  static String sha256(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      char[] hex = "0123456789abcdef".toCharArray();
      StringBuilder result = new StringBuilder(64);
      for (byte value : hash) {
        result.append(hex[(value & 255) >>> 4]).append(hex[value & 15]);
      }
      return result.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError("SHA-256 unavailable", impossible);
    }
  }
}
