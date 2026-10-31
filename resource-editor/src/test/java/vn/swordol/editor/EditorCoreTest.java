package vn.swordol.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import vn.swordol.assets.pipeline.AssetRegistry;
import vn.swordol.assets.pipeline.MapDocument;

public class EditorCoreTest {
  private Path project() throws IOException {
    Path root = Files.createTempDirectory("editor-test");
    Files.createDirectory(root.resolve("images"));
    Files.createDirectory(root.resolve("maps"));
    Files.write(root.resolve("images/terrain.png"), png(64, 64));
    MapDocument field = MapDocument.create("world.field", 6, 4, 32, 32);
    field.addTileset("terrain", "tileset.terrain", 2, 4);
    Files.write(root.resolve("maps/field.map.json"), field.toJsonBytes());
    Files.write(
        root.resolve("maps/town.map.json"),
        MapDocument.create("world.town", 3, 3, 32, 32).toJsonBytes());
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
  public void listsAssetsByTypeAndRejectsUnknownId() throws Exception {
    EditorProject editor = EditorProject.open(project());
    assertEquals(Arrays.asList("world.field", "world.town"), editor.assets("map"));
    assertEquals(Arrays.asList("tileset.terrain"), editor.assets("image"));
    assertEquals(
        Arrays.asList("tileset.terrain", "world.field", "world.town"), editor.assets(null));
    try {
      editor.openMap("world.missing");
      fail("unknown asset accepted");
    } catch (IOException expected) {
    }
    try {
      editor.openMap("tileset.terrain");
      fail("non-map asset opened as map");
    } catch (IOException expected) {
    }
  }

  @Test
  public void createsSavesAndReopensAMap() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession session = editor.createMap("world.cave", 5, 3, 16, 16);
    assertEquals(Arrays.asList("world.cave", "world.field", "world.town"), editor.assets("map"));
    session.apply(new EditorCommands.SetBlocked(2, 1, true));
    session.apply(new EditorCommands.AddLayer("decor"));
    assertTrue(session.isDirty());
    editor.saveMap(session);
    assertFalse(session.isDirty());
    EditorProject reopened = EditorProject.open(root);
    MapEditSession again = reopened.openMap("world.cave");
    assertEquals(5, again.document().width());
    assertEquals(16, again.document().tileWidth());
    assertEquals(2, again.document().layerCount());
    assertTrue(again.document().blocked(2, 1));
    assertFalse(again.isDirty());
  }

  @Test
  public void undoAndRedoRestoreEveryCommandKind() throws Exception {
    EditorProject editor = EditorProject.open(project());
    MapEditSession session = editor.openMap("world.field");
    MapDocument map = session.document();
    byte[] original = map.toJsonBytes();
    List<EditorCommand> commands =
        Arrays.<EditorCommand>asList(
            new EditorCommands.PaintTile(0, 1, 1, 3),
            new EditorCommands.FlipTile(0, 1, 1, MapDocument.FLIP_H),
            new EditorCommands.SetBlocked(0, 0, true),
            new EditorCommands.AddLayer("decor"),
            new EditorCommands.AddSpawn("slime-1", "monster", "slime", 2, 2, 30),
            new EditorCommands.AddPortal("to-town", 5, 3, "world.town", 1, 1),
            new EditorCommands.MoveLayer(1, 0),
            new EditorCommands.RemoveLayer(0),
            new EditorCommands.RemoveSpawn("slime-1"),
            new EditorCommands.RemovePortal("to-town"));
    for (EditorCommand command : commands) {
      byte[] before = map.toJsonBytes();
      session.apply(command);
      byte[] after = map.toJsonBytes();
      assertFalse(command.describe(), Arrays.equals(before, after));
      session.undo();
      assertArrayEquals(command.describe(), before, map.toJsonBytes());
      session.redo();
      assertArrayEquals(command.describe(), after, map.toJsonBytes());
    }
    while (session.canUndo()) {
      session.undo();
    }
    assertArrayEquals(original, map.toJsonBytes());
    while (session.canRedo()) {
      session.redo();
    }
    assertEquals(0, map.spawns().size());
    assertEquals(0, map.portals().size());
    assertEquals(1, map.layerCount());
  }

  @Test
  public void undoBeyondStartAndRedoBeyondEndAreRefused() throws Exception {
    EditorProject editor = EditorProject.open(project());
    MapEditSession session = editor.openMap("world.field");
    assertFalse(session.canUndo());
    assertFalse(session.canRedo());
    try {
      session.undo();
      fail("undo without history accepted");
    } catch (IOException expected) {
    }
    session.apply(new EditorCommands.PaintTile(0, 0, 0, 1));
    session.undo();
    try {
      session.undo();
      fail("second undo accepted");
    } catch (IOException expected) {
    }
    session.redo();
    try {
      session.redo();
      fail("second redo accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void newEditDiscardsTheRedoBranch() throws Exception {
    EditorProject editor = EditorProject.open(project());
    MapEditSession session = editor.openMap("world.field");
    session.apply(new EditorCommands.PaintTile(0, 0, 0, 1));
    session.undo();
    session.apply(new EditorCommands.PaintTile(0, 1, 0, 2));
    assertFalse(session.canRedo());
    assertEquals(0, session.document().tile(0, 0, 0));
    assertEquals(2, session.document().tile(0, 1, 0));
  }

  @Test
  public void rejectedCommandLeavesDocumentAndHistoryUnchanged() throws Exception {
    EditorProject editor = EditorProject.open(project());
    MapEditSession session = editor.openMap("world.field");
    byte[] before = session.document().toJsonBytes();
    try {
      session.apply(new EditorCommands.PaintTile(0, 9, 9, 1));
      fail("out of bounds paint accepted");
    } catch (IOException expected) {
    }
    try {
      session.apply(new EditorCommands.PaintTile(0, 0, 0, 99));
      fail("gid beyond tileset accepted");
    } catch (IOException expected) {
    }
    assertArrayEquals(before, session.document().toJsonBytes());
    assertFalse(session.canUndo());
    assertFalse(session.isDirty());
  }

  @Test
  public void saveRejectsBrokenReferencesAndKeepsThePreviousFile() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession session = editor.openMap("world.field");
    byte[] onDisk = Files.readAllBytes(root.resolve("maps/field.map.json"));
    session.apply(new EditorCommands.AddPortal("nowhere", 1, 1, "world.missing", 0, 0));
    try {
      editor.saveMap(session);
      fail("portal to unknown map saved");
    } catch (IOException expected) {
    }
    assertArrayEquals(onDisk, Files.readAllBytes(root.resolve("maps/field.map.json")));
    assertTrue(session.isDirty());
    AssetRegistry.load(root);
  }

  @Test
  public void saveRecordsTilesetsAsDependenciesAndKeepsPortalLinksOutOfTheGraph() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession session = editor.openMap("world.field");
    session.apply(new EditorCommands.AddPortal("to-town", 5, 3, "world.town", 1, 1));
    editor.saveMap(session);
    AssetRegistry registry = AssetRegistry.load(root);
    assertEquals(Arrays.asList("tileset.terrain"), registry.dependenciesOf("world.field"));
    assertEquals(Arrays.asList("world.field"), registry.findUsages("tileset.terrain"));
    assertEquals(Arrays.asList("world.field"), registry.findUsages("world.town"));
  }

  @Test
  public void savesMutualPortalsBetweenTwoMaps() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession field = editor.openMap("world.field");
    field.apply(new EditorCommands.AddPortal("to-town", 5, 3, "world.town", 1, 1));
    editor.saveMap(field);
    MapEditSession town = editor.openMap("world.town");
    town.apply(new EditorCommands.AddPortal("to-field", 0, 0, "world.field", 0, 0));
    editor.saveMap(town);
    AssetRegistry.load(root);
    assertEquals(Arrays.asList("world.town"), EditorProject.open(root).assets("map").subList(1, 2));
  }

  @Test
  public void saveIsRefusedWhenItWouldBreakAnotherMapsPortal() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession town = editor.openMap("world.town");
    town.apply(new EditorCommands.AddPortal("to-field", 0, 0, "world.field", 2, 2));
    editor.saveMap(town);
    MapEditSession field = EditorProject.open(root).openMap("world.field");
    byte[] onDisk = Files.readAllBytes(root.resolve("maps/field.map.json"));
    field.apply(new EditorCommands.SetBlocked(2, 2, true));
    EditorProject second = EditorProject.open(root);
    MapEditSession editing = second.openMap("world.field");
    editing.apply(new EditorCommands.SetBlocked(2, 2, true));
    try {
      second.saveMap(editing);
      fail("blocking a cell another map opens a portal onto was saved");
    } catch (IOException expected) {
    }
    assertArrayEquals(onDisk, Files.readAllBytes(root.resolve("maps/field.map.json")));
    assertTrue(editing.isDirty());
    AssetRegistry.load(root);
    EditorProject.open(root).openMap("world.field");
  }

  @Test
  public void saveIsRefusedWhenASpawnWouldStandOnABlockedCell() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession session = editor.openMap("world.field");
    session.apply(new EditorCommands.AddSpawn("hero", "player-start", "hero", 1, 1, 100));
    editor.saveMap(session);
    byte[] onDisk = Files.readAllBytes(root.resolve("maps/field.map.json"));
    session.apply(new EditorCommands.SetBlocked(1, 1, true));
    try {
      editor.saveMap(session);
      fail("a spawn on a blocked cell was saved");
    } catch (IOException expected) {
    }
    assertArrayEquals(onDisk, Files.readAllBytes(root.resolve("maps/field.map.json")));
    assertTrue(session.isDirty());
  }

  @Test
  public void repeatedSaveWritesIdenticalBytes() throws Exception {
    Path root = project();
    EditorProject editor = EditorProject.open(root);
    MapEditSession session = editor.openMap("world.field");
    session.apply(new EditorCommands.PaintTile(0, 2, 2, 4));
    editor.saveMap(session);
    byte[] first = Files.readAllBytes(root.resolve("maps/field.map.json"));
    editor.saveMap(session);
    assertArrayEquals(first, Files.readAllBytes(root.resolve("maps/field.map.json")));
  }

  @Test
  public void createMapRefusesDuplicateAssetId() throws Exception {
    EditorProject editor = EditorProject.open(project());
    try {
      editor.createMap("world.field", 4, 4, 32, 32);
      fail("duplicate map asset accepted");
    } catch (IOException expected) {
    }
  }

  /** A PNG header large enough for the test tilesets; only the IHDR size is inspected. */
  static byte[] png(int width, int height) {
    byte[] header = new byte[33];
    byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    System.arraycopy(signature, 0, header, 0, signature.length);
    header[11] = 13;
    header[12] = 'I';
    header[13] = 'H';
    header[14] = 'D';
    header[15] = 'R';
    for (int index = 0; index < 4; index++) {
      header[16 + index] = (byte) (width >>> (24 - 8 * index));
      header[20 + index] = (byte) (height >>> (24 - 8 * index));
    }
    header[24] = 8;
    header[25] = 3;
    return header;
  }
}
