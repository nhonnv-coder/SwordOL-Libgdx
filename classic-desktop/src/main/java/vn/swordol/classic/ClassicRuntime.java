package vn.swordol.classic;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import javax.microedition.midlet.MIDlet;
import org.microemu.DisplayComponent;
import org.microemu.EmulatorContext;
import org.microemu.MIDletAccess;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.ui.DisplayRepaintListener;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.DeviceDisplay;
import org.microemu.device.FontManager;
import org.microemu.device.InputMethod;
import org.microemu.device.MutableImage;
import org.microemu.device.j2se.J2SEButton;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.j2se.J2SEFontManager;
import org.microemu.device.j2se.J2SEInputMethod;
import org.microemu.device.j2se.J2SEMutableImage;

/**
 * Desktop-only compatibility boundary. Never creates a Swing window or rewrites original business
 * logic.
 */
public final class ClassicRuntime implements AutoCloseable {
  private final FrameMailbox frames;
  private final RuntimeContext context = new RuntimeContext();
  private final Component eventSource =
      new Component() {
        private static final long serialVersionUID = 1L;
      };
  private Common common;
  private volatile boolean closed;

  public ClassicRuntime(int width, int height) {
    frames = new FrameMailbox(width, height);
  }

  public void start(Path jar) throws IOException {
    if (common != null || closed) {
      throw new IllegalStateException("Runtime already used");
    }
    if (!Files.isRegularFile(jar)) {
      throw new IOException("Original client JAR is missing");
    }
    Files.createDirectories(Paths.get(System.getProperty("user.home"), ".microemulator"));
    common = new Common(context);
    DeviceEntry device =
        new DeviceEntry(
            "libGDX framebuffer", null, "org/microemu/device/resizable/device.xml", true, false);
    common.initParams(
        new ArrayList<String>(
            Arrays.asList(
                "--resizableDevice",
                Integer.toString(frames.width),
                Integer.toString(frames.height),
                "--rms",
                "file",
                jar.toAbsolutePath().toString())),
        device,
        J2SEDevice.class);
    // initParams returns whether the DEFAULT descriptor was selected, not startup success.
    if (org.microemu.device.DeviceFactory.getDevice() == null) {
      throw new IOException("Cannot initialize original runtime device");
    }
    common.initMIDlet(true);
    if (MIDletBridge.getCurrentMIDlet() == null) {
      throw new IOException("Original MIDlet did not start");
    }
  }

  public FrameMailbox frames() {
    return frames;
  }

  public Object currentMidlet() {
    return MIDletBridge.getCurrentMIDlet();
  }

  public void button(int awtCode, char character, boolean down) {
    if (closed || MIDletBridge.getMIDletAccess() == null) {
      return;
    }
    KeyEvent event =
        new KeyEvent(
            eventSource,
            down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            0,
            awtCode,
            character == 0 ? KeyEvent.CHAR_UNDEFINED : character);
    J2SEButton button = context.input.getButton(event);
    if (button == null && character == 0) {
      return;
    }
    if (down) {
      context.input.buttonPressed(button, character);
    } else {
      context.input.buttonReleased(button, character);
    }
  }

  public void type(char character) {
    if (!closed && MIDletBridge.getMIDletAccess() != null && character >= ' ' && character != 127) {
      context.input.type(character);
    }
  }

  public void pointer(int action, int x, int y) {
    if (closed || MIDletBridge.getMIDletAccess() == null) {
      return;
    }
    if (action == 0) {
      context.input.pointerPressed(x, y);
    } else if (action == 1) {
      context.input.pointerDragged(x, y);
    } else if (action == 2) {
      context.input.pointerReleased(x, y);
    } else {
      throw new IllegalArgumentException("Unknown pointer action");
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (common != null && org.microemu.device.DeviceFactory.getDevice() != null) {
      Thread shutdown =
          new Thread(
              () -> {
                try {
                  Common.dispose();
                } catch (RuntimeException failure) {
                  System.err.println(
                      "Original shutdown failed: " + failure.getClass().getSimpleName());
                }
              },
              "classic-shutdown");
      shutdown.setDaemon(true);
      shutdown.start();
      try {
        shutdown.join(2500);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      if (shutdown.isAlive()) {
        System.err.println("Original shutdown exceeded 2500ms; host exit finalizes captures.");
      }
    }
    context.input.dispose();
  }

  private static final class DesktopInput extends J2SEInputMethod {
    void type(char character) {
      if (inputMethodListener != null) {
        clipboardPaste(Character.toString(character));
      } else {
        buttonPressed(null, character);
        buttonReleased(null, character);
      }
    }
  }

  private final class RuntimeContext implements EmulatorContext {
    private final DesktopInput input = new DesktopInput();
    private final J2SEDeviceDisplay display = new J2SEDeviceDisplay(this);
    private final J2SEFontManager fonts = new J2SEFontManager();
    private final DisplayComponent component =
        new DisplayComponent() {
          private J2SEMutableImage image;
          private int[] argb;
          private DisplayRepaintListener listener;

          public void addDisplayRepaintListener(DisplayRepaintListener listener) {
            this.listener = listener;
          }

          public void removeDisplayRepaintListener(DisplayRepaintListener listener) {
            if (this.listener == listener) {
              this.listener = null;
            }
          }

          public MutableImage getDisplayImage() {
            return image;
          }

          public synchronized void repaintRequest(int x, int y, int width, int height) {
            MIDletAccess access = MIDletBridge.getMIDletAccess();
            if (closed
                || access == null
                || access.getDisplayAccess() == null
                || access.getDisplayAccess().getCurrent() == null) {
              return;
            }
            if (image == null) {
              image = new J2SEMutableImage(frames.width, frames.height);
              argb = new int[frames.width * frames.height];
            }
            Graphics graphics = image.getImage().getGraphics();
            try {
              if (!display.isFullScreenMode()) {
                display.paintControls(graphics);
              }
              display.paintDisplayable(graphics, x, y, width, height);
            } finally {
              graphics.dispose();
            }
            image.getRGB(argb, 0, frames.width, 0, 0, frames.width, frames.height);
            frames.publish(argb);
            if (listener != null) {
              listener.repaintInvoked(image);
            }
          }
        };

    public DisplayComponent getDisplayComponent() {
      return component;
    }

    public InputMethod getDeviceInputMethod() {
      return input;
    }

    public DeviceDisplay getDeviceDisplay() {
      return display;
    }

    public FontManager getDeviceFontManager() {
      return fonts;
    }

    public InputStream getResourceAsStream(String name) {
      MIDlet midlet = MIDletBridge.getCurrentMIDlet();
      return midlet == null
          ? ClassicRuntime.class.getResourceAsStream(name)
          : midlet.getClass().getResourceAsStream(name);
    }

    public boolean platformRequest(String url) {
      return false;
    }
  }
}
