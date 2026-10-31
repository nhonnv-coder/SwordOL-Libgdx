package vn.swordol.assets.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.Test;

public class MapDataTest {
  private static final String FIXTURE =
      "{\"collision\":[0,0,0,0,0,1],\"height\":2,\"id\":\"world.fixture\","
          + "\"layers\":[{\"name\":\"ground\",\"tiles\":[0,1,2,3,268435458,0]}],"
          + "\"orientation\":\"orthogonal\","
          + "\"portals\":[{\"id\":\"gate\",\"targetMap\":\"world.town\",\"targetX\":1,"
          + "\"targetY\":0,\"x\":0,\"y\":1}],\"schemaVersion\":1,"
          + "\"spawns\":[{\"hp\":30,\"id\":\"slime-1\",\"kind\":\"monster\","
          + "\"template\":\"slime\",\"x\":1,\"y\":0}],"
          + "\"tileHeight\":16,\"tileWidth\":16,"
          + "\"tilesets\":[{\"asset\":\"tileset.terrain\",\"columns\":2,\"name\":\"terrain\","
          + "\"tileCount\":4}],\"width\":3}";

  private MapData fixture() throws IOException {
    return MapData.parse(FIXTURE.getBytes(StandardCharsets.UTF_8));
  }

  private void rejects(String json) {
    try {
      MapData.parse(json.getBytes(StandardCharsets.UTF_8));
      fail("accepted invalid map: " + json);
    } catch (IOException expected) {
    }
  }

  private void rejects(byte[] bytes, String what) {
    try {
      MapData.parse(bytes);
      fail("accepted invalid map: " + what);
    } catch (IOException expected) {
    }
  }

  private String replace(String field, String value) {
    return FIXTURE.replace(field, value);
  }

  @Test
  public void exposesGeometryLayersAndCells() throws Exception {
    MapData map = fixture();
    assertEquals("world.fixture", map.id());
    assertEquals(3, map.width());
    assertEquals(2, map.height());
    assertEquals(16, map.tileWidth());
    assertEquals(16, map.tileHeight());
    assertEquals(1, map.layerCount());
    assertEquals("ground", map.layerName(0));
    assertEquals(1, map.tilesets().size());
    assertEquals("tileset.terrain", map.tilesets().get(0).asset());
    assertEquals(1, map.tilesets().get(0).firstGid());
    assertEquals(0, map.tileWord(0, 0, 0));
    assertEquals(1, map.tileWord(0, 1, 0));
    assertEquals(2, map.tileWord(0, 2, 0));
    assertEquals(3, map.tileWord(0, 0, 1));
    assertTrue(map.inBounds(2, 1));
    assertFalse(map.inBounds(3, 1));
    assertFalse(map.inBounds(-1, 0));
    assertTrue(map.isBlocked(2, 1));
    assertFalse(map.isBlocked(0, 0));
  }

  @Test
  public void exposesGidAndFlipBits() throws Exception {
    int word = fixture().tileWord(0, 1, 1);
    assertEquals(2, MapData.gid(word));
    assertTrue(MapData.flipH(word));
    assertFalse(MapData.flipV(word));
    assertFalse(MapData.flipD(word));
  }

  @Test
  public void exposesSpawnsAndPortals() throws Exception {
    MapData map = fixture();
    assertEquals(1, map.spawns().size());
    assertEquals("slime-1", map.spawns().get(0).id());
    assertEquals("monster", map.spawns().get(0).kind());
    assertEquals("slime", map.spawns().get(0).template());
    assertEquals(30, map.spawns().get(0).hp());
    assertEquals(1, map.spawns().get(0).x());
    assertNotNull(map.portalAt(0, 1));
    assertEquals("world.town", map.portalAt(0, 1).targetMap());
    assertEquals(1, map.portalAt(0, 1).targetX());
    assertNull(map.portalAt(1, 1));
  }

  @Test
  public void returnedCollectionsAreImmutable() throws Exception {
    MapData map = fixture();
    try {
      map.spawns().clear();
      fail("spawn list is modifiable");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.portals().clear();
      fail("portal list is modifiable");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.tilesets().clear();
      fail("tileset list is modifiable");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void rejectsCellAndLayerAccessOutsideTheMap() throws Exception {
    MapData map = fixture();
    try {
      map.tileWord(0, 3, 0);
      fail("out of bounds cell accepted");
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      map.tileWord(1, 0, 0);
      fail("unknown layer accepted");
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      map.isBlocked(0, 2);
      fail("out of bounds collision cell accepted");
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test
  public void versionIsSha256OfTheParsedBytes() throws Exception {
    byte[] bytes = FIXTURE.getBytes(StandardCharsets.UTF_8);
    StringBuilder expected = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      expected.append(String.format("%02x", value & 255));
    }
    assertEquals(expected.toString(), fixture().version());
    assertEquals(fixture().version(), MapData.parse(bytes).version());
    assertNotEquals(
        fixture().version(),
        MapData.parse(
                replace("\"id\":\"world.fixture\"", "\"id\":\"world.other\"")
                    .getBytes(StandardCharsets.UTF_8))
            .version());
  }

  @Test
  public void rejectsUnsupportedMapKinds() {
    rejects(replace("\"orientation\":\"orthogonal\"", "\"orientation\":\"isometric\""));
    rejects(replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"infinite\":true"));
    rejects(
        replace(
            "\"name\":\"ground\",\"tiles\":[0,1,2,3,268435458,0]",
            "\"name\":\"ground\",\"chunks\":[]"));
    rejects(replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
    rejects(replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"objectTemplates\":[]"));
  }

  @Test
  public void rejectsBrokenGeometryAndTiles() {
    rejects(replace("\"tiles\":[0,1,2,3,268435458,0]", "\"tiles\":[0,1,2,3,0]"));
    rejects(replace("\"collision\":[0,0,0,0,0,1]", "\"collision\":[0,0,0,0,1]"));
    rejects(replace("\"collision\":[0,0,0,0,0,1]", "\"collision\":[0,0,0,0,0,2]"));
    rejects(replace("\"width\":3", "\"width\":0"));
    rejects(replace("\"width\":3", "\"width\":513"));
    rejects(replace("\"tileWidth\":16", "\"tileWidth\":0"));
    rejects(replace("\"tiles\":[0,1,2,3,268435458,0]", "\"tiles\":[0,1,2,3,5,0]"));
    rejects(replace("\"tiles\":[0,1,2,3,268435458,0]", "\"tiles\":[0,1,2,3,-1,0]"));
    rejects(replace("\"tiles\":[0,1,2,3,268435458,0]", "\"tiles\":[0,1,2,3,2147483648,0]"));
    rejects(
        replace(
            "\"layers\":[{\"name\":\"ground\",\"tiles\":[0,1,2,3,268435458,0]}]", "\"layers\":[]"));
    rejects(replace("\"id\":\"world.fixture\"", "\"id\":\"world fixture\""));
  }

  @Test
  public void rejectsBrokenSpawnsAndPortals() {
    rejects(replace("\"x\":1,\"y\":0}]", "\"x\":3,\"y\":0}]"));
    rejects(replace("\"kind\":\"monster\"", "\"kind\":\"dragon\""));
    rejects(replace("\"x\":0,\"y\":1}]", "\"x\":0,\"y\":5}]"));
    rejects(replace("\"targetMap\":\"world.town\",", ""));
    rejects(
        replace(
            "\"portals\":[{\"id\":\"gate\",\"targetMap\":\"world.town\",\"targetX\":1,"
                + "\"targetY\":0,\"x\":0,\"y\":1}]",
            "\"portals\":[{\"id\":\"gate\",\"targetMap\":\"world.town\",\"targetX\":1,"
                + "\"targetY\":0,\"x\":0,\"y\":1},{\"id\":\"gate\",\"targetMap\":\"world.town\","
                + "\"targetX\":1,\"targetY\":0,\"x\":1,\"y\":1}]"));
  }

  @Test
  public void rejectsMalformedEncodingAndStructure() {
    rejects("not json");
    rejects("[]");
    rejects("");
    rejects(new byte[] {(byte) 0xff, (byte) 0xfe, 0x7b}, "invalid UTF-8");
    rejects(FIXTURE + "{}");
    rejects(replace("\"width\":3", "\"width\":3,\"width\":4"));
    rejects(replace("\"width\":3", "\"width\":\"3\""));
  }

  @Test
  public void rejectsDocumentsBeyondDeclaredLimits() {
    StringBuilder layers = new StringBuilder("\"layers\":[");
    for (int index = 0; index <= 16; index++) {
      layers
          .append(index == 0 ? "" : ",")
          .append("{\"name\":\"l")
          .append(index)
          .append("\",\"tiles\":[0,1,2,3,0,0]}");
    }
    rejects(
        replace(
            "\"layers\":[{\"name\":\"ground\",\"tiles\":[0,1,2,3,268435458,0]}]", layers + "]"));
    StringBuilder tilesets = new StringBuilder("\"tilesets\":[");
    for (int index = 0; index <= 8; index++) {
      tilesets
          .append(index == 0 ? "" : ",")
          .append("{\"asset\":\"tileset.t")
          .append(index)
          .append("\",\"columns\":2,\"name\":\"t")
          .append(index)
          .append("\",\"tileCount\":4}");
    }
    rejects(
        replace(
            "\"tilesets\":[{\"asset\":\"tileset.terrain\",\"columns\":2,\"name\":\"terrain\","
                + "\"tileCount\":4}]",
            tilesets + "]"));
  }
}
