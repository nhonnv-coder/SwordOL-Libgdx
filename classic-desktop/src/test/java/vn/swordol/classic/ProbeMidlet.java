package vn.swordol.classic;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

/** Network-free client fixture loaded through the same legacy classloader as the real JAR. */
public final class ProbeMidlet extends MIDlet {
  public static volatile int lastAction;
  public static volatile int pointerX = -1;
  public static volatile int pointerY = -1;
  public static volatile boolean destroyed;
  public static volatile boolean holdDestroy;
  private static ProbeMidlet instance;
  private static TextBox text;

  public void startApp() {
    instance = this;
    Display.getDisplay(this).setCurrent(new ProbeCanvas());
  }

  public void pauseApp() {}

  public void destroyApp(boolean unconditional) {
    destroyed = true;
    while (holdDestroy) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignored) {
        /* Deliberately stubborn fixture. */
      }
    }
  }

  public static void showTextBox() {
    text = new TextBox("Unicode input", "", 40, TextField.ANY);
    Display.getDisplay(instance).setCurrent(text);
  }

  public static String textValue() {
    return text.getString();
  }

  public static final class ProbeCanvas extends Canvas {
    public ProbeCanvas() {
      setFullScreenMode(true);
    }

    protected void paint(Graphics graphics) {
      graphics.setColor(0x112233);
      graphics.fillRect(0, 0, getWidth(), getHeight());
    }

    protected void keyPressed(int keyCode) {
      lastAction = getGameAction(keyCode);
    }

    protected void pointerPressed(int x, int y) {
      pointerX = x;
      pointerY = y;
    }
  }
}
