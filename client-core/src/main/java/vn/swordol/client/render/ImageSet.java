package vn.swordol.client.render;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import vn.swordol.assets.runtime.AssetBundle;

/**
 * Resolves a legacy image id for the tile loader: first the images the current map downloaded from
 * the server, then the decoded original image set, which may be an asset bundle or a directory of
 * {@code <id>.png} files. Returns null for an image no source holds, so the draw plan reports it as
 * missing instead of failing the map. Read-only: nothing here writes to the asset locations.
 */
public final class ImageSet implements TileImages.Source {
  private final Map<Integer, byte[]> downloaded;
  private final AssetBundle bundle;
  private final File directory;

  private ImageSet(Map<Integer, byte[]> downloaded, AssetBundle bundle, File directory) {
    this.downloaded = downloaded;
    this.bundle = bundle;
    this.directory = directory;
  }

  /**
   * @param downloaded images this map sent, keyed by legacy image id; may be empty
   * @param location a {@code .swb} bundle or a directory of decoded PNGs; may be null
   */
  public static ImageSet open(Map<Integer, byte[]> downloaded, String location) throws IOException {
    Map<Integer, byte[]> local =
        downloaded == null ? Collections.<Integer, byte[]>emptyMap() : downloaded;
    if (location == null || location.trim().isEmpty()) return new ImageSet(local, null, null);
    File file = new File(location);
    if (file.isDirectory()) return new ImageSet(local, null, file);
    if (!file.isFile()) throw new IOException("Legacy image location not found: " + location);
    try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
      return new ImageSet(local, AssetBundle.load(input), null);
    }
  }

  @Override
  public byte[] read(int imageId) throws IOException {
    byte[] local = downloaded.get(imageId);
    if (local != null) return local;
    if (bundle != null) return bundle.read("legacy.image." + imageId);
    if (directory != null) {
      File file = new File(directory, imageId + ".png");
      return file.isFile() ? readFully(file) : null;
    }
    return null;
  }

  private static byte[] readFully(File file) throws IOException {
    byte[] bytes = new byte[(int) file.length()];
    try (InputStream input = new FileInputStream(file)) {
      int read = 0;
      while (read < bytes.length) {
        int step = input.read(bytes, read, bytes.length - read);
        if (step < 0) throw new IOException("Truncated file: " + file);
        read += step;
      }
    }
    return bytes;
  }

  /**
   * The decoded sprite document of this sprite id, or null when no source holds it. A bundle keeps
   * it under {@code legacy.sprite.<id>}; a directory of images has its sprites in the sibling
   * {@code legacy-sprites} directory the decoders write.
   */
  public byte[] readSprite(int spriteId) throws IOException {
    if (bundle != null) {
      return bundle.read("legacy.sprite." + spriteId);
    }
    if (directory != null) {
      File file =
          new File(directory.getParentFile(), "legacy-sprites/" + spriteId + ".sprite.json");
      return file.isFile() ? readFully(file) : null;
    }
    return null;
  }

  /**
   * The decoded layout of one original window, or null when no source holds it. A bundle keeps it
   * under {@code legacy.ui.<id>}; a directory of images has them in the sibling {@code legacy-ui}
   * directory the decoders write.
   */
  public byte[] readUi(int windowId) throws IOException {
    if (bundle != null) {
      return bundle.read("legacy.ui." + windowId);
    }
    if (directory != null) {
      File file = new File(directory.getParentFile(), "legacy-ui/" + windowId + ".ui.json");
      return file.isFile() ? readFully(file) : null;
    }
    return null;
  }

  public boolean hasOriginalSet() {
    return bundle != null || directory != null;
  }
}
