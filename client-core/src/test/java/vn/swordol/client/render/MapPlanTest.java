package vn.swordol.client.render;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;

/**
 * Oracle: the ordered draw operations the independently verified Python preview produces for the
 * captured map1101400 (tools/analysis/preview_maps.py), whose layer order was corrected against
 * MapEx.drawCellA/draw and Entity.compareMapPos. Positions stay in the original downward pixel
 * space; flipping to GL coordinates is the renderer's job.
 */
public class MapPlanTest {
  @Test
  public void reproducesEveryDrawOfTheVerifiedPreviewInOrder() throws Exception {
    MapPlan.Plan plan = plan();
    assertEquals(6138, plan.draws().size());
    assertTrue(plan.missingImages().isEmpty());
    assertTrue(plan.warnings().isEmpty());
    StringBuilder text = new StringBuilder();
    for (MapPlan.Draw draw : plan.draws())
      text.append(draw.imageId())
          .append(' ')
          .append(draw.x())
          .append(' ')
          .append(draw.y())
          .append(' ')
          .append(draw.transform())
          .append('\n');
    assertEquals(
        "0e3575830596d3be7dda6bd044e0984d2cd622c1ef68392b75d550e2a2bf5b46",
        sha256(text.substring(0, text.length() - 1)));
  }

  @Test
  public void putsObjectTilesFirstThenLowDecorationThenRemainingStatics() throws Exception {
    List<MapPlan.Draw> draws = plan().draws();
    assertEquals(126, draws.get(0).imageId());
    assertEquals(0, draws.get(0).x());
    assertEquals(0, draws.get(0).y());
    assertEquals(120, draws.get(1).imageId());
    assertEquals(16, draws.get(1).x());
    assertEquals(368, draws.get(draws.size() - 1).imageId());
    assertEquals(9, draws.get(draws.size() - 1).x());
    assertEquals(1113, draws.get(draws.size() - 1).y());
    assertEquals(2, draws.get(draws.size() - 1).transform());
  }

  @Test
  public void mirrorsOffsetsAndTransformsTheClientAppliesPerPiece() throws Exception {
    MapReferences references = MapReferences.read(fixture("map-ref.bin"));
    MapPlan.Images images = sizes();
    // Reference235 is a plain static tile whose first piece is image175 at offset (-8,-16).
    WorldMap map = emptyGrid();
    map.applyTile(5, 5, 235, false);
    MapPlan.Draw plain = onlyPiece(MapPlan.build(map, references, images), 175);
    assertEquals(5 * 16 + 8 - 8, plain.x());
    assertEquals(5 * 16 + 16 - 16, plain.y());
    assertEquals(0, plain.transform());

    // The cell rotation bit is xored into the piece transform, which flips it and negates offset x.
    map.applyTile(5, 5, 235, true);
    MapPlan.Draw turned = onlyPiece(MapPlan.build(map, references, images), 175);
    assertEquals(5 * 16 + 8 + 8 - images.width(175), turned.x());
    assertEquals(plain.y(), turned.y());
    assertEquals(2, turned.transform());
  }

  @Test
  public void reportsUndefinedTilesAndMissingImagesWithoutFailing() throws Exception {
    WorldMap map = new WorldMap(MapResource.read(fixture("map-data.bin")));
    map.applyTile(1, 1, 250, false);
    MapPlan.Plan plan =
        MapPlan.build(
            map,
            MapReferences.read(fixture("map-ref.bin")),
            new MapPlan.Images() {
              @Override
              public int width(int id) {
                return id == 126 ? -1 : 16;
              }

              @Override
              public int height(int id) {
                return id == 126 ? -1 : 16;
              }
            });
    assertFalse(plan.warnings().isEmpty());
    assertTrue(plan.missingImages().contains(126));
    assertFalse(plan.draws().isEmpty());
  }

  @Test
  public void keepsTheGridAndItsUpdatesInsideTheOriginalWalkRules() throws Exception {
    WorldMap map = new WorldMap(MapResource.read(fixture("map-data.bin")));
    assertEquals(74, map.columns());
    assertEquals(74, map.rows());
    assertEquals(16, map.cellWidth());
    assertEquals(16, map.cellHeight());
    assertFalse("the original refuses column0", map.walkable(0, 5));
    assertFalse("the original refuses row0", map.walkable(5, 0));
    assertFalse(map.walkable(-1, 5));
    assertFalse(map.walkable(74, 5));
    int walkable = 0;
    for (int y = 0; y < map.rows(); y++)
      for (int x = 0; x < map.columns(); x++) if (map.walkable(x, y)) walkable++;
    assertEquals(3201, walkable);
    map.applyCell(10, 10, true, true);
    assertFalse(map.walkable(10, 10));
    map.applyCell(10, 10, false, false);
    assertTrue(map.walkable(10, 10));
    map.applyTile(10, 10, 99, true);
    assertEquals(99, map.tile(10, 10));
    assertTrue(map.rotated(10, 10));
  }

  /**
   * A grid of the captured map's shape whose cells are all empty, so one tile can be studied alone.
   */
  private static WorldMap emptyGrid() throws Exception {
    WorldMap map = new WorldMap(MapResource.read(fixture("map-data.bin")));
    for (int y = 0; y < map.rows(); y++)
      for (int x = 0; x < map.columns(); x++) map.applyTile(x, y, 255, false);
    return map;
  }

  private static MapPlan.Draw onlyPiece(MapPlan.Plan plan, int imageId) {
    MapPlan.Draw found = null;
    for (MapPlan.Draw draw : plan.draws())
      if (draw.imageId() == imageId) {
        assertNull("More than one draw for image " + imageId, found);
        found = draw;
      }
    assertNotNull("No draw for image " + imageId, found);
    return found;
  }

  private static MapPlan.Plan plan() throws Exception {
    return MapPlan.build(
        new WorldMap(MapResource.read(fixture("map-data.bin"))),
        MapReferences.read(fixture("map-ref.bin")),
        sizes());
  }

  static MapPlan.Images sizes() throws IOException {
    final Map<Integer, int[]> table = new HashMap<>();
    String text = new String(fixture("image-sizes.txt"), StandardCharsets.UTF_8);
    for (String line : text.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      String[] parts = trimmed.split(" ");
      table.put(
          Integer.parseInt(parts[0]),
          new int[] {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
    }
    return new MapPlan.Images() {
      @Override
      public int width(int id) {
        return table.containsKey(id) ? table.get(id)[0] : -1;
      }

      @Override
      public int height(int id) {
        return table.containsKey(id) ? table.get(id)[1] : -1;
      }
    };
  }

  public static byte[] fixture(String name) throws IOException {
    try (InputStream input = MapPlanTest.class.getResourceAsStream("/maps/" + name)) {
      assertNotNull("Missing fixture " + name, input);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read = input.read(buffer); read != -1; read = input.read(buffer))
        bytes.write(buffer, 0, read);
      return bytes.toByteArray();
    }
  }

  private static String sha256(String text) throws Exception {
    StringBuilder result = new StringBuilder();
    for (byte b :
        MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
      result.append(String.format("%02x", b));
    return result.toString();
  }
}
