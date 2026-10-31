package vn.swordol.assets.pipeline;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import vn.swordol.assets.runtime.AssetBundle;

/**
 * Exports the {@code image} assets of a bundle into a libGDX TextureAtlas: one {@code <name>.atlas}
 * text file and packed {@code <name>N.png} pages. The client then loads a few page textures through
 * AssetManager instead of one texture per image. Pages use shelf packing and nearest filtering,
 * matching the original pixel art.
 *
 * <p>shortcut: shelf packing, not MaxRects — wastes some page area. Switch to libGDX TexturePacker
 * (gdx-tools) if page count or size becomes a problem.
 */
public final class AtlasExporter {
  private AtlasExporter() {}

  private static final int PAGE = 2048;
  private static final int PADDING = 2; // transparent gutter so neighbours never bleed

  /** A placed region: which page, where, and the source image. */
  private static final class Placed {
    final String name;
    final BufferedImage image;
    int page;
    int x;
    int y;

    Placed(String name, BufferedImage image) {
      this.name = name;
      this.image = image;
    }
  }

  /**
   * Writes {@code name.atlas} and its page PNGs into {@code outDir}.
   *
   * @return the files written, atlas first
   * @throws IOException if the bundle has no images, an asset is not a readable image, or one image
   *     is larger than a page
   */
  public static List<Path> export(Path bundleFile, Path outDir, String name) throws IOException {
    AssetBundle bundle;
    try (InputStream input = Files.newInputStream(bundleFile)) {
      bundle = AssetBundle.load(input);
    }

    List<Placed> placed = new ArrayList<>();
    for (String id : bundle.ids()) {
      if (!"image".equals(bundle.type(id))) {
        continue;
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bundle.read(id)));
      if (image == null) {
        throw new IOException("Asset is not a readable image: " + id);
      }
      if (image.getWidth() + PADDING > PAGE || image.getHeight() + PADDING > PAGE) {
        throw new IOException("Image larger than a " + PAGE + "px page: " + id);
      }
      placed.add(new Placed(id, image));
    }
    if (placed.isEmpty()) {
      throw new IOException("Bundle has no image assets: " + bundleFile);
    }

    // Tallest first packs shelves with the least wasted height; id breaks ties for a stable layout.
    placed.sort(
        Comparator.comparingInt((Placed p) -> p.image.getHeight())
            .reversed()
            .thenComparing(p -> p.name));

    List<int[]> pageSize = new ArrayList<>(); // {width, height} actually used per page
    int page = 0;
    int cursorX = 0;
    int shelfY = 0;
    int shelfHeight = 0;
    int usedWidth = 0;
    for (Placed p : placed) {
      int w = p.image.getWidth() + PADDING;
      int h = p.image.getHeight() + PADDING;
      if (cursorX + w > PAGE) { // next shelf
        cursorX = 0;
        shelfY += shelfHeight;
        shelfHeight = 0;
      }
      if (shelfY + h > PAGE) { // next page
        pageSize.add(new int[] {usedWidth, shelfY});
        page++;
        cursorX = 0;
        shelfY = 0;
        shelfHeight = 0;
        usedWidth = 0;
      }
      p.page = page;
      p.x = cursorX;
      p.y = shelfY;
      cursorX += w;
      shelfHeight = Math.max(shelfHeight, h);
      usedWidth = Math.max(usedWidth, cursorX);
    }
    pageSize.add(new int[] {usedWidth, shelfY + shelfHeight});

    Files.createDirectories(outDir);
    List<Path> written = new ArrayList<>();
    StringBuilder atlas = new StringBuilder();
    for (int i = 0; i < pageSize.size(); i++) {
      String pageFile = name + (i + 1) + ".png";
      BufferedImage canvas =
          new BufferedImage(pageSize.get(i)[0], pageSize.get(i)[1], BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = canvas.createGraphics();
      for (Placed p : placed) {
        if (p.page == i) {
          g.drawImage(p.image, p.x, p.y, null);
        }
      }
      g.dispose();
      Path pagePath = outDir.resolve(pageFile);
      ImageIO.write(canvas, "png", pagePath.toFile());
      written.add(pagePath);

      if (i > 0) {
        atlas.append('\n');
      }
      atlas
          .append(pageFile)
          .append('\n')
          .append("size: ")
          .append(pageSize.get(i)[0])
          .append(',')
          .append(pageSize.get(i)[1])
          .append('\n')
          .append("format: RGBA8888\n")
          .append("filter: Nearest,Nearest\n")
          .append("repeat: none\n");
      for (Placed p : placed) {
        if (p.page != i) {
          continue;
        }
        int w = p.image.getWidth();
        int h = p.image.getHeight();
        atlas
            .append(p.name)
            .append('\n')
            .append("  rotate: false\n")
            .append("  xy: ")
            .append(p.x)
            .append(", ")
            .append(p.y)
            .append('\n')
            .append("  size: ")
            .append(w)
            .append(", ")
            .append(h)
            .append('\n')
            .append("  orig: ")
            .append(w)
            .append(", ")
            .append(h)
            .append('\n')
            .append("  offset: 0, 0\n")
            .append("  index: -1\n");
      }
    }

    Path atlasPath = outDir.resolve(name + ".atlas");
    Files.write(atlasPath, atlas.toString().getBytes(StandardCharsets.UTF_8));
    written.add(0, atlasPath);
    return written;
  }
}
