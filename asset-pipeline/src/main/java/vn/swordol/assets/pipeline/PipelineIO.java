package vn.swordol.assets.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class PipelineIO {
  static final int MANIFEST_LIMIT = 4 * 1024 * 1024;
  static final int BLOB_LIMIT = 16 * 1024 * 1024;
  static final long TOTAL_LIMIT = 64L * 1024 * 1024;

  private PipelineIO() {}

  static byte[] read(Path path, int limit) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if ((long) output.size() + count > limit) {
          throw new IOException("Input exceeds byte limit");
        }
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    }
  }

  static JsonObject parse(byte[] bytes) throws IOException {
    try (JsonReader reader =
        new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      reader.setStrictness(Strictness.STRICT);
      JsonElement result = value(reader, 0);
      if (!result.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IOException("Expected one JSON object");
      }
      return result.getAsJsonObject();
    } catch (IllegalStateException | NumberFormatException exception) {
      throw new IOException("Invalid JSON structure", exception);
    }
  }

  private static JsonElement value(JsonReader reader, int depth) throws IOException {
    if (depth > 48) {
      throw new IOException("JSON nesting limit exceeded");
    }
    switch (reader.peek()) {
      case BEGIN_OBJECT:
        JsonObject object = new JsonObject();
        Set<String> keys = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (!keys.add(key)) {
            throw new IOException("Duplicate JSON field");
          }
          object.add(key, value(reader, depth + 1));
        }
        reader.endObject();
        return object;
      case BEGIN_ARRAY:
        JsonArray array = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
          array.add(value(reader, depth + 1));
        }
        reader.endArray();
        return array;
      case STRING:
        return new JsonPrimitive(reader.nextString());
      case NUMBER:
        // Keep the literal as written: normalising it here would hide numbers such as 2.0 or -0
        // that the runtime reader refuses, turning a producer error into a consumer failure.
        return com.google.gson.JsonParser.parseString(reader.nextString());
      case BOOLEAN:
        return new JsonPrimitive(reader.nextBoolean());
      case NULL:
        reader.nextNull();
        return com.google.gson.JsonNull.INSTANCE;
      default:
        throw new IOException("Invalid JSON value");
    }
  }

  static String string(JsonObject object, String key) throws IOException {
    JsonElement value = object.get(key);
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isString()
        || value.getAsString().isEmpty()) {
      throw new IOException("Expected nonempty string field: " + key);
    }
    return value.getAsString();
  }

  static long integer(JsonObject object, String key) throws IOException {
    JsonElement value = object.get(key);
    try {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
        throw new IOException("Expected integer field: " + key);
      }
      return value.getAsBigDecimal().longValueExact();
    } catch (ArithmeticException exception) {
      throw new IOException("Invalid integer field: " + key, exception);
    }
  }

  static Path safe(Path root, String relative) throws IOException {
    if (relative.isEmpty()
        || relative.contains("\\")
        || relative.startsWith("/")
        || relative.contains(":")
        || relative.indexOf('\0') >= 0) {
      throw new IOException("Invalid relative path");
    }
    Path path = root;
    for (String part : relative.split("/", -1)) {
      if (part.isEmpty()
          || part.equals(".")
          || part.equals("..")
          || part.endsWith(".")
          || part.endsWith(" ")) {
        throw new IOException("Invalid relative path component");
      }
      path = path.resolve(part);
      if (Files.isSymbolicLink(path)) {
        throw new IOException("Symbolic links are not accepted");
      }
    }
    if (!path.normalize().startsWith(root)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || !path.toRealPath().startsWith(root.toRealPath())) {
      throw new IOException("Source is not a regular project file");
    }
    return path;
  }

  static String hash(byte[] bytes) {
    try {
      StringBuilder result = new StringBuilder();
      for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  static JsonElement canonical(JsonElement element) {
    if (element.isJsonObject()) {
      JsonObject result = new JsonObject();
      Map<String, JsonElement> entries = new TreeMap<>();
      for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
        entries.put(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
        result.add(entry.getKey(), canonical(entry.getValue()));
      }
      return result;
    }
    if (element.isJsonArray()) {
      JsonArray result = new JsonArray();
      for (JsonElement child : element.getAsJsonArray()) {
        result.add(canonical(child));
      }
      return result;
    }
    return element.deepCopy();
  }

  static byte[] json(JsonElement object) {
    return new Gson().toJson(canonical(object)).getBytes(StandardCharsets.UTF_8);
  }
}
