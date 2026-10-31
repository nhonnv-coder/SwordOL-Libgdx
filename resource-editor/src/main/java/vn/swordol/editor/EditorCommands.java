package vn.swordol.editor;

import java.io.IOException;
import vn.swordol.assets.pipeline.MapDocument;

/**
 * The map edits the editor offers. Every command records the state it replaced when it is applied,
 * so undo restores exactly that state and redo re-applies the same change.
 */
public final class EditorCommands {
  private EditorCommands() {}

  /** Writes one tile word into a layer cell. */
  public static final class PaintTile implements EditorCommand {
    private final int layer;
    private final int x;
    private final int y;
    private final int tileWord;
    private int previous;

    public PaintTile(int layer, int x, int y, int tileWord) {
      this.layer = layer;
      this.x = x;
      this.y = y;
      this.tileWord = tileWord;
    }

    @Override
    public String describe() {
      return "paint " + x + "," + y + " on layer " + layer;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      int replaced = map.tile(layer, x, y);
      map.setTile(layer, x, y, tileWord);
      previous = replaced;
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.setTile(layer, x, y, previous);
    }
  }

  /** Toggles one flip bit of an existing tile. */
  public static final class FlipTile implements EditorCommand {
    private final int layer;
    private final int x;
    private final int y;
    private final int flipBit;

    public FlipTile(int layer, int x, int y, int flipBit) {
      this.layer = layer;
      this.x = x;
      this.y = y;
      this.flipBit = flipBit;
    }

    @Override
    public String describe() {
      return "flip " + x + "," + y + " on layer " + layer;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      if (flipBit != MapDocument.FLIP_H
          && flipBit != MapDocument.FLIP_V
          && flipBit != MapDocument.FLIP_D) {
        throw new IOException("Flip accepts only the horizontal, vertical or diagonal bit");
      }
      int word = map.tile(layer, x, y);
      if (MapDocument.gid(word) == 0) {
        throw new IOException("Cell " + x + "," + y + " is empty; paint a tile before flipping it");
      }
      map.setTile(layer, x, y, word ^ flipBit);
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.setTile(layer, x, y, map.tile(layer, x, y) ^ flipBit);
    }
  }

  /** Marks a cell blocked or walkable for the server. */
  public static final class SetBlocked implements EditorCommand {
    private final int x;
    private final int y;
    private final boolean blocked;
    private boolean previous;

    public SetBlocked(int x, int y, boolean blocked) {
      this.x = x;
      this.y = y;
      this.blocked = blocked;
    }

    @Override
    public String describe() {
      return (blocked ? "block " : "unblock ") + x + "," + y;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      boolean replaced = map.blocked(x, y);
      map.setBlocked(x, y, blocked);
      previous = replaced;
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.setBlocked(x, y, previous);
    }
  }

  /** Appends an empty layer above the existing ones. */
  public static final class AddLayer implements EditorCommand {
    private final String name;
    private int index;

    public AddLayer(String name) {
      this.name = name;
    }

    @Override
    public String describe() {
      return "add layer " + name;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      index = map.addLayer(name);
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.removeLayer(index);
    }
  }

  /** Removes a layer, keeping its content for undo. */
  public static final class RemoveLayer implements EditorCommand {
    private final int layer;
    private String name;
    private int[] tiles;

    public RemoveLayer(int layer) {
      this.layer = layer;
    }

    @Override
    public String describe() {
      return "remove layer " + layer;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      String removedName = map.layerName(layer);
      int[] removedTiles = map.layerTiles(layer);
      map.removeLayer(layer);
      name = removedName;
      tiles = removedTiles;
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.insertLayer(layer, name, tiles);
    }
  }

  /** Changes the draw order of a layer. */
  public static final class MoveLayer implements EditorCommand {
    private final int from;
    private final int to;

    public MoveLayer(int from, int to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public String describe() {
      return "move layer " + from + " to " + to;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      map.moveLayer(from, to);
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.moveLayer(to, from);
    }
  }

  /** Adds a spawn point. */
  public static final class AddSpawn implements EditorCommand {
    private final String id;
    private final String kind;
    private final String template;
    private final int x;
    private final int y;
    private final int hp;

    public AddSpawn(String id, String kind, String template, int x, int y, int hp) {
      this.id = id;
      this.kind = kind;
      this.template = template;
      this.x = x;
      this.y = y;
      this.hp = hp;
    }

    @Override
    public String describe() {
      return "add spawn " + id;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      map.addSpawn(id, kind, template, x, y, hp);
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.removeSpawn(id);
    }
  }

  /** Removes a spawn point, keeping its fields and position for undo. */
  public static final class RemoveSpawn implements EditorCommand {
    private final String id;
    private int index;
    private MapDocument.Spawn removed;

    public RemoveSpawn(String id) {
      this.id = id;
    }

    @Override
    public String describe() {
      return "remove spawn " + id;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      int position = -1;
      for (int candidate = 0; candidate < map.spawns().size(); candidate++) {
        if (map.spawns().get(candidate).id().equals(id)) {
          position = candidate;
        }
      }
      if (position < 0) {
        throw new IOException("Unknown spawn ID: " + id);
      }
      MapDocument.Spawn spawn = map.spawns().get(position);
      map.removeSpawn(id);
      index = position;
      removed = spawn;
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.insertSpawn(
          index,
          removed.id(),
          removed.kind(),
          removed.template(),
          removed.x(),
          removed.y(),
          removed.hp());
    }
  }

  /** Adds a portal. */
  public static final class AddPortal implements EditorCommand {
    private final String id;
    private final int x;
    private final int y;
    private final String targetMap;
    private final int targetX;
    private final int targetY;

    public AddPortal(String id, int x, int y, String targetMap, int targetX, int targetY) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.targetMap = targetMap;
      this.targetX = targetX;
      this.targetY = targetY;
    }

    @Override
    public String describe() {
      return "add portal " + id;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      map.addPortal(id, x, y, targetMap, targetX, targetY);
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.removePortal(id);
    }
  }

  /** Removes a portal, keeping its target for undo. */
  public static final class RemovePortal implements EditorCommand {
    private final String id;
    private int index;
    private MapDocument.Portal removed;

    public RemovePortal(String id) {
      this.id = id;
    }

    @Override
    public String describe() {
      return "remove portal " + id;
    }

    @Override
    public void apply(MapDocument map) throws IOException {
      int position = -1;
      for (int candidate = 0; candidate < map.portals().size(); candidate++) {
        if (map.portals().get(candidate).id().equals(id)) {
          position = candidate;
        }
      }
      if (position < 0) {
        throw new IOException("Unknown portal ID: " + id);
      }
      MapDocument.Portal portal = map.portals().get(position);
      map.removePortal(id);
      index = position;
      removed = portal;
    }

    @Override
    public void revert(MapDocument map) throws IOException {
      map.insertPortal(
          index,
          removed.id(),
          removed.x(),
          removed.y(),
          removed.targetMap(),
          removed.targetX(),
          removed.targetY());
    }
  }
}
