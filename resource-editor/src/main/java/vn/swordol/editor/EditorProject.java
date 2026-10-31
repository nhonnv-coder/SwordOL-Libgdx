package vn.swordol.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import vn.swordol.assets.pipeline.AssetCli;
import vn.swordol.assets.pipeline.AssetRegistry;
import vn.swordol.assets.pipeline.MapDocument;
import vn.swordol.assets.pipeline.MapValidation;

/**
 * An asset project opened for editing. The registry is the source of truth: every save validates
 * the whole project first, writes through a temporary file in the same directory and replaces the
 * previous file with an atomic move, so a rejected or failed save keeps the file that was there.
 */
public final class EditorProject {
  private final Path root;
  private AssetRegistry registry;
  private Map<String, String> types = new TreeMap<>();

  private EditorProject(Path root) throws IOException {
    this.root = root;
    refresh();
  }

  /** Opens and validates a project directory containing {@code registry.json}. */
  public static EditorProject open(Path projectRoot) throws IOException {
    return new EditorProject(projectRoot.toRealPath());
  }

  public Path root() {
    return root;
  }

  /** Returns sorted asset IDs, of one registry type or of every type when {@code type} is null. */
  public List<String> assets(String type) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, String> entry : types.entrySet()) {
      if (type == null || type.equals(entry.getValue())) {
        result.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /** Returns the registry type of an asset, or null when the ID is absent. */
  public String type(String assetId) {
    return types.get(assetId);
  }

  /** Returns the current bytes of any asset of the project. */
  public byte[] bytes(String assetId) throws IOException {
    return registry.bytes(assetId);
  }

  public MapEditSession openMap(String assetId) throws IOException {
    requireMap(assetId);
    return new MapEditSession(assetId, MapDocument.parse(registry.bytes(assetId)));
  }

  /** Creates an empty map, registers it and opens it for editing. */
  public MapEditSession createMap(
      String assetId, int width, int height, int tileWidth, int tileHeight) throws IOException {
    AssetCli.newMap(root, assetId, width, height, tileWidth, tileHeight);
    refresh();
    return openMap(assetId);
  }

  /**
   * Validates the edited document against the project, writes it atomically and records the assets
   * it references as its registry dependencies.
   */
  public void saveMap(MapEditSession session) throws IOException {
    String assetId = session.assetId();
    requireMap(assetId);
    MapDocument map = session.document();
    Map<String, MapDocument> documents = new TreeMap<>();
    for (String id : assets("map")) {
      documents.put(id, id.equals(assetId) ? map : MapDocument.parse(registry.bytes(id)));
    }
    // Every map is checked against the edited one, not just the edited one against the project:
    // blocking a cell another map opens a portal onto breaks that other map, and finding out
    // after the file was replaced would leave the project unloadable.
    MapValidation.checkProject(documents, types, imageSource());
    List<String> referenced = new ArrayList<>(new TreeSet<>(dependencyReferences(map)));
    Path file = root.resolve(registry.source(assetId));
    Path temporary = Files.createTempFile(file.getParent(), ".map-", ".tmp");
    try {
      Files.write(temporary, map.toJsonBytes());
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    if (!referenced.equals(registry.dependenciesOf(assetId))) {
      // The document is already saved; a failure here leaves stale dependencies that the next
      // save repairs, and that validation reports rather than hides.
      AssetRegistry.setDependencies(root, assetId, referenced);
    }
    refresh();
    session.markSaved();
  }

  /**
   * Assets a map declares as dependencies: its tileset images. Portal targets are deliberately not
   * dependencies, so two maps can link to each other without creating a cycle; the registry tracks
   * those links separately and still reports them as usages.
   */
  public static List<String> dependencyReferences(MapDocument map) {
    List<String> result = new ArrayList<>();
    for (MapDocument.Tileset tileset : map.tilesets()) {
      result.add(tileset.asset());
    }
    return result;
  }

  private MapValidation.ImageSource imageSource() {
    return new MapValidation.ImageSource() {
      @Override
      public byte[] read(String assetId) throws IOException {
        return "image".equals(types.get(assetId)) ? registry.bytes(assetId) : null;
      }
    };
  }

  private void requireMap(String assetId) throws IOException {
    if (!"map".equals(types.get(assetId))) {
      throw new IOException("Asset is not a registered map: " + assetId);
    }
  }

  private void refresh() throws IOException {
    AssetRegistry loaded = AssetRegistry.load(root);
    Map<String, String> loadedTypes = new TreeMap<>();
    for (String id : loaded.ids()) {
      loadedTypes.put(id, loaded.type(id));
    }
    registry = loaded;
    types = loadedTypes;
  }
}
