package vn.swordol.protocol.wire;

import java.io.IOException;

/**
 * The map-data resource document. Layout and indexing come from MapEx: a five byte header (version,
 * columns, rows, cell width, cell height), one tile byte per cell stored row-major as {@code
 * y*columns+x}, then the blocking, rotation and flight-blocking bitsets with the least significant
 * bit of each byte holding the lower cell index. Parsing only; applying later cell updates is world
 * state and belongs to the caller.
 */
public final class MapResource {
  private final int version, columns, rows, cellWidth, cellHeight;
  private final byte[] tiles, blocking, rotation, flightBlocking;

  private MapResource(
      int version,
      int columns,
      int rows,
      int cellWidth,
      int cellHeight,
      byte[] tiles,
      byte[] blocking,
      byte[] rotation,
      byte[] flightBlocking) {
    this.version = version;
    this.columns = columns;
    this.rows = rows;
    this.cellWidth = cellWidth;
    this.cellHeight = cellHeight;
    this.tiles = tiles;
    this.blocking = blocking;
    this.rotation = rotation;
    this.flightBlocking = flightBlocking;
  }

  public static MapResource read(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 5)
      throw new IOException("Map document is shorter than its header");
    int version = bytes[0] & 255, columns = bytes[1] & 255, rows = bytes[2] & 255;
    int cellWidth = bytes[3] & 255, cellHeight = bytes[4] & 255;
    if (columns < 1
        || columns > 127
        || rows < 1
        || rows > 127
        || cellWidth < 1
        || cellWidth > 127
        || cellHeight < 1
        || cellHeight > 127) throw new IOException("Invalid map dimensions");
    int cells = columns * rows, bits = (cells + 7) / 8;
    if (bytes.length != 5 + cells + 3 * bits) throw new IOException("Invalid map document size");
    return new MapResource(
        version,
        columns,
        rows,
        cellWidth,
        cellHeight,
        java.util.Arrays.copyOfRange(bytes, 5, 5 + cells),
        java.util.Arrays.copyOfRange(bytes, 5 + cells, 5 + cells + bits),
        java.util.Arrays.copyOfRange(bytes, 5 + cells + bits, 5 + cells + 2 * bits),
        java.util.Arrays.copyOfRange(bytes, 5 + cells + 2 * bits, bytes.length));
  }

  public int version() {
    return version;
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

  public byte[] tiles() {
    return tiles.clone();
  }

  public byte[] blockingBits() {
    return blocking.clone();
  }

  public byte[] rotationBits() {
    return rotation.clone();
  }

  public byte[] flightBlockingBits() {
    return flightBlocking.clone();
  }

  public int tile(int x, int y) {
    return tiles[cell(x, y)] & 255;
  }

  public boolean blocked(int x, int y) {
    return bit(blocking, cell(x, y));
  }

  public boolean rotated(int x, int y) {
    return bit(rotation, cell(x, y));
  }

  public boolean flightBlocked(int x, int y) {
    return bit(flightBlocking, cell(x, y));
  }

  /** Row-major cell index used by both the client renderer and the server cell updates. */
  public int cell(int x, int y) {
    if (x < 0 || y < 0 || x >= columns || y >= rows)
      throw new IndexOutOfBoundsException("Cell outside the map: " + x + "," + y);
    return y * columns + x;
  }

  public static boolean bit(byte[] bits, int cell) {
    if (cell < 0 || cell / 8 >= bits.length)
      throw new IndexOutOfBoundsException("Cell outside the bitset");
    return (bits[cell / 8] & (1 << (cell % 8))) != 0;
  }

  public static void setBit(byte[] bits, int cell, boolean value) {
    if (cell < 0 || cell / 8 >= bits.length)
      throw new IndexOutOfBoundsException("Cell outside the bitset");
    if (value) bits[cell / 8] |= (byte) (1 << (cell % 8));
    else bits[cell / 8] &= (byte) ~(1 << (cell % 8));
  }
}
