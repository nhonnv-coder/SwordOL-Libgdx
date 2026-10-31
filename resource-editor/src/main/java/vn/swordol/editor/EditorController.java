package vn.swordol.editor;

import java.io.IOException;
import java.util.List;
import vn.swordol.assets.pipeline.MapDocument;

/**
 * The editing logic behind the editor window: which map, layer, tile and tool are selected, and
 * what a click on a cell means. Keeping it free of libGDX lets the UI stay a thin shell and lets
 * these rules be tested without a window.
 */
public final class EditorController {
  /** What a click on a map cell does. */
  public enum Tool {
    PAINT,
    ERASE,
    COLLISION,
    FLIP_H,
    FLIP_V,
    FLIP_D
  }

  private final EditorProject project;
  private MapEditSession session;
  private int layer;
  private int tile = 1;
  private Tool tool = Tool.PAINT;

  public EditorController(EditorProject project) {
    this.project = project;
  }

  public EditorProject project() {
    return project;
  }

  /** Sorted IDs of the project's map assets. */
  public List<String> maps() {
    return project.assets("map");
  }

  public boolean hasMap() {
    return session != null;
  }

  public MapEditSession session() {
    return session;
  }

  public void openMap(String assetId) throws IOException {
    MapEditSession opened = project.openMap(assetId);
    session = opened;
    layer = 0;
    tile = Math.min(1, opened.document().totalTiles());
    tool = Tool.PAINT;
  }

  public void createMap(String assetId, int width, int height, int tileWidth, int tileHeight)
      throws IOException {
    MapEditSession created = project.createMap(assetId, width, height, tileWidth, tileHeight);
    session = created;
    layer = 0;
    tile = 0;
    tool = Tool.PAINT;
  }

  public void selectLayer(int index) throws IOException {
    if (index < 0 || index >= require().document().layerCount()) {
      throw new IOException("Unknown layer index: " + index);
    }
    layer = index;
  }

  public int selectedLayer() {
    return layer;
  }

  /** Selects the tile gid the paint tool writes; 0 is the empty tile. */
  public void selectTile(int gid) throws IOException {
    if (gid < 0 || gid > require().document().totalTiles()) {
      throw new IOException("Tile gid beyond the map tilesets: " + gid);
    }
    tile = gid;
  }

  public int selectedTile() {
    return tile;
  }

  public void selectTool(Tool selected) {
    tool = selected;
  }

  public Tool tool() {
    return tool;
  }

  public void addLayer(String name) throws IOException {
    MapEditSession current = require();
    current.apply(new EditorCommands.AddLayer(name));
    layer = current.document().layerCount() - 1;
  }

  public void removeSelectedLayer() throws IOException {
    MapEditSession current = require();
    current.apply(new EditorCommands.RemoveLayer(layer));
    layer = Math.min(layer, current.document().layerCount() - 1);
  }

  public void moveSelectedLayer(int to) throws IOException {
    MapEditSession current = require();
    current.apply(new EditorCommands.MoveLayer(layer, to));
    layer = to;
  }

  /** Applies the selected tool to one map cell. */
  public void clickCell(int x, int y) throws IOException {
    MapEditSession current = require();
    MapDocument map = current.document();
    if (x < 0 || x >= map.width() || y < 0 || y >= map.height()) {
      throw new IOException("Cell " + x + "," + y + " is outside the map");
    }
    switch (tool) {
      case PAINT:
        current.apply(new EditorCommands.PaintTile(layer, x, y, tile));
        break;
      case ERASE:
        current.apply(new EditorCommands.PaintTile(layer, x, y, 0));
        break;
      case COLLISION:
        current.apply(new EditorCommands.SetBlocked(x, y, !map.blocked(x, y)));
        break;
      case FLIP_H:
        current.apply(new EditorCommands.FlipTile(layer, x, y, MapDocument.FLIP_H));
        break;
      case FLIP_V:
        current.apply(new EditorCommands.FlipTile(layer, x, y, MapDocument.FLIP_V));
        break;
      default:
        current.apply(new EditorCommands.FlipTile(layer, x, y, MapDocument.FLIP_D));
        break;
    }
  }

  public void addSpawn(String id, String kind, String template, int x, int y, int hp)
      throws IOException {
    require().apply(new EditorCommands.AddSpawn(id, kind, template, x, y, hp));
  }

  public void removeSpawn(String id) throws IOException {
    require().apply(new EditorCommands.RemoveSpawn(id));
  }

  public void addPortal(String id, int x, int y, String targetMap, int targetX, int targetY)
      throws IOException {
    require().apply(new EditorCommands.AddPortal(id, x, y, targetMap, targetX, targetY));
  }

  public void removePortal(String id) throws IOException {
    require().apply(new EditorCommands.RemovePortal(id));
  }

  public void undo() throws IOException {
    require().undo();
    clampLayer();
  }

  public void redo() throws IOException {
    require().redo();
    clampLayer();
  }

  public void save() throws IOException {
    project.saveMap(require());
  }

  private void clampLayer() {
    layer = Math.max(0, Math.min(layer, session.document().layerCount() - 1));
  }

  private MapEditSession require() throws IOException {
    if (session == null) {
      throw new IOException("No map is open");
    }
    return session;
  }
}
