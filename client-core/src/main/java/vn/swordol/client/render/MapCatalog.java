package vn.swordol.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import vn.swordol.assets.runtime.AssetBundle;
import vn.swordol.assets.runtime.MapData;

/**
 * The maps and images the client ships, resolved by the asset IDs the server names. A map the
 * server reports with a different version is refused: drawing a world the server is not simulating
 * would mislead the player about where the walls are.
 */
public final class MapCatalog {
  private final AssetBundle bundle;
  private final Map<String, MapData> maps = new HashMap<>();

  private MapCatalog(AssetBundle bundle) {
    this.bundle = bundle;
  }

  /** The caller keeps ownership of {@code bundle}. */
  public static MapCatalog load(InputStream bundle) throws IOException {
    return new MapCatalog(AssetBundle.load(bundle));
  }

  /**
   * Returns a map of the bundle.
   *
   * @param serverVersion the version the server reported, or null when there is nothing to compare
   * @throws IOException if the ID is absent, is not a map, or was built from different bytes
   */
  public MapData require(String assetId, String serverVersion) throws IOException {
    MapData map = maps.get(assetId);
    if (map == null) {
      byte[] bytes = bundle.read(assetId);
      if (bytes == null || !"map".equals(bundle.type(assetId))) {
        throw new IOException("This client has no map asset " + assetId);
      }
      map = MapData.parse(bytes);
      maps.put(assetId, map);
    }
    if (serverVersion != null && !serverVersion.equals(map.version())) {
      throw new IOException(
          "Map "
              + assetId
              + " differs from the server: client "
              + map.version()
              + ", server "
              + serverVersion
              + ". Rebuild the asset bundle on both sides.");
    }
    return map;
  }

  /** Returns the bytes of an image asset, for building tile sheets. */
  public byte[] image(String assetId) throws IOException {
    byte[] bytes = bundle.read(assetId);
    if (bytes == null || !"image".equals(bundle.type(assetId))) {
      throw new IOException("This client has no image asset " + assetId);
    }
    return bytes;
  }
}
