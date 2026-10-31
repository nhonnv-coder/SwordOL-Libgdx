package vn.swordol.capture;

import com.google.gson.GsonBuilder;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Inbound-only local recording. No request bodies, headers or credentials are logged. */
public final class Capture {
  private static Path root;
  private static long limit;
  private static final AtomicInteger NEXT = new AtomicInteger();
  private static final Set<Recording> ACTIVE =
      Collections.synchronizedSet(new LinkedHashSet<Recording>());

  private Capture() {}

  public static void configure(Path directory, long byteLimit) throws IOException {
    Files.createDirectories(directory);
    root = directory;
    limit = byteLimit;
    NEXT.set(0);
  }

  public static void finishAll() {
    Recording[] records;
    synchronized (ACTIVE) {
      records = ACTIVE.toArray(new Recording[0]);
    }
    for (Recording recording : records) {
      recording.finish("shutdown");
    }
  }

  public static Object wrap(final Object connection, final String address) {
    if (connection == null
        || root == null
        || Proxy.isProxyClass(connection.getClass())
        || !(address.startsWith("http://")
            || address.startsWith("https://")
            || address.startsWith("socket://"))) {
      return connection;
    }
    Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
    for (Class<?> type = connection.getClass(); type != null; type = type.getSuperclass()) {
      Collections.addAll(interfaces, type.getInterfaces());
    }
    final List<Recording> opened = Collections.synchronizedList(new ArrayList<Recording>());
    return Proxy.newProxyInstance(
        connection.getClass().getClassLoader(),
        interfaces.toArray(new Class<?>[0]),
        (proxy, method, args) -> {
          try {
            Object result = method.invoke(connection, args);
            if ((method.getName().equals("openInputStream")
                    || method.getName().equals("openDataInputStream"))
                && result instanceof InputStream) {
              try {
                Recording recording = new Recording((InputStream) result, address);
                opened.add(recording);
                return method.getReturnType() == DataInputStream.class
                    ? new DataInputStream(recording)
                    : recording;
              } catch (IOException error) {
                System.err.println("Capture unavailable: " + error.getClass().getSimpleName());
              }
            }
            return result;
          } catch (InvocationTargetException error) {
            throw error.getCause();
          } finally {
            if (method.getName().equals("close")) {
              synchronized (opened) {
                for (Recording recording : opened) {
                  recording.finish("connection-closed");
                }
              }
            }
          }
        });
  }

  private static String endpoint(String address) {
    try {
      URI uri = URI.create(address);
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toASCIIString();
    } catch (Exception error) {
      return "unavailable";
    }
  }

  private static final class Recording extends InputStream {
    private final InputStream source;
    private final OutputStream output;
    private final Path metadata;
    private final MessageDigest digest;
    private final Map<String, Object> info = new LinkedHashMap<String, Object>();
    private final long byteLimit = limit;
    private long observed;
    private long recorded;
    private boolean finished;

    Recording(InputStream source, String address) throws IOException {
      this.source = source;
      String mode = address.startsWith("socket:") ? "tcp" : "http";
      String stem = String.format(java.util.Locale.ROOT, "%06d-%s", NEXT.incrementAndGet(), mode);
      metadata = root.resolve(stem + ".json");
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (java.security.NoSuchAlgorithmException error) {
        throw new IllegalStateException(error);
      }
      output = Files.newOutputStream(root.resolve(stem + ".bin"), StandardOpenOption.CREATE_NEW);
      info.put("schemaVersion", 1);
      info.put("mode", mode);
      info.put("endpoint", endpoint(address));
      info.put("startedAt", Instant.now().toString());
      info.put("captureLimitBytes", byteLimit);
      info.put("direction", "server-to-client");
      info.put("completion", "open");
      writeMetadata();
      ACTIVE.add(this);
    }

    @Override
    public int read() throws IOException {
      byte[] one = new byte[1];
      return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int count;
      try {
        count = source.read(bytes, offset, length);
      } catch (IOException error) {
        finish("read-error");
        throw error;
      }
      if (count < 0) {
        finish("eof");
      } else if (count > 0) {
        record(bytes, offset, count);
      }
      return count;
    }

    private synchronized void record(byte[] bytes, int offset, int count) {
      if (finished) {
        return;
      }
      observed += count;
      int kept = (int) Math.min(count, byteLimit - recorded);
      try {
        output.write(bytes, offset, kept);
        output.flush();
        digest.update(bytes, offset, kept);
        recorded += kept;
      } catch (IOException error) {
        info.put("recordingError", error.getClass().getSimpleName());
        finish("recording-error");
        System.err.println("Capture write failed; game connection remains active.");
      }
    }

    @Override
    public long skip(long count) throws IOException {
      long skipped = 0;
      byte[] buffer = new byte[4096];
      while (skipped < count) {
        int read = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
        if (read <= 0) {
          break;
        }
        skipped += read;
      }
      return skipped;
    }

    @Override
    public int available() throws IOException {
      return source.available();
    }

    @Override
    public void close() throws IOException {
      try {
        source.close();
      } finally {
        finish("stream-closed");
      }
    }

    synchronized void finish(String reason) {
      if (finished) {
        return;
      }
      finished = true;
      try {
        output.close();
      } catch (IOException error) {
        info.put("recordingError", error.getClass().getSimpleName());
      }
      StringBuilder hex = new StringBuilder();
      for (byte value : digest.digest()) {
        hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
      }
      info.put("sha256", hex.toString());
      info.put("completion", reason);
      info.put("endedAt", Instant.now().toString());
      info.put("observedBytes", observed);
      info.put("recordedBytes", recorded);
      info.put("truncated", observed > recorded);
      try {
        writeMetadata();
      } catch (IOException error) {
        System.err.println("Capture metadata could not be finalized.");
      }
      ACTIVE.remove(this);
    }

    private void writeMetadata() throws IOException {
      Files.write(
          metadata,
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(info)
              .getBytes(StandardCharsets.UTF_8));
    }
  }
}
