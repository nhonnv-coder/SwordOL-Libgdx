package vn.swordol.assets.runtime;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

/** Offline verification against the actual Java loader and libGDX PNG decoder. */
public final class BundleProbe {
  private BundleProbe() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: BundleProbe BUNDLE REPORT.json");
    }
    Path source = Paths.get(args[0]);
    Path reportPath = Paths.get(args[1]);
    if (Files.exists(reportPath) && Files.isSameFile(source, reportPath)) {
      throw new IOException("Report must not overwrite the input bundle");
    }
    AssetBundle bundle;
    try (InputStream input = Files.newInputStream(source)) {
      bundle = AssetBundle.load(input);
    }
    GdxNativesLoader.load();
    Map<String, Integer> counts = new TreeMap<>();
    int images = 0;
    int documents = 0;
    int controls = 0;
    int frames = 0;
    int sixByteSprites = 0;
    long pixels = 0;
    for (String id : bundle.ids()) {
      String type = bundle.type(id);
      counts.put(type, counts.containsKey(type) ? counts.get(type) + 1 : 1);
      byte[] bytes = bundle.read(id);
      if ("image".equals(type)) {
        Pixmap image;
        try {
          image = new Pixmap(bytes, 0, bytes.length);
        } catch (RuntimeException ex) {
          throw new IOException("libGDX cannot decode " + id, ex);
        }
        try {
          if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Empty image " + id);
          }
          pixels += (long) image.getWidth() * image.getHeight();
          images++;
        } finally {
          image.dispose();
        }
      } else {
        // This checks JSON transport, not gameplay semantics or action bindings.
        JsonObject document = RuntimeJson.object(RuntimeJson.parse(bytes, id), id);
        documents++;
        if ("sprite".equals(type)) {
          frames += RuntimeJson.array(document.get("frames"), id + ".frames").size();
          if (RuntimeJson.integer(document.get("placement_size"), id + ".placement_size") == 6) {
            sixByteSprites++;
          }
        } else if ("ui".equals(type)) {
          controls += RuntimeJson.array(document.get("controls"), id + ".controls").size();
        }
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("bundle", source.toAbsolutePath().normalize().toString());
    report.addProperty("bundleSha256", RuntimeJson.sha256(Files.readAllBytes(source)));
    report.addProperty("assetsVerified", bundle.ids().size());
    report.add("types", new GsonBuilder().create().toJsonTree(counts));
    report.addProperty("imagesDecodedByLibgdx", images);
    report.addProperty("decodedPixels", pixels);
    report.addProperty("jsonDocumentsParsedStrictly", documents);
    report.addProperty("spriteFrames", frames);
    report.addProperty("sixBytePlacementSprites", sixByteSprites);
    report.addProperty("uiControls", controls);
    report.addProperty("scope", "Offline bundle integrity, strict JSON and native PNG decode; no sprite rendering, UI binding or server connection.");
    byte[] output = new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8);
    Files.write(reportPath, output);
    System.out.println(new String(output, StandardCharsets.UTF_8));
  }
}
