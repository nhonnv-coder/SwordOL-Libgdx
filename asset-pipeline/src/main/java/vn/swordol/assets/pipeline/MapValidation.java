package vn.swordol.assets.pipeline;

import java.io.IOException;
import java.util.Map;

/**
 * Checks a map document against the other assets of its project: tileset images, portal targets,
 * spawn cells and the asset ID the document is registered under. A reference that cannot be
 * resolved is an error, never a silently dropped feature.
 *
 * <p>Checks that need the referenced bytes, such as whether a tileset image is large enough for the
 * tiles it declares, go through {@link ImageSource} so the editor and the registry run exactly the
 * same rules.
 */
public final class MapValidation {
  private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

  private MapValidation() {}

  /** Supplies the bytes of an image asset, or null when the project does not contain it. */
  public interface ImageSource {
    byte[] read(String assetId) throws IOException;
  }

  /**
   * @param assetId registry asset ID the document is registered under
   * @param mapsByAssetId every map document of the project, keyed by its asset ID
   * @param assetTypes declared registry type of every asset of the project, keyed by asset ID
   * @param images bytes of the project's image assets
   */
  public static void check(
      String assetId,
      MapDocument map,
      Map<String, MapDocument> mapsByAssetId,
      Map<String, String> assetTypes,
      ImageSource images)
      throws IOException {
    if (!assetId.equals(map.id())) {
      throw new IOException(
          "Map asset " + assetId + " contains document id " + map.id() + "; they must match");
    }
    for (MapDocument.Tileset tileset : map.tilesets()) {
      if (!"image".equals(assetTypes.get(tileset.asset()))) {
        throw new IOException(
            "Map "
                + assetId
                + " tileset "
                + tileset.name()
                + " must reference an image asset, not "
                + tileset.asset());
      }
      checkTilesetImage(assetId, map, tileset, images);
    }
    for (MapDocument.Spawn spawn : map.spawns()) {
      if (map.blocked(spawn.x(), spawn.y())) {
        throw new IOException(
            "Spawn "
                + spawn.id()
                + " of "
                + assetId
                + " stands on blocked cell "
                + spawn.x()
                + ","
                + spawn.y());
      }
    }
    for (MapDocument.Portal portal : map.portals()) {
      if (map.blocked(portal.x(), portal.y())) {
        throw new IOException(
            "Portal " + portal.id() + " of " + assetId + " stands on a blocked cell");
      }
      MapDocument target = mapsByAssetId.get(portal.targetMap());
      if (target == null || !"map".equals(assetTypes.get(portal.targetMap()))) {
        throw new IOException(
            "Portal "
                + portal.id()
                + " of "
                + assetId
                + " targets unknown map "
                + portal.targetMap());
      }
      if (portal.targetX() >= target.width() || portal.targetY() >= target.height()) {
        throw new IOException(
            "Portal "
                + portal.id()
                + " of "
                + assetId
                + " targets cell "
                + portal.targetX()
                + ","
                + portal.targetY()
                + " outside "
                + portal.targetMap());
      }
      if (target.blocked(portal.targetX(), portal.targetY())) {
        throw new IOException(
            "Portal "
                + portal.id()
                + " of "
                + assetId
                + " targets a blocked cell of "
                + portal.targetMap());
      }
    }
  }

  /**
   * Checks every map of a project against every other, so an edit to one map cannot break a portal
   * another map opens into it.
   */
  public static void checkProject(
      Map<String, MapDocument> mapsByAssetId, Map<String, String> assetTypes, ImageSource images)
      throws IOException {
    for (Map.Entry<String, MapDocument> entry : mapsByAssetId.entrySet()) {
      check(entry.getKey(), entry.getValue(), mapsByAssetId, assetTypes, images);
    }
  }

  private static void checkTilesetImage(
      String assetId, MapDocument map, MapDocument.Tileset tileset, ImageSource images)
      throws IOException {
    byte[] bytes = images == null ? null : images.read(tileset.asset());
    if (bytes == null) {
      throw new IOException(
          "Map "
              + assetId
              + " tileset "
              + tileset.name()
              + " has no readable image asset "
              + tileset.asset());
    }
    int[] size = pngSize(bytes);
    if (size == null) {
      throw new IOException(
          "Tileset image "
              + tileset.asset()
              + " of map "
              + assetId
              + " is not a PNG; tileset images must be PNG so their tile grid can be checked");
    }
    int rows = (tileset.tileCount() + tileset.columns() - 1) / tileset.columns();
    if (size[0] < tileset.columns() * map.tileWidth() || size[1] < rows * map.tileHeight()) {
      throw new IOException(
          "Tileset "
              + tileset.name()
              + " image is "
              + size[0]
              + "x"
              + size[1]
              + ", too small for "
              + tileset.columns()
              + "x"
              + rows
              + " tiles of "
              + map.tileWidth()
              + "x"
              + map.tileHeight());
    }
  }

  /** Returns {width, height} of a PNG, or null when the bytes are not a PNG with an IHDR. */
  static int[] pngSize(byte[] bytes) {
    if (bytes.length < 24) {
      return null;
    }
    for (int index = 0; index < PNG_SIGNATURE.length; index++) {
      if (bytes[index] != PNG_SIGNATURE[index]) {
        return null;
      }
    }
    if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
      return null;
    }
    int width = readInt(bytes, 16);
    int height = readInt(bytes, 20);
    if (width <= 0 || height <= 0) {
      return null;
    }
    return new int[] {width, height};
  }

  private static int readInt(byte[] bytes, int offset) {
    return ((bytes[offset] & 255) << 24)
        | ((bytes[offset + 1] & 255) << 16)
        | ((bytes[offset + 2] & 255) << 8)
        | (bytes[offset + 3] & 255);
  }
}
