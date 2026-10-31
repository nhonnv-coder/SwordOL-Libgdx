package vn.swordol.assets.pipeline;

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
 * An editable finite orthogonal map: tile layers for display plus collision, spawns and portals for
 * server rules, in one document. Cell (0,0) is the bottom-left cell and {@code tiles} is row major,
 * matching the client's Scene2D coordinates.
 *
 * <p>Mutators validate before changing state, so a rejected edit leaves the document untouched.
 * This type is not thread safe.
 */
public final class MapDocument {
  public static final int SCHEMA_VERSION = 1;
  public static final int GID_MASK = 0x0FFFFFFF;
  public static final int FLIP_H = 1 << 28;
  public static final int FLIP_V = 1 << 29;
  public static final int FLIP_D = 1 << 30;
  static final int MAX_DIMENSION = 512;
  static final int MAX_CELLS = 262144;
  static final int MAX_TILE_SIZE = 1024;
  static final int MAX_LAYERS = 16;
  static final int MAX_TILESETS = 8;
  static final int MAX_TILES_PER_SET = 65536;
  static final int MAX_SPAWNS = 4096;
  static final int MAX_PORTALS = 1024;
  static final int MAX_HP = 1000000;
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
  private final List<Tileset> tilesets = new ArrayList<>();
  private final List<Layer> layers = new ArrayList<>();
  private final boolean[] collision;
  private final List<Spawn> spawns = new ArrayList<>();
  private final List<Portal> portals = new ArrayList<>();

  private MapDocument(String id, int width, int height, int tileWidth, int tileHeight) {
    this.id = id;
    this.width = width;
    this.height = height;
    this.tileWidth = tileWidth;
    this.tileHeight = tileHeight;
    this.collision = new boolean[width * height];
  }

  private static Set<String> fields(String... names) {
    return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
  }

  /** Creates an empty map with one tile layer named {@code ground} and no blocked cells. */
  public static MapDocument create(String id, int width, int height, int tileWidth, int tileHeight)
      throws IOException {
    name(id, "map id", 128);
    geometry(width, height, tileWidth, tileHeight);
    MapDocument map = new MapDocument(id, width, height, tileWidth, tileHeight);
    map.layers.add(new Layer("ground", new int[width * height]));
    return map;
  }

  /** Parses and fully validates a canonical map document. */
  public static MapDocument parse(byte[] utf8Json) throws IOException {
    JsonObject root = PipelineIO.parse(utf8Json);
    unexpected(root, DOCUMENT_FIELDS, "map document");
    if (PipelineIO.integer(root, "schemaVersion") != SCHEMA_VERSION) {
      throw new IOException("Unsupported map schemaVersion; this build writes version 1");
    }
    String orientation = PipelineIO.string(root, "orientation");
    if (!"orthogonal".equals(orientation)) {
      throw new IOException(
          "Unsupported map orientation: "
              + orientation
              + "; only finite orthogonal maps are built");
    }
    String id = name(PipelineIO.string(root, "id"), "map id", 128);
    int width = bounded(root, "width", 1, MAX_DIMENSION);
    int height = bounded(root, "height", 1, MAX_DIMENSION);
    geometry(
        width,
        height,
        bounded(root, "tileWidth", 1, MAX_TILE_SIZE),
        bounded(root, "tileHeight", 1, MAX_TILE_SIZE));
    MapDocument map =
        new MapDocument(
            id,
            width,
            height,
            bounded(root, "tileWidth", 1, MAX_TILE_SIZE),
            bounded(root, "tileHeight", 1, MAX_TILE_SIZE));
    JsonArray tilesets = array(root, "tilesets", MAX_TILESETS);
    for (JsonElement element : tilesets) {
      JsonObject tileset = object(element, "tileset");
      if (tileset.has("source")) {
        throw new IOException("External tileset files are not supported; declare an asset ID");
      }
      unexpected(tileset, TILESET_FIELDS, "tileset");
      map.addTileset(
          PipelineIO.string(tileset, "name"),
          PipelineIO.string(tileset, "asset"),
          bounded(tileset, "columns", 1, MAX_TILES_PER_SET),
          bounded(tileset, "tileCount", 1, MAX_TILES_PER_SET));
    }
    JsonArray layers = array(root, "layers", MAX_LAYERS);
    if (layers.size() == 0) {
      throw new IOException("A map needs at least one tile layer");
    }
    map.layers.clear();
    for (JsonElement element : layers) {
      JsonObject layer = object(element, "layer");
      if (layer.has("chunks")) {
        throw new IOException("Chunked (infinite) layers are not supported");
      }
      unexpected(layer, LAYER_FIELDS, "layer");
      String layerName = name(PipelineIO.string(layer, "name"), "layer name", 64);
      for (Layer existing : map.layers) {
        if (existing.name.equals(layerName)) {
          throw new IOException("Duplicate layer name: " + layerName);
        }
      }
      JsonArray tiles = array(layer, "tiles", MAX_CELLS);
      if (tiles.size() != width * height) {
        throw new IOException(
            "Layer " + layerName + " has " + tiles.size() + " tiles, expected " + width * height);
      }
      int[] words = new int[tiles.size()];
      for (int index = 0; index < words.length; index++) {
        words[index] = map.tileWord(tiles.get(index), layerName);
      }
      map.layers.add(new Layer(layerName, words));
    }
    JsonArray collision = array(root, "collision", MAX_CELLS);
    if (collision.size() != width * height) {
      throw new IOException(
          "Collision has " + collision.size() + " cells, expected " + width * height);
    }
    for (int index = 0; index < collision.size(); index++) {
      long value = integer(collision.get(index), "collision cell");
      if (value != 0 && value != 1) {
        throw new IOException("Collision cells must be 0 or 1");
      }
      map.collision[index] = value == 1;
    }
    for (JsonElement element : array(root, "spawns", MAX_SPAWNS)) {
      JsonObject spawn = object(element, "spawn");
      unexpected(spawn, SPAWN_FIELDS, "spawn");
      map.addSpawn(
          PipelineIO.string(spawn, "id"),
          PipelineIO.string(spawn, "kind"),
          PipelineIO.string(spawn, "template"),
          bounded(spawn, "x", 0, MAX_DIMENSION),
          bounded(spawn, "y", 0, MAX_DIMENSION),
          bounded(spawn, "hp", 0, MAX_HP));
    }
    for (JsonElement element : array(root, "portals", MAX_PORTALS)) {
      JsonObject portal = object(element, "portal");
      unexpected(portal, PORTAL_FIELDS, "portal");
      map.addPortal(
          PipelineIO.string(portal, "id"),
          bounded(portal, "x", 0, MAX_DIMENSION),
          bounded(portal, "y", 0, MAX_DIMENSION),
          PipelineIO.string(portal, "targetMap"),
          bounded(portal, "targetX", 0, MAX_DIMENSION),
          bounded(portal, "targetY", 0, MAX_DIMENSION));
    }
    return map;
  }

  /** Returns canonical UTF-8 bytes; repeated writes of equal content produce equal bytes. */
  public byte[] toJsonBytes() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("id", id);
    root.addProperty("orientation", "orthogonal");
    root.addProperty("width", width);
    root.addProperty("height", height);
    root.addProperty("tileWidth", tileWidth);
    root.addProperty("tileHeight", tileHeight);
    JsonArray tilesetArray = new JsonArray();
    for (Tileset tileset : tilesets) {
      JsonObject item = new JsonObject();
      item.addProperty("name", tileset.name);
      item.addProperty("asset", tileset.asset);
      item.addProperty("columns", tileset.columns);
      item.addProperty("tileCount", tileset.tileCount);
      tilesetArray.add(item);
    }
    root.add("tilesets", tilesetArray);
    JsonArray layerArray = new JsonArray();
    for (Layer layer : layers) {
      JsonObject item = new JsonObject();
      item.addProperty("name", layer.name);
      JsonArray tiles = new JsonArray();
      for (int word : layer.tiles) {
        tiles.add(word);
      }
      item.add("tiles", tiles);
      layerArray.add(item);
    }
    root.add("layers", layerArray);
    JsonArray collisionArray = new JsonArray();
    for (boolean blocked : collision) {
      collisionArray.add(blocked ? 1 : 0);
    }
    root.add("collision", collisionArray);
    JsonArray spawnArray = new JsonArray();
    for (Spawn spawn : spawns) {
      JsonObject item = new JsonObject();
      item.addProperty("id", spawn.id);
      item.addProperty("kind", spawn.kind);
      item.addProperty("template", spawn.template);
      item.addProperty("x", spawn.x);
      item.addProperty("y", spawn.y);
      item.addProperty("hp", spawn.hp);
      spawnArray.add(item);
    }
    root.add("spawns", spawnArray);
    JsonArray portalArray = new JsonArray();
    for (Portal portal : portals) {
      JsonObject item = new JsonObject();
      item.addProperty("id", portal.id);
      item.addProperty("x", portal.x);
      item.addProperty("y", portal.y);
      item.addProperty("targetMap", portal.targetMap);
      item.addProperty("targetX", portal.targetX);
      item.addProperty("targetY", portal.targetY);
      portalArray.add(item);
    }
    root.add("portals", portalArray);
    return PipelineIO.json(root);
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
    return layers.size();
  }

  public String layerName(int layer) {
    return layers.get(layer).name;
  }

  public int tile(int layer, int x, int y) throws IOException {
    return layers.get(layerIndex(layer)).tiles[cell(x, y)];
  }

  public void setTile(int layer, int x, int y, int tileWord) throws IOException {
    int index = layerIndex(layer);
    int offset = cell(x, y);
    checkTileWord(tileWord);
    layers.get(index).tiles[offset] = tileWord;
  }

  /** Appends a layer above the existing ones and returns its index. */
  public int addLayer(String name) throws IOException {
    name(name, "layer name", 64);
    if (layers.size() >= MAX_LAYERS) {
      throw new IOException("A map accepts at most " + MAX_LAYERS + " layers");
    }
    for (Layer layer : layers) {
      if (layer.name.equals(name)) {
        throw new IOException("Duplicate layer name: " + name);
      }
    }
    layers.add(new Layer(name, new int[width * height]));
    return layers.size() - 1;
  }

  public void removeLayer(int layer) throws IOException {
    int index = layerIndex(layer);
    if (layers.size() == 1) {
      throw new IOException("A map needs at least one tile layer");
    }
    layers.remove(index);
  }

  /** Returns a copy of a layer's tile words. */
  public int[] layerTiles(int layer) throws IOException {
    return layers.get(layerIndex(layer)).tiles.clone();
  }

  /** Inserts a layer with existing content, so undoing a layer removal restores its tiles. */
  public void insertLayer(int index, String name, int[] tiles) throws IOException {
    name(name, "layer name", 64);
    if (index < 0 || index > layers.size()) {
      throw new IOException("Invalid layer index: " + index);
    }
    if (layers.size() >= MAX_LAYERS) {
      throw new IOException("A map accepts at most " + MAX_LAYERS + " layers");
    }
    if (tiles.length != width * height) {
      throw new IOException("Layer tiles length does not match the map size");
    }
    for (Layer layer : layers) {
      if (layer.name.equals(name)) {
        throw new IOException("Duplicate layer name: " + name);
      }
    }
    int[] copy = tiles.clone();
    for (int word : copy) {
      checkTileWord(word);
    }
    layers.add(index, new Layer(name, copy));
  }

  public void moveLayer(int from, int to) throws IOException {
    layers.add(layerIndex(to), layers.remove(layerIndex(from)));
  }

  public boolean blocked(int x, int y) throws IOException {
    return collision[cell(x, y)];
  }

  public void setBlocked(int x, int y, boolean blocked) throws IOException {
    collision[cell(x, y)] = blocked;
  }

  public void addTileset(String name, String assetId, int columns, int tileCount)
      throws IOException {
    name(name, "tileset name", 64);
    name(assetId, "tileset asset ID", 256);
    if (columns < 1
        || columns > MAX_TILES_PER_SET
        || tileCount < 1
        || tileCount > MAX_TILES_PER_SET) {
      throw new IOException("Tileset columns and tileCount must be 1.." + MAX_TILES_PER_SET);
    }
    if (tilesets.size() >= MAX_TILESETS) {
      throw new IOException("A map accepts at most " + MAX_TILESETS + " tilesets");
    }
    for (Tileset tileset : tilesets) {
      if (tileset.name.equals(name)) {
        throw new IOException("Duplicate tileset name: " + name);
      }
    }
    tilesets.add(new Tileset(name, assetId, columns, tileCount, totalTiles() + 1));
  }

  public List<Tileset> tilesets() {
    return Collections.unmodifiableList(new ArrayList<>(tilesets));
  }

  public List<Spawn> spawns() {
    return Collections.unmodifiableList(new ArrayList<>(spawns));
  }

  public List<Portal> portals() {
    return Collections.unmodifiableList(new ArrayList<>(portals));
  }

  public void addSpawn(String id, String kind, String template, int x, int y, int hp)
      throws IOException {
    insertSpawn(spawns.size(), id, kind, template, x, y, hp);
  }

  /** Inserts a spawn at a position, so undoing a removal restores the original order. */
  public void insertSpawn(int index, String id, String kind, String template, int x, int y, int hp)
      throws IOException {
    if (index < 0 || index > spawns.size()) {
      throw new IOException("Invalid spawn index: " + index);
    }
    name(id, "spawn ID", 64);
    name(template, "spawn template", 64);
    if (!SPAWN_KINDS.contains(kind)) {
      throw new IOException("Unsupported spawn kind: " + kind);
    }
    if (hp < 0 || hp > MAX_HP) {
      throw new IOException("Spawn hp must be 0.." + MAX_HP);
    }
    if (spawns.size() >= MAX_SPAWNS) {
      throw new IOException("A map accepts at most " + MAX_SPAWNS + " spawns");
    }
    for (Spawn spawn : spawns) {
      if (spawn.id.equals(id)) {
        throw new IOException("Duplicate spawn ID: " + id);
      }
    }
    cell(x, y);
    spawns.add(index, new Spawn(id, kind, template, x, y, hp));
  }

  public void removeSpawn(String id) throws IOException {
    for (int index = 0; index < spawns.size(); index++) {
      if (spawns.get(index).id.equals(id)) {
        spawns.remove(index);
        return;
      }
    }
    throw new IOException("Unknown spawn ID: " + id);
  }

  public void addPortal(String id, int x, int y, String targetMap, int targetX, int targetY)
      throws IOException {
    insertPortal(portals.size(), id, x, y, targetMap, targetX, targetY);
  }

  /** Inserts a portal at a position, so undoing a removal restores the original order. */
  public void insertPortal(
      int index, String id, int x, int y, String targetMap, int targetX, int targetY)
      throws IOException {
    if (index < 0 || index > portals.size()) {
      throw new IOException("Invalid portal index: " + index);
    }
    name(id, "portal ID", 64);
    name(targetMap, "portal targetMap", 128);
    if (targetX < 0 || targetX >= MAX_DIMENSION || targetY < 0 || targetY >= MAX_DIMENSION) {
      throw new IOException("Portal target cell must be 0.." + (MAX_DIMENSION - 1));
    }
    if (portals.size() >= MAX_PORTALS) {
      throw new IOException("A map accepts at most " + MAX_PORTALS + " portals");
    }
    for (Portal portal : portals) {
      if (portal.id.equals(id)) {
        throw new IOException("Duplicate portal ID: " + id);
      }
      if (portal.x == x && portal.y == y) {
        throw new IOException("Two portals share cell " + x + "," + y);
      }
    }
    cell(x, y);
    portals.add(index, new Portal(id, x, y, targetMap, targetX, targetY));
  }

  public void removePortal(String id) throws IOException {
    for (int index = 0; index < portals.size(); index++) {
      if (portals.get(index).id.equals(id)) {
        portals.remove(index);
        return;
      }
    }
    throw new IOException("Unknown portal ID: " + id);
  }

  /** Highest valid gid; gid 0 means an empty cell. */
  public int totalTiles() {
    int total = 0;
    for (Tileset tileset : tilesets) {
      total += tileset.tileCount;
    }
    return total;
  }

  private int tileWord(JsonElement element, String layerName) throws IOException {
    long value = integer(element, "tile word in layer " + layerName);
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new IOException("Tile word out of range in layer " + layerName + ": " + value);
    }
    int word = (int) value;
    checkTileWord(word);
    return word;
  }

  private void checkTileWord(int tileWord) throws IOException {
    if (tileWord < 0) {
      throw new IOException("Tile word must not be negative");
    }
    int tile = gid(tileWord);
    if (tile > totalTiles()) {
      throw new IOException("Tile gid " + tile + " exceeds the declared tileset tiles");
    }
  }

  private int layerIndex(int layer) throws IOException {
    if (layer < 0 || layer >= layers.size()) {
      throw new IOException("Unknown layer index: " + layer);
    }
    return layer;
  }

  private int cell(int x, int y) throws IOException {
    if (x < 0 || x >= width || y < 0 || y >= height) {
      throw new IOException("Cell " + x + "," + y + " is outside " + width + "x" + height);
    }
    return y * width + x;
  }

  private static void geometry(int width, int height, int tileWidth, int tileHeight)
      throws IOException {
    if (width < 1
        || width > MAX_DIMENSION
        || height < 1
        || height > MAX_DIMENSION
        || tileWidth < 1
        || tileWidth > MAX_TILE_SIZE
        || tileHeight < 1
        || tileHeight > MAX_TILE_SIZE) {
      throw new IOException("Map dimensions and tile size are outside the supported range");
    }
    if (width * height > MAX_CELLS) {
      throw new IOException("Map exceeds " + MAX_CELLS + " cells");
    }
  }

  private static String name(String value, String what, int limit) throws IOException {
    if (value.length() > limit || !value.matches(NAME_GRAMMAR)) {
      throw new IOException("Invalid " + what + ": " + value);
    }
    return value;
  }

  private static void unexpected(JsonObject object, Set<String> allowed, String what)
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

  private static JsonArray array(JsonObject object, String key, int limit) throws IOException {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > limit) {
      throw new IOException("Expected array within limits: " + key);
    }
    return value.getAsJsonArray();
  }

  private static JsonObject object(JsonElement element, String what) throws IOException {
    if (element == null || !element.isJsonObject()) {
      throw new IOException("Expected object: " + what);
    }
    return element.getAsJsonObject();
  }

  /**
   * Accepts only plain nonnegative integer literals, the same grammar the runtime reader enforces.
   * Writing {@code 2.0} or {@code -0} here would otherwise build a bundle the client and the server
   * refuse to read.
   */
  private static long integer(JsonElement element, String what) throws IOException {
    if (element == null
        || !element.isJsonPrimitive()
        || !element.getAsJsonPrimitive().isNumber()
        || !element.getAsString().matches("0|[1-9][0-9]*")) {
      throw new IOException("Expected a plain nonnegative integer: " + what);
    }
    try {
      return Long.parseLong(element.getAsString());
    } catch (NumberFormatException exception) {
      throw new IOException("Integer out of range: " + what, exception);
    }
  }

  private static int bounded(JsonObject object, String key, int minimum, int maximum)
      throws IOException {
    long value = integer(object.get(key), key);
    if (value < minimum || value > maximum) {
      throw new IOException("Field " + key + " must be " + minimum + ".." + maximum);
    }
    return (int) value;
  }

  private static final class Layer {
    private final String name;
    private final int[] tiles;

    private Layer(String name, int[] tiles) {
      this.name = name;
      this.tiles = tiles;
    }
  }

  /** A tileset reference; {@code firstGid} follows declaration order and is never stored. */
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

  /** A spawn point for the demo server rules; not recovered SwordOL spawn data. */
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

  /** A transfer cell pointing at a cell of another map document. */
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
