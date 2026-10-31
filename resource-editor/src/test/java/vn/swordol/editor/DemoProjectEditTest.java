package vn.swordol.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import org.junit.Assume;
import org.junit.Test;
import vn.swordol.assets.pipeline.AssetRegistry;
import vn.swordol.assets.pipeline.MapDocument;

/**
 * Acceptance on the real sample project: a copy of {@code assets/source/demo} is opened, edited,
 * saved and reopened. The copy keeps the repository project untouched while still proving the
 * editor works on the content the game actually ships.
 */
public class DemoProjectEditTest {
  private Path copyOfDemoProject() throws IOException {
    final Path source = Paths.get("..", "assets", "source", "demo").toAbsolutePath().normalize();
    Assume.assumeTrue("Sample project is present", Files.isDirectory(source));
    final Path destination = Files.createTempDirectory("demo-project-copy");
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
              throws IOException {
            Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.copy(file, destination.resolve(source.relativize(file).toString()));
            return FileVisitResult.CONTINUE;
          }
        });
    return destination;
  }

  @Test
  public void editsTheDemoFieldAndReopensIt() throws Exception {
    Path root = copyOfDemoProject();
    EditorController editor = new EditorController(EditorProject.open(root));
    assertTrue(editor.maps().containsAll(Arrays.asList("world.demo-field", "world.town")));
    editor.openMap("world.demo-field");
    byte[] before = editor.session().document().toJsonBytes();
    assertEquals(2, editor.session().document().layerCount());
    assertFalse(editor.session().document().blocked(5, 9));
    editor.selectLayer(1);
    editor.selectTile(4);
    editor.clickCell(5, 9);
    editor.selectTool(EditorController.Tool.COLLISION);
    editor.clickCell(5, 9);
    editor.addSpawn("slime-2", "monster", "slime", 12, 3, 20);
    assertTrue(editor.session().isDirty());
    editor.save();
    assertFalse(editor.session().isDirty());

    EditorController reopened = new EditorController(EditorProject.open(root));
    reopened.openMap("world.demo-field");
    MapDocument map = reopened.session().document();
    assertEquals(4, map.tile(1, 5, 9));
    assertTrue(map.blocked(5, 9));
    assertEquals(3, map.spawns().size());
    AssetRegistry registry = AssetRegistry.load(root);
    assertEquals(Arrays.asList("tileset.terrain"), registry.dependenciesOf("world.demo-field"));
    assertEquals(Arrays.asList("world.town"), registry.portalTargets("world.demo-field"));

    EditorController undoing = new EditorController(EditorProject.open(root));
    undoing.openMap("world.demo-field");
    undoing.removeSpawn("slime-2");
    undoing.selectTool(EditorController.Tool.COLLISION);
    undoing.clickCell(5, 9);
    undoing.selectLayer(1);
    undoing.selectTool(EditorController.Tool.ERASE);
    undoing.clickCell(5, 9);
    undoing.save();
    assertArrayEquals(before, undoing.session().document().toJsonBytes());
  }
}
