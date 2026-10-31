package vn.swordol.assets.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable finite orthogonal map read from bundle bytes, used by both the client renderer and
 * the server rules so neither can diverge from the other. Cell (0,0) is the bottom-left cell and
 * tiles are row major.
 *
 * <p>This reader intentionally repeats the producer's schema rules instead of depending on the
 * pipeline: the runtime must stay usable without the editor, and bundle content is untrusted. The
 * limits below are the documented contract; the producer must not exceed them.
 */
public final class MapData {
  public static final int SCHEMA_VERSION = 1;
  public static final int GID_MASK = 0x0FFFFFFF;
  public static final int FLIP_H = 1 << 28;
  public static final int FLIP_V = 1 << 29;
  public static final int FLIP_D = 1 << 30;
  private static final int MAX_DIMENSION = 512;
  private static final int MAX_CELLS = 262144;
  private static final int MAX_TILE_SIZE = 1024;
  private static final int MAX_LAYERS = 16;
  private static final int MAX_TILESETS = 8;
  private static final int MAX_TILES_PER_SET = 65536;
  private static final int MAX_SPAWNS = 4096;
  private static final int MAX_PORTALS = 1024;
  private static final int MAX_HP = 1000000;
  private static final String NAME_GRAMMAR = "[A-Za-z0-9][A-Za-z0-9._-]*";
  private static final Set<String> DOCUMENT_FIELDS =
      fields(
          "schemaVersion",
          "id",
          "orientation",
          "width",
          "height",
          "tileWidth",
          "tileHeight",
          "tilesets",
          "layers",
          "collision",
          "spawns",
          "portals");
  private static final Set<String> TILESET_FIELDS = fields("name", "asset", "columns", "tileCount");
  private static final Set<String> LAYER_FIELDS = fields("name", "tiles");
  private static final Set<String> SPAWN_FIELDS = fields("id", "kind", "template", "x", "y", "hp");
  private static final Set<String> PORTAL_FIELDS =
      fields("id", "x", "y", "targetMap", "targetX", "targetY");
  private static final Set<String> SPAWN_KINDS = fields("monster", "npc", "player-start");

  private final String id;
  private final int width;
  private final int height;
  private final int tileWidth;
  private final int tileHeight;
  private final List<Tileset> tilesets;
  private final List<String> layerNames;
  private final int[][] layerTiles;
  private final boolean[] collision;
  private final List<Spawn> spawns;
  private final List<Portal> portals;
  private final String version;

  private MapData(
      String id,
      int width,
      int height,
      int tileWidth,
      int tileHeight,
      List<Tileset> tilesets,
      List<String> layerNames,
      int[][] layerTiles,
      boolean[] collision,
      List<Spawn> spawns,
      List<Portal> portals,
      String version) {
    this.id = id;
    this.width = width;
    this.height = height;
    this.tileWidth = tileWidth;
    this.tileHeight = tileHeight;
    this.tilesets = Collections.unmodifiableList(tilesets);
    this.layerNames = Collections.unmodifiableList(layerNames);
    this.layerTiles = layerTiles;
    this.collision = collision;
    this.spawns = Collections.unmodifiableList(spawns);
    this.portals = Collections.unmodifiableList(portals);
    this.version = version;
  }

  private static Set<String> fields(String... names) {
    return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
  }

  /**
   * Validates and reads a map document.
   *
   * @throws IOException if the document is malformed, unsupported or outside the documented limits
   */
  public static MapData parse(byte[] bytes) throws IOException {
    if (bytes == null) {
      throw new IOException("Map bytes are null");
    }
    JsonObject root = RuntimeJson.object(RuntimeJson.parse(bytes, "map document"), "map document");
    for (String key : root.keySet()) {
      if (!DOCUMENT_FIELDS.contains(key)) {
        throw new IOException("Unsupported map document field: " + key);
      }
    }
    for (String key : DOCUMENT_FIELDS) {
      if (!root.has(key)) {
        throw new IOException("Missing map document field: " + key);
      }
    }
    if (RuntimeJson.integer(root.get("schemaVersion"), "schemaVersion") != SCHEMA_VERSION) {
      throw new IOException("Unsupported map schemaVersion; this build reads version 1");
    }
    String orientation = RuntimeJson.string(root.get("orientation"), "orientation");
    if (!"orthogonal".equals(orientation)) {
      throw new IOException("Unsupported map orientation: " + orientation);
    }
    String id = name(RuntimeJson.string(root.get("id"), "id"), "map id", 128);
    int width = (int) bounded(root, "width", 1, MAX_DIMENSION);
    int height = (int) bounded(root, "height", 1, MAX_DIMENSION);
    int tileWidth = (int) bounded(root, "tileWidth", 1, MAX_TILE_SIZE);
    int tileHeight = (int) bounded(root, "tileHeight", 1, MAX_TILE_SIZE);
    int cells = width * height;
    if (cells > MAX_CELLS) {
      throw new IOException("Map exceeds " + MAX_CELLS + " cells");
    }
    List<Tileset> tilesets = new ArrayList<>();
    Set<String> tilesetNames = new HashSet<>();
    int totalTiles = 0;
    for (JsonElement element : array(root, "tilesets", MAX_TILESETS)) {
      JsonObject tileset = RuntimeJson.object(element, "tileset");
      exact(tileset, TILESET_FIELDS, "tileset");
      String tilesetName =
          name(RuntimeJson.string(tileset.get("name"), "tileset name"), "tileset name", 64);
      if (!tilesetNames.add(tilesetName)) {
        throw new IOException("Duplicate tileset name: " + tilesetName);
      }
      int columns = (int) bounded(tileset, "columns", 1, MAX_TILES_PER_SET);
      int tileCount = (int) bounded(tileset, "tileCount", 1, MAX_TILES_PER_SET);
      tilesets.add(
          new Tileset(
              tilesetName,
              name(RuntimeJson.string(tileset.get("asset"), "tileset asset"), "tileset asset", 256),
              columns,
              tileCount,
              totalTiles + 1));
      totalTiles += tileCount;
    }
    JsonArray layerArray = array(root, "layers", MAX_LAYERS);
    if (layerArray.size() == 0) {
      throw new IOException("A map needs at least one tile layer");
    }
    List<String> layerNames = new ArrayList<>();
    int[][] layerTiles = new int[layerArray.size()][];
    for (int index = 0; index < layerArray.size(); index++) {
      JsonObject layer = RuntimeJson.object(layerArray.get(index), "layer");
      exact(layer, LAYER_FIELDS, "layer");
      String layerName =
          name(RuntimeJson.string(layer.get("name"), "layer name"), "layer name", 64);
      if (layerNames.contains(layerName)) {
        throw new IOException("Duplicate layer name: " + layerName);
      }
      layerNames.add(layerName);
      JsonArray tiles = array(layer, "tiles", MAX_CELLS);
      if (tiles.size() != cells) {
        throw new IOException(
            "Layer " + layerName + " has " + tiles.size() + " tiles, expected " + cells);
      }
      int[] words = new int[cells];
      for (int cell = 0; cell < cells; cell++) {
        long word = RuntimeJson.integer(tiles.get(cell), "tile word in layer " + layerName);
        if (word > Integer.MAX_VALUE) {
          throw new IOException("Tile word out of range in layer " + layerName);
        }
        if (gid((int) word) > totalTiles) {
          throw new IOException("Tile gid exceeds declared tileset tiles in layer " + layerName);
        }
        words[cell] = (int) word;
      }
      layerTiles[index] = words;
    }
    JsonArray collisionArray = array(root, "collision", MAX_CELLS);
    if (collisionArray.size() != cells) {
      throw new IOException("Collision has " + collisionArray.size() + " cells, expected " + cells);
    }
    boolean[] collision = new boolean[cells];
    for (int cell = 0; cell < cells; cell++) {
      long value = RuntimeJson.integer(collisionArray.get(cell), "collision cell");
      if (value != 0 && value != 1) {
        throw new IOException("Collision cells must be 0 or 1");
      }
      collision[cell] = value == 1;
    }
    List<Spawn> spawns = new ArrayList<>();
    Set<String> spawnIds = new HashSet<>();
    for (JsonElement element : array(root, "spawns", MAX_SPAWNS)) {
      JsonObject spawn = RuntimeJson.object(element, "spawn");
      exact(spawn, SPAWN_FIELDS, "spawn");
      String spawnId = name(RuntimeJson.string(spawn.get("id"), "spawn id"), "spawn id", 64);
      if (!spawnIds.add(spawnId)) {
        throw new IOException("Duplicate spawn ID: " + spawnId);
      }
      String kind = RuntimeJson.string(spawn.get("kind"), "spawn kind");
      if (!SPAWN_KINDS.contains(kind)) {
        throw new IOException("Unsupported spawn kind: " + kind);
      }
      int x = (int) bounded(spawn, "x", 0, width - 1);
      int y = (int) bounded(spawn, "y", 0, height - 1);
      spawns.add(
          new Spawn(
              spawnId,
              kind,
              name(
                  RuntimeJson.string(spawn.get("template"), "spawn template"),
                  "spawn template",
                  64),
              x,
              y,
              (int) bounded(spawn, "hp", 0, MAX_HP)));
    }
    List<Portal> portals = new ArrayList<>();
    Set<String> portalIds = new HashSet<>();
    Set<Integer> portalCells = new HashSet<>();
    for (JsonElement element : array(root, "portals", MAX_PORTALS)) {
      JsonObject portal = RuntimeJson.object(element, "portal");
      exact(portal, PORTAL_FIELDS, "portal");
      String portalId = name(RuntimeJson.string(portal.get("id"), "portal id"), "portal id", 64);
      if (!portalIds.add(portalId)) {
        throw new IOException("Duplicate portal ID: " + portalId);
      }
      int x = (int) bounded(portal, "x", 0, width - 1);
      int y = (int) bounded(portal, "y", 0, height - 1);
      if (!portalCells.add(y * width + x)) {
        throw new IOException("Two portals share cell " + x + "," + y);
      }
      portals.add(
          new Portal(
              portalId,
              x,
              y,
              name(
                  RuntimeJson.string(portal.get("targetMap"), "portal targetMap"),
                  "portal targetMap",
                  128),
              (int) bounded(portal, "targetX", 0, MAX_DIMENSION - 1),
              (int) bounded(portal, "targetY", 0, MAX_DIMENSION - 1)));
    }
    return new MapData(
        id,
        width,
        height,
        tileWidth,
        tileHeight,
        tilesets,
        layerNames,
        layerTiles,
        collision,
        spawns,
        portals,
        RuntimeJson.sha256(bytes));
  }

  public static int gid(int tileWord) {
    return tileWord & GID_MASK;
  }

  public static boolean flipH(int tileWord) {
    return (tileWord & FLIP_H) != 0;
  }

  public static boolean flipV(int tileWord) {
    return (tileWord & FLIP_V) != 0;
  }

  public static boolean flipD(int tileWord) {
    return (tileWord & FLIP_D) != 0;
  }

  public String id() {
    return id;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int tileWidth() {
    return tileWidth;
  }

  public int tileHeight() {
    return tileHeight;
  }

  public int layerCount() {
    return layerNames.size();
  }

  public String layerName(int layer) {
    return layerNames.get(layer);
  }

  public int tileWord(int layer, int x, int y) {
    if (layer < 0 || layer >= layerTiles.length) {
      throw new IndexOutOfBoundsException("Unknown layer index: " + layer);
    }
    return layerTiles[layer][cell(x, y)];
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < width && y >= 0 && y < height;
  }

  /**
   * @throws IndexOutOfBoundsException if the cell is outside the map; callers check {@link
   *     #inBounds} first rather than relying on a safe-looking default
   */
  public boolean isBlocked(int x, int y) {
    return collision[cell(x, y)];
  }

  public List<Tileset> tilesets() {
    return tilesets;
  }

  public List<Spawn> spawns() {
    return spawns;
  }

  public List<Portal> portals() {
    return portals;
  }

  /** Returns the portal standing on a cell, or null when there is none. */
  public Portal portalAt(int x, int y) {
    for (Portal portal : portals) {
      if (portal.x == x && portal.y == y) {
        return portal;
      }
    }
    return null;
  }

  /** Lowercase SHA-256 of the exact bytes this map was parsed from. */
  public String version() {
    return version;
  }

  private int cell(int x, int y) {
    if (!inBounds(x, y)) {
      throw new IndexOutOfBoundsException(
          "Cell " + x + "," + y + " is outside " + width + "x" + height);
    }
    return y * width + x;
  }

  private static JsonArray array(JsonObject object, String key, int limit) throws IOException {
    JsonArray array = RuntimeJson.array(object.get(key), key);
    if (array.size() > limit) {
      throw new IOException("Field " + key + " exceeds " + limit + " entries");
    }
    return array;
  }

  private static void exact(JsonObject object, Set<String> allowed, String what)
      throws IOException {
    for (String key : object.keySet()) {
      if (!allowed.contains(key)) {
        throw new IOException("Unsupported " + what + " field: " + key);
      }
    }
    for (String key : allowed) {
      if (!object.has(key)) {
        throw new IOException("Missing " + what + " field: " + key);
      }
    }
  }

  private static long bounded(JsonObject object, String key, long minimum, long maximum)
      throws IOException {
    long value = RuntimeJson.integer(object.get(key), key);
    if (value < minimum || value > maximum) {
      throw new IOException("Field " + key + " must be " + minimum + ".." + maximum);
    }
    return value;
  }

  private static String name(String value, String what, int limit) throws IOException {
    if (value.length() > limit || !value.matches(NAME_GRAMMAR)) {
      throw new IOException("Invalid " + what + ": " + value);
    }
    return value;
  }

  /** A tileset reference; {@code firstGid} follows declaration order. */
  public static final class Tileset {
    private final String name;
    private final String asset;
    private final int columns;
    private final int tileCount;
    private final int firstGid;

    private Tileset(String name, String asset, int columns, int tileCount, int firstGid) {
      this.name = name;
      this.asset = asset;
      this.columns = columns;
      this.tileCount = tileCount;
      this.firstGid = firstGid;
    }

    public String name() {
      return name;
    }

    public String asset() {
      return asset;
    }

    public int columns() {
      return columns;
    }

    public int tileCount() {
      return tileCount;
    }

    public int firstGid() {
      return firstGid;
    }
  }

  /** A spawn point for the demo server rules. */
  public static final class Spawn {
    private final String id;
    private final String kind;
    private final String template;
    private final int x;
    private final int y;
    private final int hp;

    private Spawn(String id, String kind, String template, int x, int y, int hp) {
      this.id = id;
      this.kind = kind;
      this.template = template;
      this.x = x;
      this.y = y;
      this.hp = hp;
    }

    public String id() {
      return id;
    }

    public String kind() {
      return kind;
    }

    public String template() {
      return template;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int hp() {
      return hp;
    }
  }

  /** A transfer cell pointing at a cell of another map asset. */
  public static final class Portal {
    private final String id;
    private final int x;
    private final int y;
    private final String targetMap;
    private final int targetX;
    private final int targetY;

    private Portal(String id, int x, int y, String targetMap, int targetX, int targetY) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.targetMap = targetMap;
      this.targetX = targetX;
      this.targetY = targetY;
    }

    public String id() {
      return id;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public String targetMap() {
      return targetMap;
    }

    public int targetX() {
      return targetX;
    }

    public int targetY() {
      return targetY;
    }
  }
}
