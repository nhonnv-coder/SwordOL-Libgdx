package vn.swordol.client.application;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;

public class ClientFlowTest {
  // Catches missing LOGIN -> ENTER_WORLD chaining and wrong MOVE payloads.
  @Test
  public void commandFlowAndRemovedMediator() throws Exception {
    ServerSocket server = new ServerSocket(0);
    List<String> requests = new CopyOnWriteArrayList<>();
    Thread peer =
        new Thread(() -> {
          try (Socket socket = server.accept()) {
            BufferedReader in =
                new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter out =
                new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            for (int i = 0; i < 4; i++) {
              String line = in.readLine();
              requests.add(line);
              JsonObject req = new com.google.gson.Gson().fromJson(line, JsonObject.class);
              out.println(
                  "{\"requestId\":"
                      + req.get("requestId")
                      + ",\"type\":"
                      + req.get("type")
                      + ",\"ok\":true,\"payload\":"
                      + (i == 0
                          ? "{\"sessionId\":\"demo\"}"
                          : "{\"player\":{\"name\":\"Hero\",\"x\":2,\"y\":3,\"hp\":100,\"gold\":0},\"monster\":{\"id\":\"m1\",\"x\":4,\"y\":4,\"hp\":20},\"map\":{\"width\":10,\"height\":8}}")
                      + "}");
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    peer.start();
    BlockingQueue<Runnable> render = new LinkedBlockingQueue<>();
    GameFacade facade = new GameFacade(render::add);
    List<String> updates = new ArrayList<>();
    GameView view =
        new GameView() {
          public void showState(JsonObject s) {
            updates.add(s.getAsJsonObject("player").get("name").getAsString());
          }

          public void showStatus(String s) {}
        };
    facade.attach(view);
    facade.connect("127.0.0.1", server.getLocalPort(), "Hero");
    long until = System.currentTimeMillis() + 5000;
    while (updates.isEmpty() && System.currentTimeMillis() < until) {
      Runnable r = render.poll(100, TimeUnit.MILLISECONDS);
      if (r != null) r.run();
    }
    assertEquals(Arrays.asList("Hero"), updates);
    facade.move(2, 3);
    peer.join(6500);
    assertEquals(4, requests.size());
    assertEquals(
        "PING",
        new com.google.gson.Gson()
            .fromJson(requests.get(3), JsonObject.class)
            .get("type")
            .getAsString());
    JsonObject move = new com.google.gson.Gson().fromJson(requests.get(2), JsonObject.class);
    assertEquals("MOVE", move.get("type").getAsString());
    assertEquals(2, move.getAsJsonObject("payload").get("x").getAsInt());
    facade.detach();
    Runnable r;
    while ((r = render.poll()) != null) r.run();
    assertEquals(1, updates.size());
    // A missing error route, unbounded frame reader, or malformed-response crash breaks these.
    assertRejected(facade, render, "{}", "Network error:");
    char[] huge = new char[16385];
    Arrays.fill(huge, 'x');
    assertRejected(facade, render, new String(huge), "Response too large");
    assertRejected(facade, render, "ERROR", "BAD_NAME: rejected");
    facade.close();
    GameFacade reopened = GameFacade.open(render::add);
    assertRejected(reopened, render, "ERROR", "BAD_NAME: rejected");
    reopened.close();
    server.close();
  }

  private void assertRejected(
      GameFacade facade, BlockingQueue<Runnable> render, String fixture, String expectedStatus)
      throws Exception {
    List<String> statuses = new ArrayList<>();
    facade.attach(
        new GameView() {
          public void showState(JsonObject s) {
            fail("Invalid response must not display a world");
          }

          public void showStatus(String s) {
            statuses.add(s);
          }
        });
    try (ServerSocket peer = new ServerSocket(0)) {
      Thread responder =
          new Thread(() -> {
            try (Socket socket = peer.accept()) {
              BufferedReader in =
                  new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
              JsonObject request =
                  new com.google.gson.Gson().fromJson(in.readLine(), JsonObject.class);
              String reply =
                  fixture.equals("ERROR")
                      ? "{\"requestId\":"
                          + request.get("requestId")
                          + ",\"type\":\"LOGIN\",\"ok\":false,\"error\":{\"code\":\"BAD_NAME\",\"message\":\"rejected\"}}"
                      : fixture;
              socket.getOutputStream().write((reply + "\n").getBytes("UTF-8"));
              socket.getOutputStream().flush();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      responder.start();
      facade.connect("127.0.0.1", peer.getLocalPort(), "Hero");
      long deadline = System.currentTimeMillis() + 3000;
      while (statuses.stream().noneMatch(s -> s.contains(expectedStatus))
          && System.currentTimeMillis() < deadline) {
        Runnable next = render.poll(100, TimeUnit.MILLISECONDS);
        if (next != null) next.run();
      }
      assertTrue(statuses.toString(), statuses.stream().anyMatch(s -> s.contains(expectedStatus)));
      facade.detach();
      responder.join(1000);
    }
  }
}
