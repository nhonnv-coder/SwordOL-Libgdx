package vn.swordol.classic;

/** The fixed game resolution is independent of desktop window size and UI/footer bars. */
public final class ViewportBounds {
  public final float left, bottom, width, height, scale;
  private final int screenHeight, logicalWidth, logicalHeight;

  private ViewportBounds(int sw, int sh, int lw, int lh, int footer, boolean integer) {
    if (sw <= 0 || sh <= 0 || lw <= 0 || lh <= 0) {
      throw new IllegalArgumentException("Invalid viewport size");
    }
    screenHeight = sh;
    logicalWidth = lw;
    logicalHeight = lh;
    footer = Math.max(0, Math.min(footer, sh - 1));
    float fit = Math.min((float) sw / lw, (float) (sh - footer) / lh);
    scale = integer && fit >= 1 ? (float) Math.floor(fit) : fit;
    width = lw * scale;
    height = lh * scale;
    left = (sw - width) / 2;
    bottom = footer + (sh - footer - height) / 2;
  }

  public static ViewportBounds fit(int sw, int sh, int lw, int lh, int footer, boolean integer) {
    return new ViewportBounds(sw, sh, lw, lh, footer, integer);
  }

  /** Input and returned coordinates both have their origin at the top left. */
  public int[] point(int x, int y, boolean clamp) {
    float top = screenHeight - bottom - height;
    if (!clamp && (x < left || x >= left + width || y < top || y >= top + height)) {
      return null;
    }
    return new int[] {
      Math.max(0, Math.min(logicalWidth - 1, (int) Math.floor((x - left) / scale))),
      Math.max(0, Math.min(logicalHeight - 1, (int) Math.floor((y - top) / scale)))
    };
  }
}
