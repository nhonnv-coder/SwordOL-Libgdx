package vn.swordol.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import vn.swordol.assets.runtime.MapData;

/**
 * Slices a map's tileset images into gid-indexed regions. Requires an OpenGL context, so the game
 * and the editor create it, and both get identical slicing rules.
 */
public final class TileSheets implements MapRenderer.Tiles, Disposable {
  /** Returns the bytes of an image asset, or null when the project does not contain it. */
  public interface Source {
    byte[] read(String assetId) throws IOException;
  }

  private final List<Texture> textures = new ArrayList<>();
  private final List<TextureRegion> regions = new ArrayList<>();

  private TileSheets() {}

  public static TileSheets load(MapData map, Source source) throws IOException {
    TileSheets sheets = new TileSheets();
    boolean loaded = false;
    try {
      for (MapData.Tileset tileset : map.tilesets()) {
        byte[] bytes = source.read(tileset.asset());
        if (bytes == null) {
          throw new IOException("Missing tileset image asset: " + tileset.asset());
        }
        Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
        Texture texture;
        try {
          texture = new Texture(pixels);
        } finally {
          pixels.dispose();
        }
        sheets.textures.add(texture);
        int rows = (tileset.tileCount() + tileset.columns() - 1) / tileset.columns();
        if (texture.getWidth() < tileset.columns() * map.tileWidth()
            || texture.getHeight() < rows * map.tileHeight()) {
          throw new IOException(
              "Tileset "
                  + tileset.name()
                  + " image is "
                  + texture.getWidth()
                  + "x"
                  + texture.getHeight()
                  + ", too small for "
                  + tileset.columns()
                  + "x"
                  + rows
                  + " tiles of "
                  + map.tileWidth()
                  + "x"
                  + map.tileHeight());
        }
        for (int index = 0; index < tileset.tileCount(); index++) {
          int column = index % tileset.columns();
          int row = index / tileset.columns();
          sheets.regions.add(
              new TextureRegion(
                  texture,
                  column * map.tileWidth(),
                  row * map.tileHeight(),
                  map.tileWidth(),
                  map.tileHeight()));
        }
      }
      loaded = true;
      return sheets;
    } finally {
      if (!loaded) {
        sheets.dispose();
      }
    }
  }

  /** Returns the region for a gid; gid 1 is the first tile of the first declared tileset. */
  @Override
  public TextureRegion region(int gid) {
    return gid < 1 || gid > regions.size() ? null : regions.get(gid - 1);
  }

  public int tileCount() {
    return regions.size();
  }

  @Override
  public void dispose() {
    for (Texture texture : textures) {
      texture.dispose();
    }
    textures.clear();
    regions.clear();
  }
}
