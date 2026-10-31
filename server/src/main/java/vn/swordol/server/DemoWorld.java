package vn.swordol.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import vn.swordol.assets.runtime.AssetBundle;
import vn.swordol.assets.runtime.MapData;

/**
 * The maps the demo server serves, read from the same asset bundle the client ships. Both sides
 * therefore run on identical bytes, and {@link MapData#version()} lets a client detect a server
 * built from a different bundle instead of silently drawing the wrong world.
 *
 * <p>Parsed maps are immutable and shared between sessions.
 */
public final class DemoWorld {
  public static final String START_MAP = "world.demo-field";
  private static final String RESOURCE = "/bundles/demo.swb";
  private final AssetBundle bundle;
  private final Map<String, MapData> maps = new HashMap<>();

  private DemoWorld(AssetBundle bundle) {
    this.bundle = bundle;
  }

  /** Loads the bundle shipped on the server classpath. */
  public static DemoWorld fromClasspath() throws IOException {
    try (InputStream input = DemoWorld.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IOException("Missing bundled assets at " + RESOURCE);
      }
      return load(input);
    }
  }

  /** Loads a bundle file, for running the server against a rebuilt asset project. */
  public static DemoWorld fromFile(Path bundleFile) throws IOException {
    try (InputStream input = Files.newInputStream(bundleFile)) {
      return load(input);
    }
  }

  /** The caller keeps ownership of {@code input}. */
  public static DemoWorld load(InputStream input) throws IOException {
    return new DemoWorld(AssetBundle.load(input));
  }

  /** Returns a map of the bundle; the result is immutable and may be shared. */
  public synchronized MapData map(String assetId) throws IOException {
    MapData cached = maps.get(assetId);
    if (cached != null) {
      return cached;
    }
    byte[] bytes = bundle.read(assetId);
    if (bytes == null || !"map".equals(bundle.type(assetId))) {
      throw new IOException("Bundle has no map asset " + assetId);
    }
    MapData parsed = MapData.parse(bytes);
    if (!assetId.equals(parsed.id())) {
      throw new IOException("Map asset " + assetId + " contains document id " + parsed.id());
    }
    maps.put(assetId, parsed);
    return parsed;
  }
}
