package vn.swordol.assets.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class MapRegistryTest {
  private Path project() throws IOException {
    Path root = Files.createTempDirectory("map-registry-test");
    Files.createDirectory(root.resolve("images"));
    Files.createDirectory(root.resolve("maps"));
    Files.write(root.resolve("images/terrain.png"), MapValidationTest.png(64, 64));
    return root;
  }

  private void writeMap(Path root, String file, MapDocument map) throws IOException {
    Files.write(root.resolve("maps/" + file), map.toJsonBytes());
  }

  private void registry(Path root, String... assets) throws IOException {
    StringBuilder json = new StringBuilder("{\"schemaVersion\":1,\"assets\":[");
    for (int index = 0; index < assets.length; index++) {
      json.append(index == 0 ? "" : ",").append(assets[index]);
    }
    Files.write(
        root.resolve("registry.json"),
        json.append("]}").toString().getBytes(StandardCharsets.UTF_8));
  }

  private String asset(String id, String type, String source, String dependencies) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\""
        + type
        + "\",\"source\":\""
        + source
        + "\",\"dependencies\":["
        + dependencies
        + "],\"provenance\":{}}";
  }

  private MapDocument field() throws IOException {
    MapDocument map = MapDocument.create("world.field", 4, 3, 32, 32);
    map.addTileset("terrain", "tileset.terrain", 2, 4);
    map.setTile(0, 0, 0, 1);
    map.addPortal("to-town", 3, 2, "world.town", 1, 1);
    return map;
  }

  private Path consistentProject() throws IOException {
    Path root = project();
    writeMap(root, "field.map.json", field());
    writeMap(root, "town.map.json", MapDocument.create("world.town", 2, 2, 32, 32));
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"tileset.terrain\""),
        asset("world.town", "map", "maps/town.map.json", ""));
    return root;
  }

  private void invalid(Path root) throws Exception {
    try {
      AssetRegistry.load(root);
      fail("invalid map project accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void loadsConsistentMapProjectAndReportsTilesetUsage() throws Exception {
    AssetRegistry registry = AssetRegistry.load(consistentProject());
    registry.validate("world.field");
    assertEquals(Collections.singletonList("world.field"), registry.findUsages("tileset.terrain"));
    assertEquals(Collections.singletonList("world.field"), registry.findUsages("world.town"));
  }

  @Test
  public void rejectsAMapWhoseTilesetImageIsTooSmall() throws Exception {
    Path root = project();
    Files.write(root.resolve("images/terrain.png"), MapValidationTest.png(32, 32));
    writeMap(root, "field.map.json", field());
    writeMap(root, "town.map.json", MapDocument.create("world.town", 2, 2, 32, 32));
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"tileset.terrain\""),
        asset("world.town", "map", "maps/town.map.json", ""));
    invalid(root);
  }

  @Test
  public void rejectsMapAssetWithBrokenDocument() throws Exception {
    Path root = project();
    Files.write(
        root.resolve("maps/field.map.json"),
        "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
    registry(root, asset("world.field", "map", "maps/field.map.json", ""));
    invalid(root);
  }

  @Test
  public void rejectsMapAssetWhoseDocumentIdDiffers() throws Exception {
    Path root = project();
    writeMap(root, "field.map.json", MapDocument.create("world.other", 2, 2, 32, 32));
    registry(root, asset("world.field", "map", "maps/field.map.json", ""));
    invalid(root);
  }

  @Test
  public void rejectsTilesetReferenceMissingFromDependencies() throws Exception {
    Path root = project();
    writeMap(root, "field.map.json", field());
    writeMap(root, "town.map.json", MapDocument.create("world.town", 2, 2, 32, 32));
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"world.town\""),
        asset("world.town", "map", "maps/town.map.json", ""));
    invalid(root);
  }

  @Test
  public void acceptsMutualPortalsAndReportsThemAsUsages() throws Exception {
    Path root = project();
    MapDocument town = MapDocument.create("world.town", 2, 2, 32, 32);
    town.addPortal("to-field", 0, 0, "world.field", 0, 0);
    writeMap(root, "field.map.json", field());
    writeMap(root, "town.map.json", town);
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"tileset.terrain\""),
        asset("world.town", "map", "maps/town.map.json", ""));
    AssetRegistry registry = AssetRegistry.load(root);
    assertEquals(Collections.singletonList("world.town"), registry.findUsages("world.field"));
    assertEquals(Collections.singletonList("world.field"), registry.findUsages("world.town"));
  }

  @Test
  public void rejectsPortalTargetDeclaredAsADependency() throws Exception {
    Path root = project();
    writeMap(root, "field.map.json", field());
    writeMap(root, "town.map.json", MapDocument.create("world.town", 2, 2, 32, 32));
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"tileset.terrain\",\"world.town\""),
        asset("world.town", "map", "maps/town.map.json", ""));
    invalid(root);
  }

  @Test
  public void rejectsPortalToAssetThatIsNotRegistered() throws Exception {
    Path root = project();
    writeMap(root, "field.map.json", field());
    registry(
        root,
        asset("tileset.terrain", "image", "images/terrain.png", ""),
        asset("world.field", "map", "maps/field.map.json", "\"tileset.terrain\""));
    invalid(root);
  }

  @Test
  public void buildsBundleContainingMapDocumentBytes() throws Exception {
    Path root = consistentProject();
    Path output = root.resolve("out.swb");
    AssetBuilder.build(root, output);
    assertTrue(Files.size(output) > 0);
    byte[] expected = Files.readAllBytes(root.resolve("maps/field.map.json"));
    assertEquals(MapDocument.parse(expected).id(), "world.field");
    assertTrue(Arrays.equals(expected, MapDocument.parse(expected).toJsonBytes()));
  }

  @Test
  public void createsNewMapAssetThroughCliAndRefusesDuplicates() throws Exception {
    Path root = project();
    registry(root, asset("tileset.terrain", "image", "images/terrain.png", ""));
    AssetCli.newMap(root, "world.new", 6, 4, 32, 32);
    AssetRegistry registry = AssetRegistry.load(root);
    registry.validate("world.new");
    MapDocument created =
        MapDocument.parse(Files.readAllBytes(root.resolve("maps/world.new.map.json")));
    assertEquals(6, created.width());
    assertEquals(4, created.height());
    assertEquals("world.new", created.id());
    try {
      AssetCli.newMap(root, "world.new", 6, 4, 32, 32);
      fail("duplicate map asset accepted");
    } catch (IOException expected) {
    }
  }
}
