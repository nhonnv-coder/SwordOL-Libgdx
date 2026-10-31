package vn.swordol.client.network;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class JsonConnectionTest {
  @Test
  public void dispatchesOnCallerExecutorAndDropsResponsesAfterDisconnect() throws Exception {
    BlockingQueue<Runnable> render = new LinkedBlockingQueue<>();
    BlockingQueue<JsonObject> replies = new LinkedBlockingQueue<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (ServerSocket server = new ServerSocket(0);
        JsonConnection connection =
            new JsonConnection(
                render::add, replies::add, status -> failure.set(new AssertionError(status)))) {
      Thread peer =
          new Thread(() -> {
            try (Socket socket = server.accept()) {
              BufferedReader input =
                  new BufferedReader(
                      new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
              for (int index = 0; index < 2; index++) {
                JsonObject request = JsonParser.parseString(input.readLine()).getAsJsonObject();
                JsonObject response = new JsonObject();
                response.add("requestId", request.get("requestId"));
                response.add("type", request.get("type"));
                response.addProperty("ok", true);
                response.add("payload", new JsonObject());
                socket.getOutputStream().write((response + "\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
              }
            } catch (Throwable ex) {
              failure.set(ex);
            }
          });
      peer.setDaemon(true);
      peer.start();
      connection.connect("127.0.0.1", server.getLocalPort());
      connection.request("LOGIN", new JsonObject());
      Runnable first = render.poll(4, TimeUnit.SECONDS);
      assertNotNull(first);
      assertTrue("callback must wait for render dispatcher", replies.isEmpty());
      first.run();
      assertEquals("LOGIN", replies.remove().get("type").getAsString());
      connection.request("ENTER_WORLD", new JsonObject());
      Runnable stale = render.poll(4, TimeUnit.SECONDS);
      assertNotNull(stale);
      connection.disconnect();
      stale.run();
      assertTrue("old session reply must not enter new world", replies.isEmpty());
      peer.join(4000);
      assertFalse(peer.isAlive());
      assertNull(failure.get());
    }
  }
}
