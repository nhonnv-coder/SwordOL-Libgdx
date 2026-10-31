package vn.swordol.assets.pipeline;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Validates registry metadata and project-contained source files; never migrates implicitly. */
public final class AssetRegistry {
  private final Path root;
  private final Map<String, JsonObject> assets;

  /** Map asset ID to the map assets its portals target; separate from the dependency graph. */
  private final Map<String, List<String>> portalLinks = new TreeMap<>();

  private AssetRegistry(Path root, Map<String, JsonObject> assets) {
    this.root = root;
    this.assets = assets;
  }

  public static AssetRegistry load(Path projectRoot) throws IOException {
    Path root = projectRoot.toRealPath();
    return parse(
        root,
        PipelineIO.parse(
            PipelineIO.read(PipelineIO.safe(root, "registry.json"), PipelineIO.MANIFEST_LIMIT)));
  }

  static AssetRegistry parse(Path root, JsonObject document) throws IOException {
    if (PipelineIO.integer(document, "schemaVersion") != 1) {
      throw new IOException(
          "Unsupported registry schemaVersion; use explicit migrate for schema 0");
    }
    JsonElement entries = document.get("assets");
    if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().size() > 10000) {
      throw new IOException("Invalid assets array or asset count limit");
    }
    Map<String, JsonObject> assets = new TreeMap<>();
    Map<String, String> paths = new HashMap<>();
    for (JsonElement entry : entries.getAsJsonArray()) {
      if (!entry.isJsonObject()) {
        throw new IOException("Asset must be an object");
      }
      JsonObject asset = entry.getAsJsonObject();
      String id = PipelineIO.string(asset, "id");
      if (id.length() > 256 || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
        throw new IOException("Invalid asset ID");
      }
      if (assets.put(id, asset.deepCopy()) != null) {
        throw new IOException("Duplicate asset ID");
      }
      String type = PipelineIO.string(asset, "type");
      if (!type.matches("image|map|ui|sprite|font|binary")) {
        throw new IOException("Unsupported asset type");
      }
      String source = PipelineIO.string(asset, "source");
      PipelineIO.safe(root, source);
      String previous = paths.put(source.toLowerCase(Locale.ROOT), source);
      if (previous != null && !previous.equals(source)) {
        throw new IOException("Case-insensitive source collision");
      }
      if (!asset.has("provenance") || !asset.get("provenance").isJsonObject()) {
        throw new IOException("Expected provenance object");
      }
      dependencies(asset);
    }
    AssetRegistry registry = new AssetRegistry(root, assets);
    registry.checkGraph();
    long total = 0;
    Set<String> hashes = new java.util.HashSet<>();
    for (JsonObject asset : assets.values()) {
      byte[] bytes = registry.read(asset);
      if (hashes.add(PipelineIO.hash(bytes))) {
        total += bytes.length;
      }
      if (total > PipelineIO.TOTAL_LIMIT) {
        throw new IOException("Project decoded byte limit exceeded");
      }
    }
    registry.checkMaps();
    return registry;
  }

  /**
   * Parses every {@code map} asset and resolves its tileset and portal references.
   *
   * <p>Tileset images must be declared as dependencies. Portal targets must not: two maps may link
   * to each other, which is an ordinary two-way door but would be a cycle in the dependency graph.
   * Portal links are kept in a separate relation and reported by {@link #findUsages}, so deleting a
   * linked map still shows where it is used.
   */
  private void checkMaps() throws IOException {
    Map<String, String> types = new TreeMap<>();
    Map<String, MapDocument> documents = new TreeMap<>();
    for (Map.Entry<String, JsonObject> entry : assets.entrySet()) {
      types.put(entry.getKey(), PipelineIO.string(entry.getValue(), "type"));
    }
    for (Map.Entry<String, JsonObject> entry : assets.entrySet()) {
      if ("map".equals(types.get(entry.getKey()))) {
        documents.put(entry.getKey(), MapDocument.parse(read(entry.getValue())));
      }
    }
    MapValidation.ImageSource images =
        new MapValidation.ImageSource() {
          @Override
          public byte[] read(String imageId) throws IOException {
            JsonObject asset = assets.get(imageId);
            return asset == null ? null : AssetRegistry.this.read(asset);
          }
        };
    for (Map.Entry<String, MapDocument> entry : documents.entrySet()) {
      MapDocument map = entry.getValue();
      MapValidation.check(entry.getKey(), map, documents, types, images);
      List<String> declared = dependencies(assets.get(entry.getKey()));
      for (MapDocument.Tileset tileset : map.tilesets()) {
        requireDependency(entry.getKey(), declared, tileset.asset(), "tileset");
      }
      List<String> targets = new ArrayList<>();
      for (MapDocument.Portal portal : map.portals()) {
        if (declared.contains(portal.targetMap())) {
          throw new IOException(
              "Map "
                  + entry.getKey()
                  + " declares portal target "
                  + portal.targetMap()
                  + " as a dependency; portal links are tracked separately so two maps can link to"
                  + " each other");
        }
        if (!targets.contains(portal.targetMap())) {
          targets.add(portal.targetMap());
        }
      }
      portalLinks.put(entry.getKey(), Collections.unmodifiableList(targets));
    }
  }

  private static void requireDependency(
      String assetId, List<String> declared, String referenced, String what) throws IOException {
    if (!declared.contains(referenced)) {
      throw new IOException(
          "Map "
              + assetId
              + " uses "
              + what
              + " "
              + referenced
              + " without declaring it as a dependency");
    }
  }

  private void checkGraph() throws IOException {
    Map<String, Integer> pending = new HashMap<>();
    Map<String, List<String>> users = new HashMap<>();
    ArrayDeque<String> ready = new ArrayDeque<>();
    for (Map.Entry<String, JsonObject> entry : assets.entrySet()) {
      List<String> dependencies = dependencies(entry.getValue());
      pending.put(entry.getKey(), dependencies.size());
      if (dependencies.isEmpty()) {
        ready.add(entry.getKey());
      }
      for (String dependency : dependencies) {
        if (!assets.containsKey(dependency)) {
          throw new IOException("Missing asset dependency");
        }
        if (!users.containsKey(dependency)) {
          users.put(dependency, new ArrayList<String>());
        }
        users.get(dependency).add(entry.getKey());
      }
    }
    int visited = 0;
    while (!ready.isEmpty()) {
      String id = ready.remove();
      visited++;
      for (String user : users.containsKey(id) ? users.get(id) : Collections.<String>emptyList()) {
        int count = pending.get(user) - 1;
        pending.put(user, count);
        if (count == 0) {
          ready.add(user);
        }
      }
    }
    if (visited != assets.size()) {
      throw new IOException("Cyclic asset dependency");
    }
  }

  static List<String> dependencies(JsonObject asset) throws IOException {
    JsonElement value = asset.get("dependencies");
    if (value == null || !value.isJsonArray()) {
      throw new IOException("Expected dependencies array");
    }
    Set<String> result = new TreeSet<>();
    for (JsonElement dependency : value.getAsJsonArray()) {
      if (!dependency.isJsonPrimitive()
          || !dependency.getAsJsonPrimitive().isString()
          || !result.add(dependency.getAsString())) {
        throw new IOException("Invalid or duplicate dependency");
      }
    }
    return new ArrayList<>(result);
  }

  public void validate(String assetId) throws IOException {
    JsonObject asset = assets.get(assetId);
    if (asset == null) {
      throw new IOException("Unknown asset ID");
    }
    read(asset);
  }

  /** Returns sorted assets that declare this asset as a dependency or target it with a portal. */
  public List<String> findUsages(String assetId) throws IOException {
    if (!assets.containsKey(assetId)) {
      throw new IOException("Unknown asset ID");
    }
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, JsonObject> entry : assets.entrySet()) {
      List<String> links = portalLinks.get(entry.getKey());
      if (dependencies(entry.getValue()).contains(assetId)
          || (links != null && links.contains(assetId))) {
        result.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /** Returns the map assets this map's portals target, sorted by first appearance. */
  public List<String> portalTargets(String assetId) throws IOException {
    require(assetId);
    List<String> links = portalLinks.get(assetId);
    return links == null ? Collections.<String>emptyList() : links;
  }

  /** Returns sorted asset IDs of the project. */
  public List<String> ids() {
    return Collections.unmodifiableList(new ArrayList<>(assets.keySet()));
  }

  /** Returns the declared registry type of an asset. */
  public String type(String assetId) throws IOException {
    return PipelineIO.string(require(assetId), "type");
  }

  /** Returns the project-relative source path of an asset. */
  public String source(String assetId) throws IOException {
    return PipelineIO.string(require(assetId), "source");
  }

  /** Returns the current bytes of an asset source file. */
  public byte[] bytes(String assetId) throws IOException {
    return read(require(assetId));
  }

  /** Returns the declared dependencies of an asset, sorted and deduplicated. */
  public List<String> dependenciesOf(String assetId) throws IOException {
    return dependencies(require(assetId));
  }

  private JsonObject require(String assetId) throws IOException {
    JsonObject asset = assets.get(assetId);
    if (asset == null) {
      throw new IOException("Unknown asset ID: " + assetId);
    }
    return asset;
  }

  /**
   * Replaces one asset's dependency list, validating the whole project before publishing the new
   * registry with an atomic move. The previous registry survives a rejected change.
   */
  public static void setDependencies(Path projectRoot, String assetId, List<String> dependencies)
      throws IOException {
    Path root = projectRoot.toRealPath();
    Path registryPath = PipelineIO.safe(root, "registry.json");
    JsonObject document =
        PipelineIO.parse(PipelineIO.read(registryPath, PipelineIO.MANIFEST_LIMIT));
    if (PipelineIO.integer(document, "schemaVersion") != 1) {
      throw new IOException("Expected registry schemaVersion 1");
    }
    JsonElement entries = document.get("assets");
    if (entries == null || !entries.isJsonArray()) {
      throw new IOException("Expected assets array");
    }
    boolean found = false;
    for (JsonElement element : entries.getAsJsonArray()) {
      JsonObject asset = element.isJsonObject() ? element.getAsJsonObject() : null;
      if (asset != null && assetId.equals(PipelineIO.string(asset, "id"))) {
        com.google.gson.JsonArray updated = new com.google.gson.JsonArray();
        for (String dependency : new TreeSet<>(dependencies)) {
          updated.add(dependency);
        }
        asset.add("dependencies", updated);
        found = true;
      }
    }
    if (!found) {
      throw new IOException("Unknown asset ID: " + assetId);
    }
    byte[] bytes = PipelineIO.json(document);
    if (bytes.length > PipelineIO.MANIFEST_LIMIT) {
      throw new IOException("Updated registry exceeds manifest limit");
    }
    parse(root, document);
    Path temporary = Files.createTempFile(root, ".registry-", ".tmp");
    try {
      Files.write(temporary, bytes);
      Files.move(
          temporary,
          registryPath,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  byte[] read(JsonObject asset) throws IOException {
    return PipelineIO.read(
        PipelineIO.safe(root, PipelineIO.string(asset, "source")), PipelineIO.BLOB_LIMIT);
  }

  Map<String, JsonObject> entries() {
    return assets;
  }
}
