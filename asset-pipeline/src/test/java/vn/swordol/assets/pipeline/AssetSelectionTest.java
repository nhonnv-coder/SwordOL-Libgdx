package vn.swordol.assets.pipeline;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.Test;

/**
 * Building one bundle per asset, or per group, instead of one bundle for the whole project. A
 * selected asset must bring the assets it declares a dependency on, otherwise the bundle it
 * produces cannot be loaded on its own.
 */
public class AssetSelectionTest {
  @Test
  public void aSelectedAssetBringsTheAssetsItDependsOn() throws Exception {
    Path root = project();
    Path out = root.resolve("one.swb");
    AssetBuilder.build(root, out, Collections.singletonList("sprite"));
    assertEquals(Arrays.asList("image", "palette", "sprite"), bundled(out));
    assertEquals(3, AssetBuilder.selection(root, Collections.singletonList("sprite")).size());
  }

  @Test
  public void buildingTheSameSelectionTwiceProducesTheSameBytes() throws Exception {
    Path root = project();
    Path out = root.resolve("same.swb");
    AssetBuilder.build(root, out, Arrays.asList("sprite", "image"));
    byte[] first = Files.readAllBytes(out);
    AssetBuilder.build(root, out, Arrays.asList("image", "sprite"));
    assertArrayEquals("selection order must not change the bundle", first, Files.readAllBytes(out));
  }

  @Test
  public void anEmptyOrUnknownSelectionIsRefusedAndLeavesTheOldBundle() throws Exception {
    Path root = project();
    Path out = root.resolve("kept.swb");
    AssetBuilder.build(root, out, Collections.singletonList("image"));
    byte[] kept = Files.readAllBytes(out);
    refuse(() -> AssetBuilder.build(root, out, Collections.<String>emptyList()));
    refuse(() -> AssetBuilder.build(root, out, Collections.singletonList("missing")));
    refuse(() -> AssetBuilder.build(root, out, Arrays.asList("image", "missing")));
    assertArrayEquals(kept, Files.readAllBytes(out));
  }

  @Test
  public void buildsOneBundlePerAssetWithAnIndexOfWhatWentWhere() throws Exception {
    Path root = project();
    Path out = Files.createDirectory(root.resolve("each"));
    List<String> built = AssetBuilder.buildEach(root, out, AssetBuilder.Grouping.ASSET, null);
    assertEquals(Arrays.asList("font", "image", "palette", "sprite"), built);
    assertEquals(Arrays.asList("image", "palette", "sprite"), bundled(out.resolve("sprite.swb")));
    assertEquals(Collections.singletonList("image"), bundled(out.resolve("image.swb")));

    JsonObject index = PipelineIO.parse(Files.readAllBytes(out.resolve("bundles.json")));
    assertEquals(1, index.get("schemaVersion").getAsInt());
    assertEquals("asset", index.get("grouping").getAsString());
    assertEquals(4, index.getAsJsonArray("bundles").size());
    JsonObject first = index.getAsJsonArray("bundles").get(0).getAsJsonObject();
    assertEquals("font", first.get("name").getAsString());
    assertEquals("font.swb", first.get("file").getAsString());
    assertEquals(64, first.get("sha256").getAsString().length());
    assertTrue(first.get("size").getAsLong() > 0);
    // The index records what the bundle really holds: the asset plus its dependency.
    assertEquals(2, first.getAsJsonArray("assets").size());
  }

  @Test
  public void buildsOneBundlePerTypeAndPerIdPrefix() throws Exception {
    Path root = project();
    Path byType = Files.createDirectory(root.resolve("by-type"));
    assertEquals(
        Arrays.asList("binary", "font"),
        AssetBuilder.buildEach(root, byType, AssetBuilder.Grouping.TYPE, null));
    assertEquals(
        Arrays.asList("image", "palette", "sprite"), bundled(byType.resolve("binary.swb")));
    // The font's dependency is pulled in even though it is not a font itself.
    assertEquals(Arrays.asList("font", "image"), bundled(byType.resolve("font.swb")));

    Path byPrefix = Files.createDirectory(root.resolve("by-prefix"));
    AssetBuilder.buildEach(
        root, byPrefix, AssetBuilder.Grouping.PREFIX, Arrays.asList("sprite", "font"));
    assertEquals(
        Arrays.asList("image", "palette", "sprite"), bundled(byPrefix.resolve("sprite.swb")));
    assertFalse(Files.exists(byPrefix.resolve("image.swb")));
  }

  @Test
  public void refusesAnOutputNameThatCouldEscapeTheDirectory() throws Exception {
    Path root = Files.createTempDirectory("asset-escape");
    Files.write(root.resolve("a.bin"), new byte[] {7});
    Files.write(
        root.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":[{\"id\":\"..\\\\evil\",\"type\":\"binary\","
                + "\"source\":\"a.bin\",\"dependencies\":[],\"provenance\":{}}]}")
            .getBytes(StandardCharsets.UTF_8));
    Path out = Files.createDirectory(root.resolve("each"));
    refuse(() -> AssetBuilder.buildEach(root, out, AssetBuilder.Grouping.ASSET, null));
  }

  private static Path project() throws IOException {
    Path root = Files.createTempDirectory("asset-selection");
    Files.write(root.resolve("image.bin"), new byte[] {1, 2, 3});
    Files.write(root.resolve("palette.bin"), new byte[] {4, 5});
    Files.write(root.resolve("sprite.bin"), new byte[] {6, 7, 8, 9});
    Files.write(root.resolve("font.bin"), new byte[] {10});
    StringBuilder registry = new StringBuilder("{\"schemaVersion\":1,\"assets\":[");
    registry.append(asset("image", "binary", "image.bin", ""));
    registry.append(',').append(asset("palette", "binary", "palette.bin", ""));
    registry.append(',').append(asset("sprite", "binary", "sprite.bin", "\"image\",\"palette\""));
    registry.append(',').append(asset("font", "font", "font.bin", "\"image\""));
    registry.append("]}");
    Files.write(
        root.resolve("registry.json"), registry.toString().getBytes(StandardCharsets.UTF_8));
    return root;
  }

  private static String asset(String id, String type, String source, String dependencies) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\""
        + type
        + "\",\"source\":\""
        + source
        + "\",\"dependencies\":["
        + dependencies
        + "],\"provenance\":{}}";
  }

  private static List<String> bundled(Path bundle) throws IOException {
    try (ZipFile zip = new ZipFile(bundle.toFile())) {
      byte[] manifest = new byte[(int) zip.getEntry("bundle.json").getSize()];
      try (java.io.InputStream input = zip.getInputStream(zip.getEntry("bundle.json"))) {
        new java.io.DataInputStream(input).readFully(manifest);
      }
      List<String> ids = new java.util.ArrayList<>();
      for (com.google.gson.JsonElement element :
          PipelineIO.parse(manifest).getAsJsonArray("assets")) {
        ids.add(element.getAsJsonObject().get("id").getAsString());
      }
      Collections.sort(ids);
      return ids;
    }
  }

  private interface Build {
    void run() throws IOException;
  }

  private static void refuse(Build build) throws Exception {
    try {
      build.run();
      fail("Invalid selection accepted");
    } catch (IOException | IllegalArgumentException expected) {
    }
  }
}
