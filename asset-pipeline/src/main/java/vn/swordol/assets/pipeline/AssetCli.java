package vn.swordol.assets.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Headless registry commands. Invalid input prints one bounded message and exits nonzero. */
public final class AssetCli {
  private AssetCli() {}

  public static void main(String[] args) {
    try {
      if (args.length == 2 && "validate".equals(args[0])) {
        AssetRegistry.load(Paths.get(args[1]));
        System.out.println("Registry valid");
      } else if (args.length == 3 && "build".equals(args[0])) {
        AssetBuilder.build(Paths.get(args[1]), Paths.get(args[2]));
        System.out.println("Bundle built");
      } else if (args.length > 3 && "build".equals(args[0])) {
        java.util.List<String> ids = Arrays.asList(args).subList(3, args.length);
        AssetBuilder.build(Paths.get(args[1]), Paths.get(args[2]), ids);
        System.out.println(
            "Bundle built with "
                + AssetBuilder.selection(Paths.get(args[1]), ids).size()
                + " assets");
      } else if (args.length >= 4 && "split".equals(args[0])) {
        AssetBuilder.Grouping grouping = grouping(args[3]);
        java.util.List<String> names =
            args.length > 4 ? Arrays.asList(args).subList(4, args.length) : null;
        for (String built :
            AssetBuilder.buildEach(Paths.get(args[1]), Paths.get(args[2]), grouping, names)) {
          System.out.println(built);
        }
      } else if (args.length == 2 && "list".equals(args[0])) {
        list(Paths.get(args[1]));
      } else if (args.length == 3 && "usages".equals(args[0])) {
        for (String id : AssetRegistry.load(Paths.get(args[1])).findUsages(args[2])) {
          System.out.println(id);
        }
      } else if (args.length == 3 && "import-legacy".equals(args[0])) {
        importLegacy(Paths.get(args[1]), Paths.get(args[2]));
        System.out.println("Legacy project imported");
      } else if (args.length == 7 && "new-map".equals(args[0])) {
        newMap(
            Paths.get(args[1]),
            args[2],
            dimension(args[3]),
            dimension(args[4]),
            dimension(args[5]),
            dimension(args[6]));
        System.out.println("Map created and registered");
      } else if (args.length == 2 && "migrate".equals(args[0])) {
        migrate(Paths.get(args[1]));
        System.out.println("Registry migrated; schema 0 backup preserved");
      } else if (args.length == 4 && "atlas".equals(args[0])) {
        for (java.nio.file.Path written :
            AtlasExporter.export(Paths.get(args[1]), Paths.get(args[2]), args[3])) {
          System.out.println(written);
        }
      } else {
        throw new IOException(
            "Usage: validate PROJECT | build PROJECT OUTPUT [ID...] | list PROJECT"
                + " | split PROJECT OUTPUT_DIR asset|type|prefix [NAME...] | usages PROJECT ID"
                + " | import-legacy MANIFEST NEW_PROJECT | migrate PROJECT"
                + " | new-map PROJECT ID WIDTH HEIGHT TILE_WIDTH TILE_HEIGHT"
                + " | atlas BUNDLE OUTPUT_DIR NAME");
      }
    } catch (IOException | RuntimeException exception) {
      String message = exception.getMessage();
      if (message == null) {
        message = exception.getClass().getSimpleName();
      }
      message = message.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
      System.err.println(
          "Asset command failed: " + message.substring(0, Math.min(message.length(), 400)));
      System.exit(1);
    }
  }

  /** One line per asset: id, type, decoded size and the ids it depends on. */
  public static void list(Path projectRoot) throws IOException {
    AssetRegistry registry = AssetRegistry.load(projectRoot);
    for (String id : registry.ids()) {
      StringBuilder line = new StringBuilder(id);
      line.append('	').append(registry.type(id));
      line.append('	').append(registry.bytes(id).length);
      line.append('	');
      boolean first = true;
      for (String dependency : registry.dependenciesOf(id)) {
        if (!first) {
          line.append(',');
        }
        line.append(dependency);
        first = false;
      }
      System.out.println(line);
    }
  }

  private static AssetBuilder.Grouping grouping(String name) throws IOException {
    for (AssetBuilder.Grouping candidate : AssetBuilder.Grouping.values()) {
      if (candidate.name().equalsIgnoreCase(name)) {
        return candidate;
      }
    }
    throw new IOException("Grouping must be asset, type or prefix");
  }

  /** Creates an empty map document and registers it; refuses an ID or file that already exists. */
  public static void newMap(
      Path projectRoot, String assetId, int width, int height, int tileWidth, int tileHeight)
      throws IOException {
    Path root = projectRoot.toRealPath();
    Path registryPath = PipelineIO.safe(root, "registry.json");
    byte[] original = PipelineIO.read(registryPath, PipelineIO.MANIFEST_LIMIT);
    JsonObject document = PipelineIO.parse(original);
    if (PipelineIO.integer(document, "schemaVersion") != 1) {
      throw new IOException("Expected registry schemaVersion 1");
    }
    JsonElement entries = document.get("assets");
    if (entries == null || !entries.isJsonArray()) {
      throw new IOException("Expected assets array");
    }
    for (JsonElement element : entries.getAsJsonArray()) {
      if (element.isJsonObject()
          && assetId.equals(PipelineIO.string(element.getAsJsonObject(), "id"))) {
        throw new IOException("Asset ID already registered: " + assetId);
      }
    }
    MapDocument map = MapDocument.create(assetId, width, height, tileWidth, tileHeight);
    String relative = "maps/" + assetId + ".map.json";
    Path mapFile = root.resolve("maps").resolve(assetId + ".map.json");
    if (!mapFile.normalize().startsWith(root)) {
      throw new IOException("Map destination escapes the project");
    }
    Files.createDirectories(root.resolve("maps"));
    // CREATE_NEW keeps an existing document untouched when only the registry entry is missing.
    Files.write(mapFile, map.toJsonBytes(), java.nio.file.StandardOpenOption.CREATE_NEW);
    boolean registered = false;
    try {
      JsonObject asset = new JsonObject();
      asset.addProperty("id", assetId);
      asset.addProperty("type", "map");
      asset.addProperty("source", relative);
      asset.add("dependencies", new JsonArray());
      JsonObject provenance = new JsonObject();
      provenance.addProperty("createdBy", "asset-pipeline new-map");
      asset.add("provenance", provenance);
      entries.getAsJsonArray().add(asset);
      byte[] updated = PipelineIO.json(document);
      if (updated.length > PipelineIO.MANIFEST_LIMIT) {
        throw new IOException("Updated registry exceeds manifest limit");
      }
      AssetRegistry.parse(root, document);
      Path temporary = Files.createTempFile(root, ".registry-", ".tmp");
      try {
        Files.write(temporary, updated);
        Files.move(
            temporary,
            registryPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
      registered = true;
    } finally {
      if (!registered) {
        Files.deleteIfExists(mapFile);
      }
    }
  }

  private static int dimension(String value) throws IOException {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IOException("Expected a positive integer, got: " + value, exception);
    }
  }

  public static void migrate(Path projectRoot) throws IOException {
    Path root = projectRoot.toRealPath();
    Path registry = PipelineIO.safe(root, "registry.json");
    byte[] original = PipelineIO.read(registry, PipelineIO.MANIFEST_LIMIT);
    JsonObject document = PipelineIO.parse(original);
    if (PipelineIO.integer(document, "schemaVersion") != 0) {
      throw new IOException("Explicit migration accepts only schemaVersion 0");
    }
    JsonElement entries = document.get("assets");
    if (entries == null || !entries.isJsonArray()) {
      throw new IOException("Expected assets array");
    }
    for (JsonElement element : entries.getAsJsonArray()) {
      if (!element.isJsonObject()) {
        throw new IOException("Asset must be an object");
      }
      JsonObject asset = element.getAsJsonObject();
      if (asset.has("source")) {
        throw new IOException("Schema 0 asset already contains source");
      }
      String path = PipelineIO.string(asset, "path");
      asset.remove("path");
      asset.addProperty("source", path);
    }
    document.addProperty("schemaVersion", 1);
    AssetRegistry.parse(root, document);
    byte[] migrated = PipelineIO.json(document);
    if (migrated.length > PipelineIO.MANIFEST_LIMIT) {
      throw new IOException("Migrated registry exceeds manifest limit");
    }
    Path backup = root.resolve("registry.json.schema0.bak");
    // CREATE_NEW prevents a second migration from replacing the original backup.
    Files.write(backup, original, java.nio.file.StandardOpenOption.CREATE_NEW);
    Path temporary = Files.createTempFile(root, ".registry-", ".tmp");
    try {
      Files.write(temporary, migrated);
      Files.move(
          temporary, registry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static void importLegacy(Path manifestFile, Path newProject) throws IOException {
    Path manifest = manifestFile.toAbsolutePath().normalize();
    Path sourceRoot = manifest.getParent().toRealPath();
    JsonObject exported =
        PipelineIO.parse(
            PipelineIO.read(
                PipelineIO.safe(sourceRoot, manifest.getFileName().toString()),
                PipelineIO.MANIFEST_LIMIT));
    String sourceHash = PipelineIO.string(exported, "source_sha256");
    if (!sourceHash.matches("[0-9a-f]{64}")) {
      throw new IOException("Invalid source SHA-256");
    }
    String sourceName = PipelineIO.string(exported, "source");
    JsonElement members = exported.get("members");
    if (members == null || !members.isJsonArray() || members.getAsJsonArray().size() > 10000) {
      throw new IOException("Invalid exporter members or count limit");
    }
    Path destination = newProject.toAbsolutePath().normalize();
    if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Import destination already exists");
    }
    Path staging = Files.createTempDirectory(destination.getParent(), ".asset-import-");
    boolean published = false;
    try {
      Files.createDirectory(staging.resolve("files"));
      JsonObject registry = new JsonObject();
      registry.addProperty("schemaVersion", 1);
      JsonArray assets = new JsonArray();
      registry.add("assets", assets);
      Set<Long> ordinals = new HashSet<>();
      Set<String> paths = new HashSet<>();
      long total = 0;
      for (JsonElement element : members.getAsJsonArray()) {
        if (!element.isJsonObject()) {
          throw new IOException("Exporter member must be an object");
        }
        JsonObject member = element.getAsJsonObject();
        long ordinal = PipelineIO.integer(member, "archive_index");
        if (ordinal < 0 || !ordinals.add(ordinal)) {
          throw new IOException("Duplicate or invalid archive ordinal");
        }
        String exportedPath = PipelineIO.string(member, "exported_path");
        if (!paths.add(exportedPath.toLowerCase(Locale.ROOT))) {
          throw new IOException("Duplicate or case-colliding exported path");
        }
        byte[] bytes =
            PipelineIO.read(PipelineIO.safe(sourceRoot, exportedPath), PipelineIO.BLOB_LIMIT);
        total += bytes.length;
        if (total > PipelineIO.TOTAL_LIMIT) {
          throw new IOException("Import decoded byte limit exceeded");
        }
        String hash = PipelineIO.string(member, "sha256");
        if (!hash.matches("[0-9a-f]{64}")
            || !PipelineIO.hash(bytes).equals(hash)
            || PipelineIO.integer(member, "size") != bytes.length) {
          throw new IOException("Exporter member size or SHA-256 mismatch");
        }
        String relative =
            "files/"
                + ordinal
                + (exportedPath.toLowerCase(Locale.ROOT).endsWith(".png") ? ".png" : ".bin");
        Files.write(staging.resolve(relative), bytes);
        JsonObject asset = new JsonObject();
        asset.addProperty("id", "legacy." + ordinal);
        asset.addProperty("type", relative.endsWith(".png") ? "image" : "binary");
        asset.addProperty("source", relative);
        asset.add("dependencies", new JsonArray());
        JsonObject provenance = new JsonObject();
        provenance.addProperty("source", sourceName);
        provenance.addProperty("source_sha256", sourceHash);
        provenance.addProperty("archive_index", ordinal);
        provenance.addProperty("source_path", PipelineIO.string(member, "source_path"));
        provenance.addProperty("exported_path", exportedPath);
        provenance.addProperty("size", bytes.length);
        provenance.addProperty("sha256", hash);
        asset.add("provenance", provenance);
        assets.add(asset);
      }
      byte[] registryBytes = PipelineIO.json(registry);
      if (registryBytes.length > PipelineIO.MANIFEST_LIMIT) {
        throw new IOException("Imported registry exceeds manifest limit");
      }
      Files.write(staging.resolve("registry.json"), registryBytes);
      AssetRegistry.load(staging);
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      published = true;
    } finally {
      if (!published) {
        // Only files created by this import exist in this private staging directory.
        try (java.nio.file.DirectoryStream<Path> files =
            Files.newDirectoryStream(staging.resolve("files"))) {
          for (Path file : files) {
            Files.deleteIfExists(file);
          }
        }
        Files.deleteIfExists(staging.resolve("files"));
        Files.deleteIfExists(staging.resolve("registry.json"));
        Files.deleteIfExists(staging);
      }
    }
  }
}
