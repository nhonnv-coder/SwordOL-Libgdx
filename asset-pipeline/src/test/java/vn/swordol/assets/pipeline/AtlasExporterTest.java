package vn.swordol.assets.pipeline;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Test;

public class AtlasExporterTest {

  @Test
  public void packsEveryImageAndPlacesItWhereTheAtlasSaysItIs() throws Exception {
    Path project = Files.createTempDirectory("atlas-project");
    writePng(project.resolve("red.png"), 4, 4, 0xFFFF0000);
    writePng(project.resolve("blue.png"), 8, 6, 0xFF0000FF);
    Files.write(
        project.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":["
                + image("img.red", "red.png")
                + ","
                + image("img.blue", "blue.png")
                + "]}")
            .getBytes(StandardCharsets.UTF_8));
    Path bundle = project.resolve("bundle.swb");
    AssetBuilder.build(project, bundle);

    Path out = Files.createTempDirectory("atlas-out");
    List<Path> written = AtlasExporter.export(bundle, out, "images");

    Path atlas = out.resolve("images.atlas");
    assertEquals(atlas, written.get(0));
    assertTrue(Files.exists(atlas));

    Map<String, Region> regions = parse(atlas);
    assertEquals(2, regions.size());
    assertEquals("4, 4", regions.get("img.red").size);
    assertEquals("8, 6", regions.get("img.blue").size);

    // The region's centre pixel on its page must be the source colour: proves packing + draw.
    assertEquals(0xFFFF0000, centrePixel(out, regions.get("img.red")));
    assertEquals(0xFF0000FF, centrePixel(out, regions.get("img.blue")));
  }

  @Test(expected = IOException.class)
  public void refusesABundleWithoutImages() throws Exception {
    Path project = Files.createTempDirectory("atlas-empty");
    Files.write(project.resolve("a.bin"), new byte[] {1, 2, 3});
    Files.write(
        project.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":[{\"id\":\"a\",\"type\":\"binary\",\"source\":\"a.bin\","
                + "\"dependencies\":[],\"provenance\":{}}]}")
            .getBytes(StandardCharsets.UTF_8));
    Path bundle = project.resolve("bundle.swb");
    AssetBuilder.build(project, bundle);
    AtlasExporter.export(bundle, Files.createTempDirectory("atlas-out"), "images");
  }

  private static String image(String id, String source) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"image\",\"source\":\""
        + source
        + "\",\"dependencies\":[],\"provenance\":{}}";
  }

  private static void writePng(Path path, int w, int h, int argb) throws IOException {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        image.setRGB(x, y, argb);
      }
    }
    ImageIO.write(image, "png", path.toFile());
  }

  private static final class Region {
    String page;
    int x;
    int y;
    int w;
    int h;
    String size;
  }

  private static Map<String, Region> parse(Path atlas) throws IOException {
    Map<String, Region> regions = new HashMap<>();
    String page = null;
    Region current = null;
    for (String line : Files.readAllLines(atlas)) {
      if (line.isEmpty()) {
        continue;
      }
      if (line.startsWith("  ")) {
        String body = line.trim();
        if (body.startsWith("xy:")) {
          String[] xy = body.substring(3).trim().split(",");
          current.x = Integer.parseInt(xy[0].trim());
          current.y = Integer.parseInt(xy[1].trim());
        } else if (body.startsWith("size:")) {
          current.size = body.substring(5).trim();
          String[] wh = current.size.split(",");
          current.w = Integer.parseInt(wh[0].trim());
          current.h = Integer.parseInt(wh[1].trim());
        }
      } else if (line.contains(":")) {
        // page property (size/format/filter/repeat); ignored here
      } else if (line.endsWith(".png")) {
        page = line;
      } else {
        current = new Region();
        current.page = page;
        regions.put(line, current);
      }
    }
    return regions;
  }

  private static int centrePixel(Path outDir, Region region) throws IOException {
    BufferedImage page = ImageIO.read(outDir.resolve(region.page).toFile());
    return page.getRGB(region.x + region.w / 2, region.y + region.h / 2);
  }
}
