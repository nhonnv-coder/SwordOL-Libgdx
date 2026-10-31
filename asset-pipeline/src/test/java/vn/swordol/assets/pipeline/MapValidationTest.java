package vn.swordol.assets.pipeline;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MapValidationTest {
  private MapDocument field() throws IOException {
    MapDocument map = MapDocument.create("world.field", 4, 3, 32, 32);
    map.addTileset("terrain", "tileset.terrain", 2, 4);
    map.addPortal("to-town", 3, 2, "world.town", 1, 1);
    return map;
  }

  private MapDocument town() throws IOException {
    return MapDocument.create("world.town", 2, 2, 32, 32);
  }

  private Map<String, MapDocument> maps(MapDocument... documents) {
    Map<String, MapDocument> result = new HashMap<>();
    for (MapDocument document : documents) {
      result.put(document.id(), document);
    }
    return result;
  }

  private Map<String, String> types() {
    Map<String, String> result = new HashMap<>();
    result.put("tileset.terrain", "image");
    result.put("world.field", "map");
    result.put("world.town", "map");
    return result;
  }

  /** A 64 by 64 PNG header, enough for two columns of 32 pixel tiles. */
  private MapValidation.ImageSource images(final int width, final int height) {
    return new MapValidation.ImageSource() {
      @Override
      public byte[] read(String assetId) throws IOException {
        if (!"tileset.terrain".equals(assetId)) {
          return null;
        }
        return png(width, height);
      }
    };
  }

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

  private void rejects(
      String assetId, MapDocument map, Map<String, MapDocument> maps, Map<String, String> types) {
    rejects(assetId, map, maps, types, images(64, 64));
  }

  private void rejects(
      String assetId,
      MapDocument map,
      Map<String, MapDocument> maps,
      Map<String, String> types,
      MapValidation.ImageSource source) {
    try {
      MapValidation.check(assetId, map, maps, types, source);
      fail("accepted inconsistent map references");
    } catch (IOException expected) {
    }
  }

  @Test
  public void acceptsConsistentProject() throws Exception {
    MapDocument field = field();
    MapValidation.check("world.field", field, maps(field, town()), types(), images(64, 64));
  }

  @Test
  public void rejectsAssetIdThatDisagreesWithDocumentId() throws Exception {
    MapDocument field = field();
    rejects("world.other", field, maps(field, town()), types());
  }

  @Test
  public void rejectsPortalToUnknownMap() throws Exception {
    MapDocument field = field();
    rejects("world.field", field, maps(field), types());
  }

  @Test
  public void rejectsPortalTargetOutsideTargetMap() throws Exception {
    MapDocument field = MapDocument.create("world.field", 4, 3, 32, 32);
    field.addPortal("to-town", 3, 2, "world.town", 5, 0);
    rejects("world.field", field, maps(field, town()), types());
  }

  @Test
  public void rejectsPortalOntoBlockedTargetCell() throws Exception {
    MapDocument field = field();
    MapDocument town = town();
    town.setBlocked(1, 1, true);
    rejects("world.field", field, maps(field, town), types());
  }

  @Test
  public void rejectsPortalStandingOnBlockedSourceCell() throws Exception {
    MapDocument field = field();
    field.setBlocked(3, 2, true);
    rejects("world.field", field, maps(field, town()), types());
  }

  @Test
  public void rejectsTilesetAssetMissingOrNotAnImage() throws Exception {
    MapDocument field = field();
    Map<String, String> missing = types();
    missing.remove("tileset.terrain");
    rejects("world.field", field, maps(field, town()), missing);
    Map<String, String> wrongType = types();
    wrongType.put("tileset.terrain", "binary");
    rejects("world.field", field, maps(field, town()), wrongType);
  }

  @Test
  public void rejectsPortalTargetAssetThatIsNotAMap() throws Exception {
    MapDocument field = field();
    Map<String, String> wrongType = types();
    wrongType.put("world.town", "binary");
    rejects("world.field", field, maps(field, town()), wrongType);
  }

  @Test
  public void rejectsASpawnStandingOnABlockedCell() throws Exception {
    MapDocument field = field();
    field.addSpawn("hero", "player-start", "hero", 2, 2, 100);
    field.setBlocked(2, 2, true);
    rejects("world.field", field, maps(field, town()), types());
  }

  @Test
  public void acceptsASpawnOnAWalkableCell() throws Exception {
    MapDocument field = field();
    field.addSpawn("hero", "player-start", "hero", 2, 2, 100);
    MapValidation.check("world.field", field, maps(field, town()), types(), images(64, 64));
  }

  @Test
  public void rejectsATilesetImageTooSmallForItsDeclaredTiles() throws Exception {
    MapDocument field = field();
    rejects("world.field", field, maps(field, town()), types(), images(32, 64));
    rejects("world.field", field, maps(field, town()), types(), images(64, 16));
  }

  @Test
  public void rejectsATilesetImageThatIsNotAReadablePng() throws Exception {
    MapDocument field = field();
    rejects(
        "world.field",
        field,
        maps(field, town()),
        types(),
        new MapValidation.ImageSource() {
          @Override
          public byte[] read(String assetId) {
            return new byte[] {1, 2, 3};
          }
        });
  }
}
