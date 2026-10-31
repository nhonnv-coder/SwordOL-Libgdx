package vn.swordol.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import vn.swordol.assets.runtime.SpriteData;

/**
 * One frame of a sprite action, turned into image blits the way ISpriteEx.paint does: every
 * placement names a module, the module names a rectangle of one image in the sprite's table, and a
 * slot the table empties draws nothing. Positions stay in the original's downward pixel space,
 * relative to the entity's own origin, so this can be checked without a window.
 */
public final class SpritePlan {
  private SpritePlan() {}

  /**
   * @param imageTable the ids {@link Look#imageTable} resolved for this entity
   * @param step frame of the action; it wraps, so a caller can keep ticking
   * @param mirrored true when the entity faces east, which the original draws mirrored
   */
  public static Frame build(
      SpriteData sprite, int[] imageTable, int actionId, int step, boolean mirrored) {
    int frames = sprite.actionFrameCount(actionId);
    int index = sprite.actionFrame(actionId, Math.floorMod(step, frames));
    List<Draw> draws = new ArrayList<>();
    for (SpriteData.Placement placement : sprite.placements(index)) {
      SpriteData.Module module = sprite.module(placement.moduleIndex());
      if (module.imageIndex() >= imageTable.length) {
        continue;
      }
      int imageId = imageTable[module.imageIndex()];
      if (imageId == 0) {
        continue;
      }
      int transform = placement.transform();
      int x = placement.x();
      if (mirrored) {
        x = Look.mirrorOffsetX(x, module.width(), module.height(), transform);
        transform = Look.mirrorTransform(transform);
      }
      draws.add(
          new Draw(
              imageId,
              module.x(),
              module.y(),
              module.width(),
              module.height(),
              x,
              placement.y(),
              transform));
    }
    return new Frame(index, Collections.unmodifiableList(draws));
  }

  public static final class Frame {
    private final int frameIndex;
    private final List<Draw> draws;

    Frame(int frameIndex, List<Draw> draws) {
      this.frameIndex = frameIndex;
      this.draws = draws;
    }

    public int frameIndex() {
      return frameIndex;
    }

    public List<Draw> draws() {
      return draws;
    }
  }

  /** One module blit: a rectangle of an image, placed relative to the entity in downward pixels. */
  public static final class Draw {
    private final int imageId, sourceX, sourceY, width, height, x, y, transform;

    Draw(
        int imageId, int sourceX, int sourceY, int width, int height, int x, int y, int transform) {
      this.imageId = imageId;
      this.sourceX = sourceX;
      this.sourceY = sourceY;
      this.width = width;
      this.height = height;
      this.x = x;
      this.y = y;
      this.transform = transform;
    }

    public int imageId() {
      return imageId;
    }

    public int sourceX() {
      return sourceX;
    }

    public int sourceY() {
      return sourceY;
    }

    public int width() {
      return width;
    }

    public int height() {
      return height;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int transform() {
      return transform;
    }
  }
}
