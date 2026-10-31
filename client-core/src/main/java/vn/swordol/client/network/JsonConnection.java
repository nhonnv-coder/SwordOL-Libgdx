package vn.swordol.client.network;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Owns the socket. Blocking IO never runs on the rendering thread. */
public final class JsonConnection implements AutoCloseable {
  private final Consumer<JsonObject> responseHandler;
  private final Consumer<String> statusHandler;
  private final Consumer<Runnable> render;
  private final ScheduledExecutorService io =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "swordol-network");
        t.setDaemon(true);
        return t;
      });
  private final AtomicLong ids = new AtomicLong();
  private volatile long generation;
  private volatile Socket socket;

  public JsonConnection(
      Consumer<Runnable> render,
      Consumer<JsonObject> responseHandler,
      Consumer<String> statusHandler) {
    this.responseHandler = java.util.Objects.requireNonNull(responseHandler);
    this.statusHandler = java.util.Objects.requireNonNull(statusHandler);
    this.render = java.util.Objects.requireNonNull(render);
    io.scheduleWithFixedDelay(
        () -> {
          Socket active = socket;
          if (active != null && active.isConnected() && !active.isClosed())
            request("PING", new JsonObject());
        },
        5,
        5,
        TimeUnit.SECONDS);
  }

  public void connect(String host, int port) {
    disconnect();
    final long current = generation;
    io.execute(() -> {
      Socket next = new Socket();
      try {
        synchronized (this) {
          if (current != generation) {
            next.close();
            return;
          }
          socket = next;
        }
        next.connect(new InetSocketAddress(host, port), 3000);
        next.setSoTimeout(5000);
        if (current != generation) next.close();
      } catch (IOException e) {
        try {
          next.close();
        } catch (IOException ignored) {
        }
        report(current, "Connection failed: " + e.getMessage());
      }
    });
  }

  public void request(String type, JsonObject payload) {
    final long current = generation;
    io.execute(() -> {
      if (current != generation) return;
      try {
        Socket active = socket;
        if (active == null || !active.isConnected() || active.isClosed())
          throw new IOException("Connect first");
        String id = Long.toString(ids.incrementAndGet());
        JsonObject req = new JsonObject();
        req.addProperty("requestId", id);
        req.addProperty("type", type);
        req.add("payload", payload);
        byte[] bytes = (req.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16384) throw new IOException("Request too large");
        active.getOutputStream().write(bytes);
        active.getOutputStream().flush();
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        int b;
        while ((b = active.getInputStream().read()) != -1 && b != '\n') {
          if (frame.size() >= 16384) throw new IOException("Response too large");
          frame.write(b);
        }
        if (b == -1) throw new EOFException("Server disconnected");
        JsonObject response =
            JsonParser.parseString(new String(frame.toByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        if (!id.equals(response.get("requestId").getAsString())
            || !type.equals(response.get("type").getAsString()))
          throw new IOException("Mismatched response");
        render.accept(() -> {
          if (current == generation)
            try {
              responseHandler.accept(response);
            } catch (RuntimeException e) {
              statusHandler.accept("Invalid server response");
            }
        });
      } catch (IOException | RuntimeException e) {
        closeCurrent(current);
        report(current, "Network error: " + e.getMessage());
      }
    });
  }

  private void report(long current, String message) {
    render.accept(() -> {
      if (current == generation) statusHandler.accept(message);
    });
  }

  private synchronized void closeCurrent(long current) {
    if (current == generation) {
      Socket old = socket;
      socket = null;
      if (old != null)
        try {
          old.close();
        } catch (IOException ignored) {
        }
    }
  }

  public synchronized void disconnect() {
    generation++;
    Socket old = socket;
    socket = null;
    if (old != null)
      try {
        old.close();
      } catch (IOException ignored) {
      }
  }

  @Override
  public void close() {
    disconnect();
    io.shutdownNow();
  }
}
