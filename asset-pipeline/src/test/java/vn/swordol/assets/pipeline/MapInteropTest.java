package vn.swordol.assets.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import vn.swordol.assets.runtime.AssetBundle;
import vn.swordol.assets.runtime.MapData;

/** Proves the editor-side writer and the runtime-side reader agree on one schema. */
public class MapInteropTest {
  private Path project() throws IOException {
    Path root = Files.createTempDirectory("map-interop-test");
    Files.createDirectory(root.resolve("images"));
    Files.createDirectory(root.resolve("maps"));
    Files.write(root.resolve("images/terrain.png"), MapValidationTest.png(64, 64));
    MapDocument town = MapDocument.create("world.town", 2, 2, 32, 32);
    MapDocument field = MapDocument.create("world.field", 5, 4, 32, 32);
    field.addTileset("terrain", "tileset.terrain", 2, 4);
    field.addLayer("decor");
    field.setTile(0, 1, 2, 3);
    field.setTile(1, 4, 3, 2 | MapDocument.FLIP_V);
    field.setBlocked(2, 2, true);
    field.addSpawn("slime-1", "monster", "slime", 3, 1, 30);
    field.addPortal("to-town", 0, 3, "world.town", 1, 1);
    Files.write(root.resolve("maps/field.map.json"), field.toJsonBytes());
    Files.write(root.resolve("maps/town.map.json"), town.toJsonBytes());
    String registry =
        "{\"schemaVersion\":1,\"assets\":["
            + "{\"id\":\"tileset.terrain\",\"type\":\"image\",\"source\":\"images/terrain.png\","
            + "\"dependencies\":[],\"provenance\":{}},"
            + "{\"id\":\"world.field\",\"type\":\"map\",\"source\":\"maps/field.map.json\","
            + "\"dependencies\":[\"tileset.terrain\"],\"provenance\":{}},"
            + "{\"id\":\"world.town\",\"type\":\"map\",\"source\":\"maps/town.map.json\","
            + "\"dependencies\":[],\"provenance\":{}}]}";
    Files.write(root.resolve("registry.json"), registry.getBytes(StandardCharsets.UTF_8));
    return root;
  }

  @Test
  public void runtimeReadsMapsBuiltByThePipeline() throws Exception {
    Path root = project();
    Path bundleFile = root.resolve("maps.swb");
    AssetBuilder.build(root, bundleFile);
    AssetBundle bundle;
    try (InputStream input = Files.newInputStream(bundleFile)) {
      bundle = AssetBundle.load(input);
    }
    assertEquals("map", bundle.type("world.field"));
    byte[] bytes = bundle.read("world.field");
    assertNotNull(bytes);
    MapData map = MapData.parse(bytes);
    assertEquals("world.field", map.id());
    assertEquals(5, map.width());
    assertEquals(4, map.height());
    assertEquals(2, map.layerCount());
    assertEquals("ground", map.layerName(0));
    assertEquals("decor", map.layerName(1));
    assertEquals(3, map.tileWord(0, 1, 2));
    assertEquals(2, MapData.gid(map.tileWord(1, 4, 3)));
    assertTrue(MapData.flipV(map.tileWord(1, 4, 3)));
    assertTrue(map.isBlocked(2, 2));
    assertEquals("slime-1", map.spawns().get(0).id());
    assertEquals(30, map.spawns().get(0).hp());
    assertEquals("world.town", map.portalAt(0, 3).targetMap());
    assertEquals(
        PipelineIO.hash(Files.readAllBytes(root.resolve("maps/field.map.json"))), map.version());
    assertEquals("map", bundle.type("world.town"));
    assertEquals(2, MapData.parse(bundle.read("world.town")).width());
  }
}
