package vn.swordol.assets.pipeline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class MapDocumentTest {
  private MapDocument painted() throws IOException {
    MapDocument map = MapDocument.create("world.test", 4, 3, 32, 32);
    map.addTileset("terrain", "tileset.terrain", 2, 4);
    map.setTile(0, 1, 0, 1);
    map.setTile(0, 0, 1, 2 | MapDocument.FLIP_H | MapDocument.FLIP_D);
    map.setBlocked(2, 2, true);
    map.addSpawn("slime-1", "monster", "slime", 3, 1, 30);
    map.addPortal("to-town", 3, 2, "world.town", 1, 1);
    return map;
  }

  private void rejects(String json) {
    try {
      MapDocument.parse(json.getBytes(StandardCharsets.UTF_8));
      fail("accepted invalid map document: " + json);
    } catch (IOException expected) {
    }
  }

  private String document(String overrides) {
    String base =
        "{\"schemaVersion\":1,\"id\":\"world.test\",\"orientation\":\"orthogonal\","
            + "\"width\":2,\"height\":2,\"tileWidth\":32,\"tileHeight\":32,"
            + "\"tilesets\":[{\"name\":\"terrain\",\"asset\":\"tileset.terrain\","
            + "\"columns\":2,\"tileCount\":4}],"
            + "\"layers\":[{\"name\":\"ground\",\"tiles\":[0,0,0,0]}],"
            + "\"collision\":[0,0,0,0],\"spawns\":[],\"portals\":[]}";
    if (overrides.isEmpty()) {
      return base;
    }
    return base.substring(0, base.length() - 1) + "," + overrides + "}";
  }

  private String replace(String field, String value) {
    return document("").replace(field, value);
  }

  @Test
  public void roundTripPreservesContentAndBytes() throws Exception {
    MapDocument map = painted();
    byte[] bytes = map.toJsonBytes();
    MapDocument reopened = MapDocument.parse(bytes);
    assertEquals("world.test", reopened.id());
    assertEquals(4, reopened.width());
    assertEquals(3, reopened.height());
    assertEquals(32, reopened.tileWidth());
    assertEquals(1, reopened.layerCount());
    assertEquals("ground", reopened.layerName(0));
    assertEquals(1, reopened.tile(0, 1, 0));
    assertTrue(reopened.blocked(2, 2));
    assertFalse(reopened.blocked(0, 0));
    assertEquals(1, reopened.spawns().size());
    assertEquals(30, reopened.spawns().get(0).hp());
    assertEquals("world.town", reopened.portals().get(0).targetMap());
    assertArrayEquals(bytes, reopened.toJsonBytes());
  }

  @Test
  public void tileFlipBitsSurviveRoundTrip() throws Exception {
    MapDocument reopened = MapDocument.parse(painted().toJsonBytes());
    int word = reopened.tile(0, 0, 1);
    assertEquals(2, MapDocument.gid(word));
    assertTrue(MapDocument.flipH(word));
    assertFalse(MapDocument.flipV(word));
    assertTrue(MapDocument.flipD(word));
  }

  @Test
  public void coordinatesUseBottomLeftOriginRowMajor() throws Exception {
    MapDocument map = MapDocument.create("world.test", 4, 3, 32, 32);
    map.addTileset("terrain", "tileset.terrain", 2, 4);
    map.setTile(0, 1, 0, 3);
    String json = new String(map.toJsonBytes(), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"tiles\":[0,3,0,0,"));
    map.setTile(0, 0, 1, 4);
    json = new String(map.toJsonBytes(), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"tiles\":[0,3,0,0,4,"));
  }

  @Test
  public void layersCanBeAddedRemovedAndMoved() throws Exception {
    MapDocument map = painted();
    assertEquals(1, map.addLayer("decor"));
    map.setTile(1, 0, 0, 1);
    assertEquals(2, map.layerCount());
    map.moveLayer(1, 0);
    assertEquals("decor", map.layerName(0));
    assertEquals("ground", map.layerName(1));
    assertEquals(1, map.tile(0, 0, 0));
    assertEquals(1, map.tile(1, 1, 0));
    map.removeLayer(0);
    assertEquals(1, map.layerCount());
    assertEquals("ground", map.layerName(0));
  }

  @Test
  public void rejectsDuplicateLayerNameAndRemovingLastLayer() throws Exception {
    MapDocument map = painted();
    try {
      map.addLayer("ground");
      fail("duplicate layer name accepted");
    } catch (IOException expected) {
    }
    try {
      map.removeLayer(0);
      fail("removing the only layer accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void rejectsOutOfRangeEditingCoordinates() throws Exception {
    MapDocument map = painted();
    try {
      map.setTile(0, 4, 0, 1);
      fail("out of bounds column accepted");
    } catch (IOException expected) {
    }
    try {
      map.setTile(0, 0, -1, 1);
      fail("negative row accepted");
    } catch (IOException expected) {
    }
    try {
      map.setBlocked(0, 3, true);
      fail("out of bounds collision cell accepted");
    } catch (IOException expected) {
    }
    try {
      map.tile(1, 0, 0);
      fail("unknown layer accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void rejectsTileWordsOutsideTilesetRangeAndReservedBits() throws Exception {
    MapDocument map = painted();
    try {
      map.setTile(0, 0, 0, 5);
      fail("gid above tileset total accepted");
    } catch (IOException expected) {
    }
    try {
      map.setTile(0, 0, 0, -1);
      fail("negative tile word accepted");
    } catch (IOException expected) {
    }
    try {
      map.setTile(0, 0, 0, 1 | (1 << 27));
      fail("reserved tile bit accepted");
    } catch (IOException expected) {
    }
    rejects(replace("\"tiles\":[0,0,0,0]", "\"tiles\":[0,0,0,2147483648]"));
    rejects(replace("\"tiles\":[0,0,0,0]", "\"tiles\":[0,0,0,5]"));
  }

  @Test
  public void rejectsNumbersTheRuntimeReaderWouldRefuse() {
    rejects(replace("\"width\":2", "\"width\":2.0"));
    rejects(replace("\"width\":2", "\"width\":2e0"));
    rejects(replace("\"height\":2", "\"height\":02"));
    rejects(replace("\"tiles\":[0,0,0,0]", "\"tiles\":[0,0,0,1.0]"));
    rejects(replace("\"collision\":[0,0,0,0]", "\"collision\":[0,0,0,-0]"));
    rejects(replace("\"collision\":[0,0,0,0]", "\"collision\":[0,0,0,1.0]"));
    rejects(
        document(
            "\"spawns\":[{\"id\":\"a\",\"kind\":\"monster\",\"template\":\"slime\","
                + "\"x\":0,\"y\":0,\"hp\":1.0}]"));
  }

  @Test
  public void rejectsUnsupportedMapKinds() {
    rejects(replace("\"orientation\":\"orthogonal\"", "\"orientation\":\"isometric\""));
    rejects(replace("\"orientation\":\"orthogonal\"", "\"orientation\":\"hexagonal\""));
    rejects(document("\"infinite\":true"));
    rejects(
        replace(
            "\"layers\":[{\"name\":\"ground\",\"tiles\":[0,0,0,0]}]",
            "\"layers\":[{\"name\":\"ground\",\"chunks\":[]}]"));
    rejects(
        replace(
            "\"tilesets\":[{\"name\":\"terrain\",\"asset\":\"tileset.terrain\","
                + "\"columns\":2,\"tileCount\":4}]",
            "\"tilesets\":[{\"name\":\"terrain\",\"source\":\"terrain.tsx\","
                + "\"columns\":2,\"tileCount\":4}]"));
    rejects(document("\"objectTemplates\":[]"));
  }

  @Test
  public void rejectsMalformedGeometryAndLimits() {
    rejects(replace("\"tiles\":[0,0,0,0]", "\"tiles\":[0,0,0]"));
    rejects(replace("\"collision\":[0,0,0,0]", "\"collision\":[0,0,0,0,0]"));
    rejects(replace("\"collision\":[0,0,0,0]", "\"collision\":[0,0,0,2]"));
    rejects(replace("\"width\":2", "\"width\":0"));
    rejects(replace("\"width\":2", "\"width\":513"));
    rejects(replace("\"tileWidth\":32", "\"tileWidth\":0"));
    rejects(replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
    rejects(replace("\"layers\":[{\"name\":\"ground\",\"tiles\":[0,0,0,0]}]", "\"layers\":[]"));
    rejects(replace("\"id\":\"world.test\"", "\"id\":\"world test\""));
  }

  @Test
  public void rejectsInvalidSpawnsAndPortals() {
    rejects(
        document(
            "\"spawns\":[{\"id\":\"a\",\"kind\":\"monster\",\"template\":\"slime\",\"x\":2,\"y\":0,\"hp\":1}]"));
    rejects(
        document(
            "\"spawns\":[{\"id\":\"a\",\"kind\":\"dragonfly\",\"template\":\"slime\","
                + "\"x\":0,\"y\":0,\"hp\":1}]"));
    rejects(
        document(
            "\"spawns\":[{\"id\":\"a\",\"kind\":\"monster\",\"template\":\"slime\","
                + "\"x\":0,\"y\":0,\"hp\":1},{\"id\":\"a\",\"kind\":\"monster\","
                + "\"template\":\"slime\",\"x\":1,\"y\":0,\"hp\":1}]"));
    rejects(
        document(
            "\"portals\":[{\"id\":\"p\",\"x\":9,\"y\":0,\"targetMap\":\"world.town\","
                + "\"targetX\":0,\"targetY\":0}]"));
    rejects(document("\"portals\":[{\"id\":\"p\",\"x\":0,\"y\":0,\"targetX\":0,\"targetY\":0}]"));
    rejects(
        document(
            "\"portals\":[{\"id\":\"p\",\"x\":0,\"y\":0,\"targetMap\":\"world.town\","
                + "\"targetX\":-1,\"targetY\":0}]"));
  }

  @Test
  public void rejectsDuplicateEditedSpawnAndPortalIds() throws Exception {
    MapDocument map = painted();
    try {
      map.addSpawn("slime-1", "monster", "slime", 0, 0, 10);
      fail("duplicate spawn ID accepted");
    } catch (IOException expected) {
    }
    try {
      map.addPortal("to-town", 0, 0, "world.town", 1, 1);
      fail("duplicate portal ID accepted");
    } catch (IOException expected) {
    }
    map.removeSpawn("slime-1");
    assertEquals(0, map.spawns().size());
    map.removePortal("to-town");
    assertEquals(0, map.portals().size());
  }

  @Test
  public void canonicalBytesAreStableAcrossRepeatedWrites() throws Exception {
    MapDocument map = painted();
    assertArrayEquals(map.toJsonBytes(), map.toJsonBytes());
    assertArrayEquals(map.toJsonBytes(), MapDocument.parse(map.toJsonBytes()).toJsonBytes());
  }
}
