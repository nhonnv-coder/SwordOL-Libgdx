package vn.swordol.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import vn.swordol.protocol.wire.MapReferences;

/**
 * Loads only the images one legacy map actually references, so a map costs its own tiles rather
 * than the whole decoded image set. Requires an OpenGL context. A source may return null for an
 * image it does not hold: the plan then reports it as missing instead of failing the map.
 */
public final class TileImages implements MapPlan.Images, Disposable {
  /** Returns the PNG bytes of a legacy image id, or null when this source does not hold it. */
  public interface Source {
    byte[] read(int imageId) throws IOException;
  }

  private final Map<Integer, TextureRegion> regions = new HashMap<>();
  private final List<Texture> textures = new ArrayList<>();
  private final List<Integer> unavailable = new ArrayList<>();

  private TileImages() {}

  public static TileImages load(WorldMap map, MapReferences references, Source source)
      throws IOException {
    TileImages images = new TileImages();
    boolean loaded = false;
    try {
      for (int imageId : referenced(map, references)) {
        byte[] bytes = source.read(imageId);
        if (bytes == null) {
          images.unavailable.add(imageId);
          continue;
        }
        Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
        Texture texture;
        try {
          texture = new Texture(pixels);
        } finally {
          pixels.dispose();
        }
        images.textures.add(texture);
        images.regions.put(imageId, new TextureRegion(texture));
      }
      loaded = true;
      return images;
    } finally {
      if (!loaded) images.dispose();
    }
  }

  /**
   * Image ids the map's current tiles reach, following animation references to their first frame.
   */
  public static List<Integer> referenced(WorldMap map, MapReferences references) {
    TreeSet<Integer> ids = new TreeSet<>();
    for (int y = 0; y < map.rows(); y++)
      for (int x = 0; x < map.columns(); x++) {
        int index = map.tile(x, y);
        if (index == WorldMap.EMPTY_TILE || !references.defines(index)) continue;
        collect(references, index, ids, 0);
      }
    return new ArrayList<>(ids);
  }

  private static void collect(
      MapReferences references, int index, TreeSet<Integer> ids, int depth) {
    if (depth > 32 || !references.defines(index)) return;
    MapReferences.Reference reference = references.get(index);
    if (reference.isAnimation()) {
      if (!reference.pieces().isEmpty())
        collect(references, reference.pieces().get(0).value(), ids, depth + 1);
      return;
    }
    for (MapReferences.Piece piece : reference.pieces()) ids.add(piece.value());
  }

  public TextureRegion region(int imageId) {
    return regions.get(imageId);
  }

  public List<Integer> unavailable() {
    return new ArrayList<>(unavailable);
  }

  public int count() {
    return regions.size();
  }

  @Override
  public int width(int imageId) {
    TextureRegion region = regions.get(imageId);
    return region == null ? -1 : region.getRegionWidth();
  }

  @Override
  public int height(int imageId) {
    TextureRegion region = regions.get(imageId);
    return region == null ? -1 : region.getRegionHeight();
  }

  @Override
  public void dispose() {
    for (Texture texture : textures) texture.dispose();
    textures.clear();
    regions.clear();
  }
}
