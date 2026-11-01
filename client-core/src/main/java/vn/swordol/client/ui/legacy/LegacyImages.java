package vn.swordol.client.ui.legacy;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.swordol.client.render.ImageSet;

/**
 * The pictures a window's controls draw, cut the way {@code ImagePointer} cuts them: one image per
 * legacy id, divided into equal cells whose width the id itself carries, with the cell an index
 * names handed back. Textures are shared between every control that names the same image.
 *
 * <p>An id no source holds is reported rather than substituted, so a window shows what the data
 * actually has instead of a stand-in that would look like the original's artwork.
 */
public final class LegacyImages implements Disposable {
  private final ImageSet source;
  private final Map<Integer, Texture> textures = new HashMap<>();
  private final List<Integer> missing = new ArrayList<>();

  public LegacyImages(ImageSet source) {
    this.source = source;
  }

  /** The cell this control names, or null when the image or the cell is not there. */
  public TextureRegion cell(int imageId, int imageIndex) {
    if (imageId <= 0) return null;
    Texture texture = texture(imageId);
    if (texture == null) return null;
    int width = LegacyImageCell.cellWidth(imageId, texture.getWidth());
    if (!LegacyImageCell.holds(imageIndex, width, texture.getWidth())) return null;
    return new TextureRegion(
        texture, LegacyImageCell.sourceX(imageIndex, width), 0, width, texture.getHeight());
  }

  public List<Integer> missingImages() {
    return new ArrayList<>(missing);
  }

  private Texture texture(int imageId) {
    if (textures.containsKey(imageId)) return textures.get(imageId);
    Texture loaded = null;
    try {
      byte[] bytes = source.read(imageId);
      if (bytes == null) {
        missing.add(imageId);
      } else {
        Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
        try {
          loaded = new Texture(pixels);
        } finally {
          pixels.dispose();
        }
      }
    } catch (IOException | RuntimeException ex) {
      missing.add(imageId);
    }
    textures.put(imageId, loaded);
    return loaded;
  }

  @Override
  public void dispose() {
    for (Texture texture : textures.values()) if (texture != null) texture.dispose();
    textures.clear();
  }
}
