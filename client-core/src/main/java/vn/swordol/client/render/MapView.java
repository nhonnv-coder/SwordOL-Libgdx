package vn.swordol.client.render;

/**
 * Coordinate rules for drawing a legacy map. MapEx.setMapView centres the view on the entity and
 * clamps it inside the map world; the original canvas counts pixels downward from the top-left,
 * while libGDX counts upward from the bottom-left, so every row and image has to be flipped once.
 */
public final class MapView {
  private MapView() {}

  /** The left or top edge of the view in original pixels, centred and clamped like setMapView. */
  public static int offset(int entityPixel, int viewSize, int worldSize) {
    int edge = entityPixel > viewSize / 2 ? entityPixel - viewSize / 2 : 0;
    edge = Math.min(edge, worldSize - viewSize);
    return Math.max(edge, 0);
  }

  /** GL x of a cell's left edge. */
  public static int cellX(int cellX, WorldMap map) {
    return cellX * map.cellWidth();
  }

  /** GL y of a cell's bottom edge; cell row0 is the top row of the original map. */
  public static int cellY(int cellY, WorldMap map) {
    return map.heightInPixels() - (cellY + 1) * map.cellHeight();
  }

  /** GL y of an image whose top edge sits at this downward pixel row. */
  public static int imageY(int downwardTop, int imageHeight, WorldMap map) {
    return map.heightInPixels() - downwardTop - imageHeight;
  }
}
