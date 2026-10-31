package vn.swordol.capture;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CaptureTest {
  public interface Connection {
    InputStream openInputStream() throws IOException;

    DataInputStream openDataInputStream() throws IOException;

    void close() throws IOException;
  }

  public static class MemoryConnection implements Connection {
    public InputStream openInputStream() {
      return new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
    }

    public DataInputStream openDataInputStream() {
      return new DataInputStream(openInputStream());
    }

    public void close() throws IOException {
      throw new IOException("original failure");
    }
  }

  @Test
  public void recordsSkippedBytesAndPreservesInterfacesAndExceptions() throws Exception {
    Path root = Files.createTempDirectory("capture-test");
    Capture.configure(root, 64);
    Connection wrapped =
        (Connection)
            Capture.wrap(new MemoryConnection(), "http://user:secret@localhost/a?token=secret");
    assertSame(wrapped, Capture.wrap(wrapped, "http://localhost"));
    try (DataInputStream input = wrapped.openDataInputStream()) {
      assertEquals(1, input.read());
      assertEquals(2, input.skip(2));
      assertEquals(4, input.read());
      assertEquals(-1, input.read());
    }
    assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(root.resolve("000001-http.bin")));
    String json =
        new String(Files.readAllBytes(root.resolve("000001-http.json")), StandardCharsets.UTF_8);
    assertFalse(json.contains("secret"));
    JsonObject meta = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(
        "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a",
        meta.get("sha256").getAsString());
    assertEquals("eof", meta.get("completion").getAsString());
    try {
      wrapped.close();
      fail("Expected original IOException");
    } catch (IOException expected) {
      assertEquals("original failure", expected.getMessage());
    }
  }

  @Test
  public void truncatesOnlyRecordingAndKeepsReadingNetwork() throws Exception {
    Path root = Files.createTempDirectory("capture-limit");
    Capture.configure(root, 2);
    Connection wrapped =
        (Connection) Capture.wrap(new MemoryConnection(), "socket://localhost:7777");
    try (InputStream input = wrapped.openInputStream()) {
      byte[] bytes = new byte[4];
      assertEquals(4, input.read(bytes));
      assertArrayEquals(new byte[] {1, 2, 3, 4}, bytes);
    }
    assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(root.resolve("000001-tcp.bin")));
    JsonObject meta =
        JsonParser.parseString(
                new String(
                    Files.readAllBytes(root.resolve("000001-tcp.json")), StandardCharsets.UTF_8))
            .getAsJsonObject();
    assertTrue(meta.get("truncated").getAsBoolean());
    assertEquals(4, meta.get("observedBytes").getAsInt());
  }
}
