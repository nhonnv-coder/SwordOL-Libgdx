package vn.swordol.assets.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** An immutable, validated in-memory bundle. No content is extracted to the filesystem. */
public final class AssetBundle {
  private static final int MAX_ASSETS = 10000;
  private static final int MAX_MANIFEST = 4 * 1024 * 1024;
  private static final int MAX_BLOB = 16 * 1024 * 1024;
  private static final int MAX_TOTAL = 64 * 1024 * 1024;
  private static final Set<String> TYPES =
      new HashSet<>(Arrays.asList("image", "map", "ui", "sprite", "font", "binary"));
  private final Map<String, Asset> assets;
  private final List<String> ids;

  private AssetBundle(Map<String, Asset> assets) {
    this.assets = Collections.unmodifiableMap(assets);
    this.ids = Collections.unmodifiableList(new ArrayList<>(assets.keySet()));
  }

  /**
   * Loads and verifies every entry, hash, size and dependency before returning. The caller retains
   * ownership of {@code input}, including on failure.
   *
   * @throws IOException if the ZIP, manifest, integrity checks or resource limits are invalid
   */
  public static AssetBundle load(InputStream input) throws IOException {
    if (input == null) {
      throw new IOException("Bundle input is null");
    }
    Map<String, byte[]> entries = new HashMap<>();
    long total = 0;
    // Closing the ZIP releases its inflater, while this wrapper preserves borrowed stream
    // ownership.
    try (ZipInputStream zip =
        new ZipInputStream(
            new FilterInputStream(input) {
              @Override
              public void close() {}
            })) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        if (entry.isDirectory()
            || !(name.equals("bundle.json") || name.matches("files/[0-9a-f]{64}"))) {
          throw new IOException("Invalid bundle entry path: " + name);
        }
        if (entries.containsKey(name)) {
          throw new IOException("Duplicate ZIP entry: " + name);
        }
        if (entries.size() >= MAX_ASSETS + 1) {
          throw new IOException("Too many ZIP entries");
        }
        int limit = name.equals("bundle.json") ? MAX_MANIFEST : MAX_BLOB;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = zip.read(buffer)) != -1) {
          total += count;
          if (total > MAX_TOTAL) {
            throw new IOException("Bundle exceeds 64 MiB decoded limit at " + name);
          }
          if ((long) bytes.size() + count > limit) {
            throw new IOException("Entry exceeds decoded limit: " + name);
          }
          bytes.write(buffer, 0, count);
        }
        entries.put(name, bytes.toByteArray());
        zip.closeEntry();
      }
    } catch (IllegalArgumentException ex) {
      throw new IOException("Invalid ZIP encoding", ex);
    }
    byte[] manifest = entries.remove("bundle.json");
    if (manifest == null) {
      throw new IOException("Missing bundle.json");
    }
    JsonObject root = object(parseManifest(manifest), "bundle.json");
    if (integer(root.get("schemaVersion"), "schemaVersion") != 1) {
      throw new IOException("Unsupported bundle schemaVersion");
    }
    JsonArray definitions = array(root.get("assets"), "assets");
    if (definitions.size() > MAX_ASSETS) {
      throw new IOException("Bundle exceeds 10000 assets");
    }
    Map<String, Asset> assets = new LinkedHashMap<>();
    Set<String> referenced = new HashSet<>();
    Set<String> verified = new HashSet<>();
    for (JsonElement definition : definitions) {
      JsonObject item = object(definition, "asset");
      String id = string(item.get("id"), "asset.id");
      if (id.trim().isEmpty()) {
        throw new IOException("Asset ID must not be empty");
      }
      if (assets.containsKey(id)) {
        throw new IOException("Duplicate asset ID: " + id);
      }
      String type = string(item.get("type"), id + ".type");
      if (!TYPES.contains(type)) {
        throw new IOException("Unknown asset type for " + id + ": " + type);
      }
      String path = string(item.get("path"), id + ".path");
      String hash = string(item.get("sha256"), id + ".sha256");
      if (!hash.matches("[0-9a-f]{64}") || !path.equals("files/" + hash)) {
        throw new IOException("Invalid content path/hash for " + id);
      }
      long size = integer(item.get("size"), id + ".size");
      if (size < 0 || size > MAX_BLOB) {
        throw new IOException("Invalid blob size for " + id);
      }
      object(item.get("provenance"), id + ".provenance");
      List<String> dependencies = new ArrayList<>();
      Set<String> uniqueDependencies = new HashSet<>();
      for (JsonElement dependency : array(item.get("dependencies"), id + ".dependencies")) {
        String dependencyId = string(dependency, id + ".dependency");
        if (!uniqueDependencies.add(dependencyId)) {
          throw new IOException("Duplicate dependency for " + id + ": " + dependencyId);
        }
        dependencies.add(dependencyId);
      }
      byte[] bytes = entries.get(path);
      if (bytes == null) {
        throw new IOException("Missing blob for " + id + ": " + path);
      }
      if (bytes.length != size) {
        throw new IOException("Blob size mismatch for " + id);
      }
      if (verified.add(path) && !sha256(bytes).equals(hash)) {
        throw new IOException("Blob hash mismatch for " + id);
      }
      referenced.add(path);
      assets.put(id, new Asset(type, bytes, dependencies));
    }
    if (!referenced.equals(entries.keySet())) {
      throw new IOException("Bundle contains unreferenced blob entries");
    }
    validateDependencies(assets);
    return new AssetBundle(assets);
  }

  /** Returns a defensive copy of asset bytes, or null if the ID is absent. */
  public byte[] read(String assetId) {
    Asset asset = assets.get(assetId);
    return asset == null ? null : asset.bytes.clone();
  }

  /** Returns the declared asset type, or null if the ID is absent. */
  public String type(String assetId) {
    Asset asset = assets.get(assetId);
    return asset == null ? null : asset.type;
  }

  /** Returns immutable IDs in manifest order. */
  public List<String> ids() {
    return ids;
  }

  private static JsonElement parseManifest(byte[] bytes) throws IOException {
    return RuntimeJson.parse(bytes, "bundle.json");
  }

  private static JsonObject object(JsonElement value, String context) throws IOException {
    return RuntimeJson.object(value, context);
  }

  private static JsonArray array(JsonElement value, String context) throws IOException {
    return RuntimeJson.array(value, context);
  }

  private static String string(JsonElement value, String context) throws IOException {
    return RuntimeJson.string(value, context);
  }

  private static long integer(JsonElement value, String context) throws IOException {
    return RuntimeJson.integer(value, context);
  }

  private static String sha256(byte[] bytes) {
    return RuntimeJson.sha256(bytes);
  }

  // Kahn's algorithm avoids recursive DFS exhausting the stack for a valid long chain.
  private static void validateDependencies(Map<String, Asset> assets) throws IOException {
    Map<String, Integer> remaining = new HashMap<>();
    Map<String, List<String>> dependents = new HashMap<>();
    ArrayDeque<String> ready = new ArrayDeque<>();
    for (Map.Entry<String, Asset> entry : assets.entrySet()) {
      remaining.put(entry.getKey(), entry.getValue().dependencies.size());
      if (entry.getValue().dependencies.isEmpty()) {
        ready.add(entry.getKey());
      }
      for (String dependency : entry.getValue().dependencies) {
        if (!assets.containsKey(dependency)) {
          throw new IOException("Missing dependency " + dependency + " for " + entry.getKey());
        }
        List<String> list = dependents.get(dependency);
        if (list == null) {
          list = new ArrayList<>();
          dependents.put(dependency, list);
        }
        list.add(entry.getKey());
      }
    }
    int visited = 0;
    while (!ready.isEmpty()) {
      String id = ready.remove();
      visited++;
      List<String> list = dependents.get(id);
      if (list == null) {
        continue;
      }
      for (String dependent : list) {
        int count = remaining.get(dependent) - 1;
        remaining.put(dependent, count);
        if (count == 0) {
          ready.add(dependent);
        }
      }
    }
    if (visited != assets.size()) {
      throw new IOException("Cyclic asset dependencies");
    }
  }

  private static final class Asset {
    private final String type;
    private final byte[] bytes;
    private final List<String> dependencies;

    private Asset(String type, byte[] bytes, List<String> dependencies) {
      this.type = type;
      this.bytes = bytes;
      this.dependencies = dependencies;
    }
  }
}
