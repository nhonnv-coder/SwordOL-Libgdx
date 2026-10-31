package vn.swordol.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import vn.swordol.assets.pipeline.MapDocument;

public class EditorControllerTest {
  private Path project() throws IOException {
    Path root = Files.createTempDirectory("controller-test");
    Files.createDirectory(root.resolve("images"));
    Files.createDirectory(root.resolve("maps"));
    Files.write(root.resolve("images/terrain.png"), png(64, 64));
    MapDocument field = MapDocument.create("world.field", 4, 3, 32, 32);
    field.addTileset("terrain", "tileset.terrain", 2, 4);
    Files.write(root.resolve("maps/field.map.json"), field.toJsonBytes());
    Files.write(
        root.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":["
                + "{\"id\":\"tileset.terrain\",\"type\":\"image\","
                + "\"source\":\"images/terrain.png\",\"dependencies\":[],\"provenance\":{}},"
                + "{\"id\":\"world.field\",\"type\":\"map\",\"source\":\"maps/field.map.json\","
                + "\"dependencies\":[\"tileset.terrain\"],\"provenance\":{}}]}")
            .getBytes(StandardCharsets.UTF_8));
    return root;
  }

  private EditorController controller(Path root) throws IOException {
    EditorController controller = new EditorController(EditorProject.open(root));
    controller.openMap("world.field");
    return controller;
  }

  @Test
  public void paintToolWritesTheSelectedTile() throws Exception {
    EditorController controller = controller(project());
    controller.selectTile(3);
    controller.clickCell(1, 2);
    assertEquals(3, controller.session().document().tile(0, 1, 2));
    assertTrue(controller.session().isDirty());
  }

  @Test
  public void eraseToolClearsACell() throws Exception {
    EditorController controller = controller(project());
    controller.selectTile(2);
    controller.clickCell(0, 0);
    controller.selectTool(EditorController.Tool.ERASE);
    controller.clickCell(0, 0);
    assertEquals(0, controller.session().document().tile(0, 0, 0));
  }

  @Test
  public void collisionToolTogglesTheCell() throws Exception {
    EditorController controller = controller(project());
    controller.selectTool(EditorController.Tool.COLLISION);
    controller.clickCell(2, 1);
    assertTrue(controller.session().document().blocked(2, 1));
    controller.clickCell(2, 1);
    assertFalse(controller.session().document().blocked(2, 1));
  }

  @Test
  public void flipToolsToggleTileBits() throws Exception {
    EditorController controller = controller(project());
    controller.selectTile(1);
    controller.clickCell(3, 0);
    controller.selectTool(EditorController.Tool.FLIP_H);
    controller.clickCell(3, 0);
    assertTrue(MapDocument.flipH(controller.session().document().tile(0, 3, 0)));
    controller.selectTool(EditorController.Tool.FLIP_D);
    controller.clickCell(3, 0);
    assertTrue(MapDocument.flipD(controller.session().document().tile(0, 3, 0)));
    controller.selectTool(EditorController.Tool.FLIP_H);
    controller.clickCell(3, 0);
    assertFalse(MapDocument.flipH(controller.session().document().tile(0, 3, 0)));
  }

  @Test
  public void paintsIntoTheSelectedLayer() throws Exception {
    EditorController controller = controller(project());
    controller.addLayer("decor");
    assertEquals(1, controller.selectedLayer());
    controller.selectTile(4);
    controller.clickCell(0, 1);
    assertEquals(4, controller.session().document().tile(1, 0, 1));
    assertEquals(0, controller.session().document().tile(0, 0, 1));
    controller.selectLayer(0);
    assertEquals(0, controller.selectedLayer());
  }

  @Test
  public void refusesWorkWithoutAnOpenMapAndOutsideTheMap() throws Exception {
    EditorController empty = new EditorController(EditorProject.open(project()));
    assertFalse(empty.hasMap());
    try {
      empty.clickCell(0, 0);
      fail("click without an open map accepted");
    } catch (IOException expected) {
    }
    EditorController controller = controller(project());
    try {
      controller.clickCell(9, 9);
      fail("click outside the map accepted");
    } catch (IOException expected) {
    }
    try {
      controller.selectLayer(5);
      fail("unknown layer selected");
    } catch (IOException expected) {
    }
    try {
      controller.selectTile(99);
      fail("tile beyond the tilesets selected");
    } catch (IOException expected) {
    }
  }

  @Test
  public void undoRedoAndSaveGoThroughTheSession() throws Exception {
    Path root = project();
    EditorController controller = controller(root);
    controller.selectTile(2);
    controller.clickCell(1, 1);
    controller.undo();
    assertEquals(0, controller.session().document().tile(0, 1, 1));
    controller.redo();
    assertEquals(2, controller.session().document().tile(0, 1, 1));
    controller.save();
    assertFalse(controller.session().isDirty());
    EditorController reopened = controller(root);
    assertEquals(2, reopened.session().document().tile(0, 1, 1));
  }

  @Test
  public void createMapOpensTheNewMapAndListsIt() throws Exception {
    EditorController controller = controller(project());
    controller.createMap("world.cave", 3, 3, 16, 16);
    assertEquals("world.cave", controller.session().assetId());
    assertTrue(controller.maps().contains("world.cave"));
    assertEquals(0, controller.selectedLayer());
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
