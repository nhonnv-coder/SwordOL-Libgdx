package vn.swordol.client.render;

import java.util.Objects;
import vn.swordol.protocol.wire.MapResource;

/**
 * The live grid of one original map: the document the server sent plus the cell and tile updates it
 * keeps sending. Indexing and the walk rules follow MapEx: cells are row-major, and MapEx.isBlock
 * treats column or row zero and anything outside the grid as blocked for a walking object.
 * Rendering and world logic read this; it owns no network, no PureMVC and no GL.
 */
public final class WorldMap {
  public static final int EMPTY_TILE = 255;
  private final int columns, rows, cellWidth, cellHeight;
  private final byte[] tiles, blocking, rotation, flightBlocking;

  public WorldMap(MapResource resource) {
    Objects.requireNonNull(resource, "resource");
    columns = resource.columns();
    rows = resource.rows();
    cellWidth = resource.cellWidth();
    cellHeight = resource.cellHeight();
    tiles = resource.tiles();
    blocking = resource.blockingBits();
    rotation = resource.rotationBits();
    flightBlocking = resource.flightBlockingBits();
  }

  public int columns() {
    return columns;
  }

  public int rows() {
    return rows;
  }

  public int cellWidth() {
    return cellWidth;
  }

  public int cellHeight() {
    return cellHeight;
  }

  public int widthInPixels() {
    return columns * cellWidth;
  }

  public int heightInPixels() {
    return rows * cellHeight;
  }

  public boolean holds(int x, int y) {
    return x >= 0 && y >= 0 && x < columns && y < rows;
  }

  public int tile(int x, int y) {
    return tiles[index(x, y)] & 255;
  }

  public boolean blocked(int x, int y) {
    return MapResource.bit(blocking, index(x, y));
  }

  public boolean rotated(int x, int y) {
    return MapResource.bit(rotation, index(x, y));
  }

  public boolean flightBlocked(int x, int y) {
    return MapResource.bit(flightBlocking, index(x, y));
  }

  /** MapEx.isBlock for a walking object: the first column and row are never walkable. */
  public boolean walkable(int x, int y) {
    return x >= 1 && y >= 1 && x < columns && y < rows && !blocked(x, y);
  }

  /** 1126 action2. Updates outside this grid are dropped, as the original's length check does. */
  public void applyCell(int x, int y, boolean blockedCell, boolean flightBlockedCell) {
    if (!holds(x, y)) return;
    MapResource.setBit(blocking, index(x, y), blockedCell);
    MapResource.setBit(flightBlocking, index(x, y), flightBlockedCell);
  }

  /** 1126 action3. */
  public void applyTile(int x, int y, int tile, boolean rotatedCell) {
    if (!holds(x, y)) return;
    tiles[index(x, y)] = (byte) (tile & 255);
    MapResource.setBit(rotation, index(x, y), rotatedCell);
  }

  private int index(int x, int y) {
    if (!holds(x, y)) throw new IndexOutOfBoundsException("Cell outside the map: " + x + "," + y);
    return y * columns + x;
  }
}
