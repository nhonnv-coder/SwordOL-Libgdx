package vn.swordol.client.ui.legacy;

/**
 * The one coordinate difference between an original window and a Scene2D stage. Layouts measure y
 * downwards from the top left, as MIDP does; Scene2D measures it upwards from the bottom left. The
 * x axis and every size are the same in both, so nothing else is converted.
 */
public final class LegacyPlacement {
  private LegacyPlacement() {}

  /** The stage y of a control whose layout box is (y, height) inside a window this tall. */
  public static int stageY(int windowHeight, int controlY, int controlHeight) {
    return windowHeight - controlY - controlHeight;
  }
}
