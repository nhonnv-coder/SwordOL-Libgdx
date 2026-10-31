package vn.swordol.protocol;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** UTF-8 NDJSON; limit excludes LF. Partial frames are never dispatched. */
public final class FrameCodec {
  public static final int MAX_FRAME_BYTES = 16384;
  public static final Gson GSON = new GsonBuilder().serializeNulls().create();

  private FrameCodec() {}

  public static String read(InputStream input) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (; ; ) {
      int b = input.read();
      if (b == -1) {
        if (bytes.size() == 0) return null;
        throw new EOFException("Unterminated frame");
      }
      if (b == 10) break;
      if (bytes.size() >= MAX_FRAME_BYTES) throw new IOException("Frame exceeds 16 KiB");
      bytes.write(b);
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes.toByteArray()))
          .toString();
    } catch (CharacterCodingException ex) {
      throw new IOException("Invalid UTF-8", ex);
    }
  }

  public static void write(OutputStream output, String frame) throws IOException {
    byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_FRAME_BYTES || frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0)
      throw new IOException("Invalid output frame");
    output.write(bytes);
    output.write(10);
    output.flush();
  }

  public static Request decodeRequest(String frame) {
    try {
      JsonReader reader = new JsonReader(new StringReader(frame));
      reader.setStrictness(Strictness.STRICT);
      JsonElement parsed = JsonParser.parseReader(reader);
      if (reader.peek() != JsonToken.END_DOCUMENT || !parsed.isJsonObject())
        throw new IllegalArgumentException("Expected one JSON object");
      JsonObject o = parsed.getAsJsonObject();
      if (!string(o, "requestId", 128)
          || !string(o, "type", 64)
          || !o.has("payload")
          || !o.get("payload").isJsonObject())
        throw new IllegalArgumentException("requestId/type strings and payload object required");
      return new Request(
          o.get("requestId").getAsString(),
          o.get("type").getAsString(),
          o.getAsJsonObject("payload"));
    } catch (IOException | JsonParseException | IllegalStateException ex) {
      throw new IllegalArgumentException("Malformed JSON request", ex);
    }
  }

  private static boolean string(JsonObject o, String key, int max) {
    JsonElement e = o.get(key);
    return e != null
        && e.isJsonPrimitive()
        && e.getAsJsonPrimitive().isString()
        && !e.getAsString().trim().isEmpty()
        && e.getAsString().length() <= max;
  }

  public static String encode(Object value) {
    return GSON.toJson(value);
  }
}
