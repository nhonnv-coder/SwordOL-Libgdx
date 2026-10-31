package vn.swordol.client.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vn.swordol.assets.runtime.MapData;

/**
 * Draws a map document the same way for the game and for the editor preview, so what an author sees
 * while editing is what the client shows. Cell (0,0) is drawn at the bottom-left of the given
 * origin.
 */
public final class MapRenderer {
  /** Supplies the tile image for a gid, or null when the gid has no image. */
  public interface Tiles {
    TextureRegion region(int gid);
  }

  private MapRenderer() {}

  public static void draw(
      Batch batch,
      MapData map,
      Tiles tiles,
      float originX,
      float originY,
      float cellWidth,
      float cellHeight) {
    for (int layer = 0; layer < map.layerCount(); layer++) {
      for (int y = 0; y < map.height(); y++) {
        for (int x = 0; x < map.width(); x++) {
          int word = map.tileWord(layer, x, y);
          int gid = MapData.gid(word);
          if (gid == 0) {
            continue;
          }
          TextureRegion region = tiles.region(gid);
          if (region == null) {
            continue;
          }
          boolean flipH = MapData.flipH(word);
          boolean flipV = MapData.flipV(word);
          float scaleX = flipH ? -1 : 1;
          float scaleY = flipV ? -1 : 1;
          float rotation = 0;
          if (MapData.flipD(word)) {
            // The diagonal flip transposes the tile: a clockwise quarter turn after a horizontal
            // flip, with the other two flips folded through the rotation.
            rotation = -90;
            scaleX = flipV ? 1 : -1;
            scaleY = flipH ? -1 : 1;
          }
          batch.draw(
              region,
              originX + x * cellWidth,
              originY + y * cellHeight,
              cellWidth / 2,
              cellHeight / 2,
              cellWidth,
              cellHeight,
              scaleX,
              scaleY,
              rotation);
        }
      }
    }
  }
}
