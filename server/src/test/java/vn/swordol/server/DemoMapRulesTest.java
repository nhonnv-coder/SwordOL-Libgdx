package vn.swordol.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.InputStream;
import org.junit.Test;
import vn.swordol.assets.runtime.AssetBundle;
import vn.swordol.assets.runtime.MapData;

/** The demo rules must come from the built map, not from numbers written into the server. */
public class DemoMapRulesTest {
  private DemoSession world() throws Exception {
    DemoSession session = new DemoSession(DemoWorld.fromClasspath());
    session.handle(request("LOGIN", "{\"name\":\"Mapper\"}"));
    session.handle(request("ENTER_WORLD", "{}"));
    return session;
  }

  private vn.swordol.protocol.Request request(String type, String payload) {
    return vn.swordol.protocol.FrameCodec.decodeRequest(
        "{\"requestId\":\""
            + type
            + counter++
            + "\",\"type\":\""
            + type
            + "\",\"payload\":"
            + payload
            + "}");
  }

  private int counter;

  private JsonObject move(DemoSession session, int x, int y) {
    vn.swordol.protocol.Response response =
        session.handle(request("MOVE", "{\"x\":" + x + ",\"y\":" + y + "}"));
    JsonObject result = new JsonObject();
    result.addProperty("ok", response.ok);
    if (response.ok) {
      result.add("payload", response.payload);
    } else {
      result.addProperty("code", response.error.code);
    }
    return result;
  }

  @Test
  public void stateReportsTheMapItLoaded() throws Exception {
    JsonObject state = move(world(), 2, 1).getAsJsonObject("payload");
    JsonObject map = state.getAsJsonObject("map");
    MapData expected;
    try (InputStream input = DemoWorld.class.getResourceAsStream("/bundles/demo.swb")) {
      expected = MapData.parse(AssetBundle.load(input).read("world.demo-field"));
    }
    assertEquals("world.demo-field", map.get("id").getAsString());
    assertEquals(expected.version(), map.get("version").getAsString());
    assertEquals(expected.width(), map.get("width").getAsInt());
    assertEquals(expected.height(), map.get("height").getAsInt());
  }

  @Test
  public void monsterComesFromTheMapSpawn() throws Exception {
    JsonObject state = move(world(), 2, 1).getAsJsonObject("payload");
    JsonObject monster = state.getAsJsonObject("monster");
    assertEquals("slime-1", monster.get("id").getAsString());
    assertEquals(3, monster.get("x").getAsInt());
    assertEquals(1, monster.get("y").getAsInt());
    assertEquals(30, monster.get("hp").getAsInt());
  }

  @Test
  public void playerStartsOnTheMapPlayerStartSpawn() throws Exception {
    DemoSession session = new DemoSession(DemoWorld.fromClasspath());
    session.handle(request("LOGIN", "{\"name\":\"Mapper\"}"));
    JsonObject player =
        session.handle(request("ENTER_WORLD", "{}")).payload.getAsJsonObject("player");
    assertEquals(1, player.get("x").getAsInt());
    assertEquals(1, player.get("y").getAsInt());
  }

  @Test
  public void blockedCellsAreRefused() throws Exception {
    DemoSession session = world();
    assertEquals("BLOCKED", move(session, 14, 10).get("code").getAsString());
    assertEquals("BLOCKED", move(session, 7, 5).get("code").getAsString());
    assertTrue(move(session, 10, 10).get("ok").getAsBoolean());
  }

  @Test
  public void boundsComeFromTheMapNotFromAConstant() throws Exception {
    DemoSession session = world();
    assertTrue(move(session, 19, 14).get("ok").getAsBoolean());
    assertEquals("OUT_OF_BOUNDS", move(session, 20, 14).get("code").getAsString());
    assertEquals("OUT_OF_BOUNDS", move(session, 19, 15).get("code").getAsString());
  }

  @Test
  public void steppingOnAPortalMovesThePlayerToTheTargetMap() throws Exception {
    DemoSession session = world();
    JsonObject state = move(session, 19, 7).getAsJsonObject("payload");
    JsonObject map = state.getAsJsonObject("map");
    assertEquals("world.town", map.get("id").getAsString());
    assertEquals(10, map.get("width").getAsInt());
    assertEquals(1, state.getAsJsonObject("player").get("x").getAsInt());
    assertEquals(1, state.getAsJsonObject("player").get("y").getAsInt());
    assertFalse(state.has("monster") && state.get("monster").isJsonObject());
    MapData town;
    try (InputStream input = DemoWorld.class.getResourceAsStream("/bundles/demo.swb")) {
      town = MapData.parse(AssetBundle.load(input).read("world.town"));
    }
    assertEquals(town.version(), map.get("version").getAsString());
    assertEquals("BLOCKED", move(session, 3, 7).get("code").getAsString());
  }

  @Test
  public void attackIsRefusedOnAMapWithoutThatMonster() throws Exception {
    DemoSession session = world();
    move(session, 19, 7);
    assertEquals(
        "UNKNOWN_TARGET",
        session.handle(request("ATTACK", "{\"targetId\":\"slime-1\"}")).error.code);
  }

  @Test
  public void bundledMapsAreTheOnesTheEditorProduces() throws Exception {
    DemoWorld loaded = DemoWorld.fromClasspath();
    assertNotNull(loaded.map("world.demo-field"));
    assertNotNull(loaded.map("world.town"));
    assertEquals("world.demo-field", DemoWorld.START_MAP);
  }
}
