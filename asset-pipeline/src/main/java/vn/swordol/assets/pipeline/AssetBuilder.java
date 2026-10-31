package vn.swordol.assets.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates deterministic content-addressed bundles and atomically publishes the completed ZIP. */
public final class AssetBuilder {
  /** How {@link #buildEach} splits a project into separate bundles. */
  public enum Grouping {
    /** One bundle per asset. */
    ASSET,
    /** One bundle per registry type. */
    TYPE,
    /** One bundle per caller-supplied asset id prefix. */
    PREFIX;

    String label() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  private AssetBuilder() {}

  /** Builds one bundle holding every asset of the project. */
  public static void build(Path projectRoot, Path outputFile) throws IOException {
    AssetRegistry registry = AssetRegistry.load(projectRoot);
    write(registry, projectRoot, outputFile, new TreeSet<>(registry.ids()));
  }

  /**
   * Builds one bundle holding these assets and everything they declare a dependency on, so the
   * bundle can be loaded on its own.
   */
  public static void build(Path projectRoot, Path outputFile, Collection<String> ids)
      throws IOException {
    AssetRegistry registry = AssetRegistry.load(projectRoot);
    write(registry, projectRoot, outputFile, selection(registry, ids));
  }

  /**
   * The assets a selection pulls into a bundle: the ids themselves and their dependency closure.
   */
  public static SortedSet<String> selection(Path projectRoot, Collection<String> ids)
      throws IOException {
    return selection(AssetRegistry.load(projectRoot), ids);
  }

  public static SortedSet<String> selection(AssetRegistry registry, Collection<String> ids)
      throws IOException {
    if (ids == null || ids.isEmpty()) {
      throw new IOException("Select at least one asset to build");
    }
    SortedSet<String> selected = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>();
    for (String id : ids) {
      registry.validate(id);
      pending.add(id);
    }
    while (!pending.isEmpty()) {
      String id = pending.removeFirst();
      if (!selected.add(id)) {
        continue;
      }
      // The registry refuses cycles and missing dependencies when it loads, so this terminates.
      pending.addAll(registry.dependenciesOf(id));
    }
    return selected;
  }

  /**
   * Writes one bundle per group into {@code outputDirectory} plus a {@code bundles.json} index.
   * {@code names} selects which groups to build: asset ids, registry types or id prefixes depending
   * on the grouping, or null for every group the project has. Returns the group names built.
   */
  public static List<String> buildEach(
      Path projectRoot, Path outputDirectory, Grouping grouping, Collection<String> names)
      throws IOException {
    AssetRegistry registry = AssetRegistry.load(projectRoot);
    Path directory = outputDirectory.toRealPath();
    if (!Files.isDirectory(directory)) {
      throw new IOException("Bundle output directory does not exist: " + outputDirectory);
    }
    Map<String, SortedSet<String>> groups = groups(registry, grouping, names);
    if (groups.isEmpty()) {
      throw new IOException("Nothing matched this grouping");
    }
    JsonObject index = new JsonObject();
    index.addProperty("schemaVersion", 1);
    index.addProperty("grouping", grouping.label());
    JsonArray bundles = new JsonArray();
    index.add("bundles", bundles);
    List<String> built = new ArrayList<>();
    for (Map.Entry<String, SortedSet<String>> group : groups.entrySet()) {
      String file = fileName(group.getKey());
      Path output = directory.resolve(file).normalize();
      if (!output.startsWith(directory)) {
        throw new IOException("Bundle name would leave the output directory: " + group.getKey());
      }
      write(registry, projectRoot, output, selection(registry, group.getValue()));
      byte[] bytes = Files.readAllBytes(output);
      JsonObject entry = new JsonObject();
      entry.addProperty("name", group.getKey());
      entry.addProperty("file", file);
      entry.addProperty("sha256", PipelineIO.hash(bytes));
      entry.addProperty("size", bytes.length);
      JsonArray assets = new JsonArray();
      for (String id : selection(registry, group.getValue())) {
        assets.add(id);
      }
      entry.add("assets", assets);
      bundles.add(entry);
      built.add(group.getKey());
    }
    Path indexFile = directory.resolve("bundles.json");
    Path temporary = Files.createTempFile(directory, ".bundles-", ".tmp");
    try {
      Files.write(temporary, PipelineIO.json(index));
      Files.move(
          temporary,
          indexFile,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return built;
  }

  private static Map<String, SortedSet<String>> groups(
      AssetRegistry registry, Grouping grouping, Collection<String> names) throws IOException {
    Set<String> wanted = names == null ? null : new TreeSet<>(names);
    if (wanted != null && wanted.isEmpty()) {
      throw new IOException("Select at least one group to build");
    }
    Map<String, SortedSet<String>> groups = new TreeMap<>();
    if (grouping == Grouping.PREFIX) {
      if (wanted == null) {
        throw new IOException("Prefix grouping needs the prefixes to build");
      }
      for (String prefix : wanted) {
        SortedSet<String> members = new TreeSet<>();
        for (String id : registry.ids()) {
          if (id.startsWith(prefix)) {
            members.add(id);
          }
        }
        if (members.isEmpty()) {
          throw new IOException("No asset starts with " + prefix);
        }
        groups.put(prefix, members);
      }
      return groups;
    }
    for (String id : registry.ids()) {
      String name = grouping == Grouping.ASSET ? id : registry.type(id);
      if (wanted != null && !wanted.contains(name)) {
        continue;
      }
      SortedSet<String> members = groups.get(name);
      if (members == null) {
        groups.put(name, members = new TreeSet<>());
      }
      members.add(id);
    }
    if (wanted != null) {
      for (String name : wanted) {
        if (!groups.containsKey(name)) {
          throw new IOException("Nothing to build for " + name);
        }
      }
    }
    return groups;
  }

  /** Asset ids are free-form, so a bundle file name has to be checked before it is used as one. */
  private static String fileName(String group) throws IOException {
    if (group.isEmpty() || group.length() > 120 || ".".equals(group) || "..".equals(group)) {
      throw new IOException("Unusable bundle name: " + group);
    }
    for (int index = 0; index < group.length(); index++) {
      char character = group.charAt(index);
      boolean allowed =
          (character >= 'a' && character <= 'z')
              || (character >= 'A' && character <= 'Z')
              || (character >= '0' && character <= '9')
              || character == '.'
              || character == '-'
              || character == '_';
      if (!allowed) {
        throw new IOException("Unusable bundle name: " + group);
      }
    }
    return group + ".swb";
  }

  private static void write(
      AssetRegistry registry, Path projectRoot, Path outputFile, Set<String> ids)
      throws IOException {
    Map<String, byte[]> blobs = new TreeMap<>();
    JsonObject manifest = new JsonObject();
    manifest.addProperty("schemaVersion", 1);
    JsonArray assets = new JsonArray();
    manifest.add("assets", assets);
    long total = 0;
    for (Map.Entry<String, JsonObject> entry : registry.entries().entrySet()) {
      if (!ids.contains(entry.getKey())) {
        continue;
      }
      JsonObject source = entry.getValue();
      byte[] bytes = registry.read(source);
      String hash = PipelineIO.hash(bytes);
      String path = "files/" + hash;
      if (!blobs.containsKey(path)) {
        total += bytes.length;
        if (total > PipelineIO.TOTAL_LIMIT) {
          throw new IOException("Bundle decoded byte limit exceeded");
        }
        blobs.put(path, bytes);
      }
      JsonObject asset = new JsonObject();
      asset.addProperty("id", entry.getKey());
      asset.addProperty("type", PipelineIO.string(source, "type"));
      asset.addProperty("path", path);
      asset.addProperty("sha256", hash);
      asset.addProperty("size", bytes.length);
      JsonArray dependencies = new JsonArray();
      for (String dependency : AssetRegistry.dependencies(source)) {
        dependencies.add(dependency);
      }
      asset.add("dependencies", dependencies);
      asset.add("provenance", source.get("provenance").deepCopy());
      assets.add(asset);
    }
    byte[] manifestBytes = PipelineIO.json(manifest);
    if (manifestBytes.length > PipelineIO.MANIFEST_LIMIT
        || total + manifestBytes.length > PipelineIO.TOTAL_LIMIT) {
      throw new IOException("Bundle manifest or total byte limit exceeded");
    }
    blobs.put("bundle.json", manifestBytes);
    Path output = outputFile.toAbsolutePath().normalize();
    if (Files.isSymbolicLink(output)) {
      throw new IOException("Bundle destination may not be a symbolic link");
    }
    for (JsonObject asset : registry.entries().values()) {
      Path source = PipelineIO.safe(projectRoot.toRealPath(), PipelineIO.string(asset, "source"));
      if (source.equals(output) || (Files.exists(output) && Files.isSameFile(source, output))) {
        throw new IOException("Bundle output would overwrite source");
      }
    }
    Path registryPath = projectRoot.toRealPath().resolve("registry.json");
    if (output.equals(registryPath)
        || (Files.exists(output) && Files.isSameFile(registryPath, output))) {
      throw new IOException("Bundle output would overwrite registry");
    }
    Path temporary = Files.createTempFile(output.getParent(), ".swb-", ".tmp");
    try {
      try (OutputStream stream = Files.newOutputStream(temporary);
          ZipOutputStream zip = new ZipOutputStream(stream)) {
        for (Map.Entry<String, byte[]> blob : blobs.entrySet()) {
          ZipEntry entry = new ZipEntry(blob.getKey());
          // ZIP stores a local DOS timestamp; keep its wall time constant in every zone.
          java.util.Calendar fixed =
              new java.util.GregorianCalendar(
                  java.util.TimeZone.getDefault(), java.util.Locale.ROOT);
          fixed.clear();
          fixed.set(2000, java.util.Calendar.JANUARY, 1, 0, 0, 0);
          entry.setTime(fixed.getTimeInMillis());
          entry.setMethod(ZipEntry.STORED);
          entry.setSize(blob.getValue().length);
          entry.setCompressedSize(blob.getValue().length);
          CRC32 crc = new CRC32();
          crc.update(blob.getValue());
          entry.setCrc(crc.getValue());
          zip.putNextEntry(entry);
          zip.write(blob.getValue());
          zip.closeEntry();
        }
      }
      Files.move(
          temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
