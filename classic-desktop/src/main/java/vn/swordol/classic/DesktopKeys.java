package vn.swordol.classic;

import com.badlogic.gdx.Input.Keys;
import java.awt.event.KeyEvent;

/** Physical navigation keys only. Printable input arrives through libGDX keyTyped. */
public final class DesktopKeys {
  private DesktopKeys() {}

  public static int awtCode(int key) {
    if (key >= Keys.NUMPAD_0 && key <= Keys.NUMPAD_9) {
      return KeyEvent.VK_NUMPAD0 + key - Keys.NUMPAD_0;
    }
    switch (key) {
      case Keys.UP:
        return KeyEvent.VK_UP;
      case Keys.DOWN:
        return KeyEvent.VK_DOWN;
      case Keys.LEFT:
        return KeyEvent.VK_LEFT;
      case Keys.RIGHT:
        return KeyEvent.VK_RIGHT;
      case Keys.ENTER:
      case Keys.NUMPAD_ENTER:
        return KeyEvent.VK_ENTER;
      case Keys.F1:
        return KeyEvent.VK_F1;
      case Keys.F2:
        return KeyEvent.VK_F2;
      case Keys.ESCAPE:
        return KeyEvent.VK_F2;
      case Keys.BACKSPACE:
        return KeyEvent.VK_BACK_SPACE;
      case Keys.FORWARD_DEL:
        return KeyEvent.VK_DELETE;
      case Keys.TAB:
        return KeyEvent.VK_TAB;
      default:
        return KeyEvent.VK_UNDEFINED;
    }
  }
}
