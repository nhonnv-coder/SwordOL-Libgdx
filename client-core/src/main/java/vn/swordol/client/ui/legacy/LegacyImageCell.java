package vn.swordol.client.ui.legacy;

/**
 * How the original cuts a control's picture out of one image. {@code ImagePointer} treats an image
 * as a horizontal strip of equal cells and draws the cell its index names:
 *
 * <pre>
 *   int xOffset = imgIndex * m_imgw;
 *   drawRegion(g, image, xOffset, 0, m_imgw, image.getHeight(), flip, x, y, TOP | LEFT);
 * </pre>
 *
 * <p>The cell width is carried by the image id itself, {@code getWidthByImgID} reading it as {@code
 * imgID % 10000 / 100}; an id whose digits give zero is a single cell the whole image wide. The
 * cell is always the full height of the image, and it is placed at the control's top left at its
 * own size rather than stretched to the control's box.
 */
public final class LegacyImageCell {
  private LegacyImageCell() {}

  /** ImagePointer.getImageSeparatedWidth: the id's own digits, or the whole image. */
  public static int cellWidth(int imageId, int imageWidth) {
    int declared = Math.floorMod(imageId, 10000) / 100;
    return declared == 0 ? imageWidth : declared;
  }

  /** The left edge of the cell this index names. */
  public static int sourceX(int imageIndex, int cellWidth) {
    return Math.max(0, imageIndex) * cellWidth;
  }

  /** False when the strip does not hold a cell at this index, so nothing should be drawn. */
  public static boolean holds(int imageIndex, int cellWidth, int imageWidth) {
    if (cellWidth <= 0 || imageIndex < 0) return false;
    return sourceX(imageIndex, cellWidth) + cellWidth <= imageWidth;
  }
}
