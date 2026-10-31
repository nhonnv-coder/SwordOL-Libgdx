package vn.swordol.client.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;
import java.util.Objects;
import vn.swordol.protocol.wire.MapReferences;

/**
 * Draws a legacy map in GL from the plan MapPlan builds, keeping the original's paint order. The
 * plan is rebuilt only when the grid changes, so a cell update is cheap and a frame is a plain
 * batch loop. Knows nothing about the network, PureMVC or entities; the screen draws those on top.
 */
public final class WorldRenderer {
  private final WorldMap map;
  private final MapReferences references;
  private final TileImages images;
  private MapPlan.Plan plan;

  public WorldRenderer(WorldMap map, MapReferences references, TileImages images) {
    this.map = Objects.requireNonNull(map, "map");
    this.references = Objects.requireNonNull(references, "references");
    this.images = Objects.requireNonNull(images, "images");
    refresh();
  }

  /** Rebuilds the draw plan after the server changed tiles or cells. */
  public void refresh() {
    plan = MapPlan.build(map, references, images);
  }

  public MapPlan.Plan plan() {
    return plan;
  }

  public WorldMap map() {
    return map;
  }

  public List<String> warnings() {
    return plan.warnings();
  }

  public List<Integer> missingImages() {
    return plan.missingImages();
  }

  /** Draws the whole map with its left-bottom corner at the given GL position. */
  public void draw(Batch batch, float originX, float originY) {
    draw(batch, originX, originY, Float.NaN, Float.NaN);
  }

  /**
   * Draws the map with its left-bottom corner at the given GL position, skipping images outside a
   * view of this size at the GL origin. A map at the schema limit is far larger than any window, so
   * drawing every cell of every layer each frame is wasted work.
   */
  public void draw(Batch batch, float originX, float originY, float viewWidth, float viewHeight) {
    boolean cull = !Float.isNaN(viewWidth) && !Float.isNaN(viewHeight);
    for (MapPlan.Draw operation : plan.draws()) {
      TextureRegion region = images.region(operation.imageId());
      if (region == null) continue;
      float width = region.getRegionWidth(), height = region.getRegionHeight();
      float x = originX + operation.x();
      float y = originY + MapView.imageY(operation.y(), region.getRegionHeight(), map);
      // A transposed tile does not keep this bounding box, so it is never culled.
      if (cull
          && !operation.diagonal()
          && (x + width < 0 || y + height < 0 || x > viewWidth || y > viewHeight)) continue;
      float scaleX = operation.flipHorizontal() ? -1 : 1;
      // The original's vertical flip is around the image, and GL already draws it upside down
      // relative to the stored rows, so the flip applies on top of that single conversion.
      float scaleY = operation.flipVertical() ? -1 : 1;
      float rotation = 0;
      if (operation.diagonal()) {
        // Transposing variants do not occur in any captured map; kept so they draw as a transpose
        // rather than silently as an unflipped tile.
        rotation = -90;
        scaleX = operation.flipVertical() ? 1 : -1;
        scaleY = operation.flipHorizontal() ? -1 : 1;
      }
      batch.draw(region, x, y, width / 2, height / 2, width, height, scaleX, scaleY, rotation);
    }
  }
}
