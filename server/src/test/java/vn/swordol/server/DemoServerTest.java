package vn.swordol.server;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import org.junit.*;
import vn.swordol.protocol.*;

public class DemoServerTest {
  private DemoServer server;
  private Socket socket;
  private int sequence;

  @Before
  public void start() throws Exception {
    server = new DemoServer(0);
    server.start();
    socket = new Socket("127.0.0.1", server.getPort());
    socket.setSoTimeout(2000);
  }

  @After
  public void stop() throws Exception {
    socket.close();
    server.close();
  }

  private Response call(String id, String type, String payload) throws Exception {
    FrameCodec.write(
        socket.getOutputStream(),
        FrameCodec.encode(
            new Request(id, type, JsonParser.parseString(payload).getAsJsonObject())));
    return FrameCodec.GSON.fromJson(FrameCodec.read(socket.getInputStream()), Response.class);
  }

  private Response call(String type, String payload) throws Exception {
    return call("r" + (sequence++), type, payload);
  }

  @Test
  public void loginRejectsMultipleLineFeeds() throws Exception {
    Response response = call("LOGIN", "{\"name\":\"D\\nN\\nM\"}");
    assertFalse(response.ok);
    assertEquals("BAD_PAYLOAD", response.error.code);
  }

  @Test
  public void loginRejectsCarriageReturnLineFeed() throws Exception {
    Response response = call("LOGIN", "{\"name\":\"D\\r\\nN\"}");
    assertFalse(response.ok);
    assertEquals("BAD_PAYLOAD", response.error.code);
  }

  @Test
  public void loginAcceptsPrintableUnicode() throws Exception {
    assertTrue(call("LOGIN", "{\"name\":\"Kiếm Khách\"}").ok);
    assertEquals(
        "Kiếm Khách",
        call("ENTER_WORLD", "{}").payload.getAsJsonObject("player").get("name").getAsString());
  }

  private void login() throws Exception {
    assertTrue(call("LOGIN", "{\"name\":\"Demo\"}").ok);
    assertTrue(call("ENTER_WORLD", "{}").ok);
  }

  @Test
  public void requiresSocketSession() throws Exception {
    assertEquals("UNAUTHENTICATED", call("ENTER_WORLD", "{}").error.code);
    login();
    try (Socket other = new Socket("127.0.0.1", server.getPort())) {
      FrameCodec.write(
          other.getOutputStream(),
          "{\"requestId\":\"x\",\"type\":\"MOVE\",\"payload\":{\"x\":2,\"y\":1,\"sessionId\":\"stolen\"}}");
      assertFalse(
          FrameCodec.GSON.fromJson(FrameCodec.read(other.getInputStream()), Response.class).ok);
    }
  }

  @Test
  public void movementIsAtomicAndBounded() throws Exception {
    login();
    assertFalse(call("MOVE", "{\"x\":100,\"y\":2}").ok);
    assertFalse(call("MOVE", "{\"x\":2.5,\"y\":1}").ok);
    Response state = call("ENTER_WORLD", "{}");
    assertEquals(1, state.payload.getAsJsonObject("player").get("x").getAsInt());
    assertTrue(call("MOVE", "{\"x\":2,\"y\":1}").ok);
  }

  @Test
  public void attackRangeCooldownReplayAndReward() throws Exception {
    login();
    assertEquals("OUT_OF_RANGE", call("ATTACK", "{\"targetId\":\"slime-1\"}").error.code);
    call("MOVE", "{\"x\":2,\"y\":1}");
    Response first = call("hit", "ATTACK", "{\"targetId\":\"slime-1\"}");
    assertTrue(first.ok);
    assertEquals(
        FrameCodec.encode(first),
        FrameCodec.encode(call("hit", "ATTACK", "{\"targetId\":\"slime-1\"}")));
    assertEquals("DUPLICATE_REQUEST", call("hit", "MOVE", "{\"x\":1,\"y\":1}").error.code);
    assertEquals("COOLDOWN", call("ATTACK", "{\"targetId\":\"slime-1\"}").error.code);
    Thread.sleep(280);
    call("ATTACK", "{\"targetId\":\"slime-1\"}");
    Thread.sleep(280);
    Response kill = call("ATTACK", "{\"targetId\":\"slime-1\"}");
    assertEquals(10, kill.payload.getAsJsonObject("player").get("gold").getAsInt());
    assertFalse(call("ATTACK", "{\"targetId\":\"slime-1\"}").ok);
    assertEquals(
        10, call("ENTER_WORLD", "{}").payload.getAsJsonObject("player").get("gold").getAsInt());
  }

  @Test
  public void cannotResetSessionByRelogin() throws Exception {
    login();
    call("MOVE", "{\"x\":2,\"y\":1}");
    assertEquals("ALREADY_LOGGED_IN", call("LOGIN", "{\"name\":\"Other\"}").error.code);
    assertEquals(
        2, call("ENTER_WORLD", "{}").payload.getAsJsonObject("player").get("x").getAsInt());
  }

  @Test
  public void oversizedSocketIsClosedWhileServerRemainsAvailable() throws Exception {
    byte[] oversized = new byte[16385];
    java.util.Arrays.fill(oversized, (byte) 97);
    socket.getOutputStream().write(oversized);
    socket.getOutputStream().flush();
    try {
      assertEquals(-1, socket.getInputStream().read());
    } catch (SocketException expected) {
    }
    socket.close();
    socket = new Socket("127.0.0.1", server.getPort());
    socket.setSoTimeout(2000);
    assertTrue(call("PING", "{}").ok);
  }

  @Test
  public void authenticatedSocketsKeepIndependentState() throws Exception {
    login();
    call("MOVE", "{\"x\":2,\"y\":1}");
    Socket first = socket;
    try {
      socket = new Socket("127.0.0.1", server.getPort());
      socket.setSoTimeout(2000);
      login();
      assertEquals(
          1, call("ENTER_WORLD", "{}").payload.getAsJsonObject("player").get("x").getAsInt());
      call("MOVE", "{\"x\":10,\"y\":10,\"sessionId\":\"anything\"}");
    } finally {
      socket.close();
      socket = first;
    }
    assertEquals(
        2, call("ENTER_WORLD", "{}").payload.getAsJsonObject("player").get("x").getAsInt());
  }

  @Test
  public void replayWindowIsBoundedAndEvictsOldest() throws Exception {
    assertTrue(call("old", "PING", "{}").ok);
    for (int i = 0; i < 256; i++) assertTrue(call("PING", "{}").ok);
    assertEquals("NOT_IMPLEMENTED", call("old", "NEW_TYPE", "{}").error.code);
  }

  @Test
  public void malformedDoesNotKillServer() throws Exception {
    FrameCodec.write(socket.getOutputStream(), "{oops");
    assertEquals(
        "BAD_REQUEST",
        FrameCodec.GSON.fromJson(FrameCodec.read(socket.getInputStream()), Response.class)
            .error
            .code);
    assertTrue(call("PING", "{}").ok);
    assertEquals("NOT_IMPLEMENTED", call("QUEST", "{}").error.code);
  }
}
