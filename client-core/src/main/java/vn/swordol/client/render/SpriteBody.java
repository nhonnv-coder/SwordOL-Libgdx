package vn.swordol.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.swordol.assets.runtime.SpriteData;

/**
 * One entity's body: the sprite the original would load for its look, the images that look resolves
 * to, and the frame it is showing. Requires an OpenGL context. The frame rate is the original's own
 * 50 ms tick; the decoded documents carry no per-frame duration, so nothing here invents one.
 */
public final class SpriteBody implements Disposable {
  private final SpriteData sprite;
  private final int[] imageTable;
  private final Map<Integer, Texture> textures = new HashMap<>();
  private final List<Integer> missing = new ArrayList<>();
  private long elapsed;
  private int step;

  private SpriteBody(SpriteData sprite, int[] imageTable) {
    this.sprite = sprite;
    this.imageTable = imageTable;
  }

  /**
   * Loads the body of this look, or returns null when no source holds its sprite. Only the images
   * the resolved table names are loaded, so an entity costs its own pictures and no more.
   */
  public static SpriteBody load(int lookFace, ImageSet images) throws IOException {
    int spriteId = Look.spriteId(lookFace);
    if (spriteId == 0) {
      return null;
    }
    byte[] document = images.readSprite(spriteId);
    if (document == null) {
      return null;
    }
    SpriteData sprite = SpriteData.read(document);
    int[] table = Look.imageTable(declaredTable(sprite), lookFace);
    SpriteBody body = new SpriteBody(sprite, table);
    boolean loaded = false;
    try {
      for (int imageId : table) {
        if (imageId == 0 || body.textures.containsKey(imageId)) {
          continue;
        }
        byte[] bytes = images.read(imageId);
        if (bytes == null) {
          body.missing.add(imageId);
          continue;
        }
        Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
        try {
          body.textures.put(imageId, new Texture(pixels));
        } finally {
          pixels.dispose();
        }
      }
      loaded = true;
      return body;
    } finally {
      if (!loaded) {
        body.dispose();
      }
    }
  }

  private static int[] declaredTable(SpriteData sprite) {
    int[] table = new int[sprite.imageCount()];
    for (int index = 0; index < table.length; index++) {
      table[index] = sprite.imageId(index);
    }
    return table;
  }

  /** Advances the animation on the original's tick and resets when the action changes. */
  public void tick(long deltaMillis, boolean animating) {
    if (!animating) {
      elapsed = 0;
      step = 0;
      return;
    }
    elapsed += Math.max(0, deltaMillis);
    while (elapsed >= Look.FRAME_MILLIS) {
      elapsed -= Look.FRAME_MILLIS;
      step++;
    }
  }

  public void resetAnimation() {
    elapsed = 0;
    step = 0;
  }

  public List<Integer> missingImages() {
    return new ArrayList<>(missing);
  }

  public SpriteData sprite() {
    return sprite;
  }

  /** The action the original would show, falling back the way getIspActionIndex does. */
  public int action(int actionIndex, int direction) {
    int wanted = Look.actionId(actionIndex, direction);
    if (sprite.hasAction(wanted)) {
      return wanted;
    }
    int standing = Look.actionId(0, direction);
    if (sprite.hasAction(standing)) {
      return standing;
    }
    return sprite.actionIds().get(0);
  }

  /**
   * Draws this body around the entity's own origin: the original places every blit relative to
   * (m_Left, m_Top), the top-left corner of the cell the entity stands on, so the caller passes
   * that point converted to GL coordinates.
   */
  public void draw(Batch batch, int actionIndex, int direction, float anchorX, float anchorY) {
    int action = action(actionIndex, direction);
    SpritePlan.Frame frame =
        SpritePlan.build(sprite, imageTable, action, step, Look.mirrored(direction));
    for (SpritePlan.Draw operation : frame.draws()) {
      Texture texture = textures.get(operation.imageId());
      if (texture == null
          || operation.sourceX() + operation.width() > texture.getWidth()
          || operation.sourceY() + operation.height() > texture.getHeight()) {
        continue;
      }
      TextureRegion region =
          new TextureRegion(
              texture,
              operation.sourceX(),
              operation.sourceY(),
              operation.width(),
              operation.height());
      float width = operation.width();
      float height = operation.height();
      // The original anchors each blit at its top-left in a downward space whose origin is the
      // entity's feet, so the GL quad sits that far below the foot line.
      float x = anchorX + operation.x();
      float y = anchorY - operation.y() - height;
      float scaleX = (operation.transform() & 2) != 0 ? -1 : 1;
      float scaleY = (operation.transform() & 1) != 0 ? -1 : 1;
      float rotation = 0;
      if ((operation.transform() & 4) != 0) {
        rotation = -90;
        scaleX = (operation.transform() & 1) != 0 ? 1 : -1;
        scaleY = (operation.transform() & 2) != 0 ? -1 : 1;
      }
      batch.draw(region, x, y, width / 2, height / 2, width, height, scaleX, scaleY, rotation);
    }
  }

  @Override
  public void dispose() {
    for (Texture texture : textures.values()) {
      texture.dispose();
    }
    textures.clear();
  }
}
