package vn.swordol.classic;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The actual visible desktop window is libGDX. The original program remains behind ClassicRuntime.
 */
public final class ClassicApplication extends ApplicationAdapter {
  private static final int FOOTER = 52;
  private final Path client;
  private final int logicalWidth, logicalHeight;
  private final Map<Integer, Integer> held = new HashMap<Integer, Integer>();
  private final Matrix4 projection = new Matrix4();
  private ClassicRuntime runtime;
  private SpriteBatch batch;
  private BitmapFont font;
  private Pixmap pixels;
  private Texture texture;
  private ViewportBounds viewport;
  private boolean integerScale, pointerDown;
  private int pointerX, pointerY, renderedFrames;
  private long uploaded;
  private String error;
  private String notice = "";

  public ClassicApplication(Path client, int width, int height) {
    this.client = client;
    logicalWidth = width;
    logicalHeight = height;
  }

  @Override
  public void create() {
    batch = new SpriteBatch();
    font = new BitmapFont();
    font.setColor(Color.LIGHT_GRAY);
    pixels = new Pixmap(logicalWidth, logicalHeight, Pixmap.Format.RGBA8888);
    texture = new Texture(pixels);
    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    runtime = new ClassicRuntime(logicalWidth, logicalHeight);
    try {
      runtime.start(client);
    } catch (Exception failure) {
      error = "Original runtime startup failed: " + failure.getClass().getSimpleName();
      Gdx.app.error("ClassicDesktop", error, failure);
    }
    Gdx.input.setInputProcessor(
        new InputAdapter() {
          @Override
          public boolean keyDown(int key) {
            if (key == Input.Keys.F10) {
              if (Gdx.graphics.isFullscreen()) {
                Gdx.graphics.setWindowedMode(1024, 820);
              } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
              }
              return true;
            }
            if (key == Input.Keys.F11) {
              screenshot();
              return true;
            }
            if (key == Input.Keys.F12) {
              integerScale = !integerScale;
              resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
              return true;
            }
            int code = DesktopKeys.awtCode(key);
            if (code == KeyEvent.VK_UNDEFINED || error != null) {
              return false;
            }
            if (!held.containsKey(key)) {
              held.put(key, code);
            }
            runtime.button(code, '\0', true);
            return true;
          }

          @Override
          public boolean keyUp(int key) {
            Integer code = held.remove(key);
            if (code == null) {
              return false;
            }
            runtime.button(code, '\0', false);
            return true;
          }

          @Override
          public boolean keyTyped(char character) {
            if (error != null || character < ' ' || character == 127) {
              return false;
            }
            // Numpad has already generated its real JavaME phone-key event.
            for (int key : held.keySet()) {
              if (key >= Input.Keys.NUMPAD_0 && key <= Input.Keys.NUMPAD_9) {
                return true;
              }
            }
            runtime.type(character);
            return true;
          }

          @Override
          public boolean touchDown(int x, int y, int pointer, int button) {
            if (error != null || pointer != 0 || button != Input.Buttons.LEFT || viewport == null) {
              return false;
            }
            int[] point = viewport.point(x, y, false);
            if (point == null) {
              return false;
            }
            pointerDown = true;
            pointerX = point[0];
            pointerY = point[1];
            runtime.pointer(0, pointerX, pointerY);
            return true;
          }

          @Override
          public boolean touchDragged(int x, int y, int pointer) {
            if (!pointerDown || pointer != 0) {
              return false;
            }
            int[] point = viewport.point(x, y, true);
            pointerX = point[0];
            pointerY = point[1];
            runtime.pointer(1, pointerX, pointerY);
            return true;
          }

          @Override
          public boolean touchUp(int x, int y, int pointer, int button) {
            if (!pointerDown || pointer != 0 || button != Input.Buttons.LEFT) {
              return false;
            }
            int[] point = viewport.point(x, y, true);
            pointerDown = false;
            runtime.pointer(2, point[0], point[1]);
            return true;
          }
        });
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  @Override
  public void resize(int width, int height) {
    if (width < 1 || height < 1) {
      return;
    }
    releaseInput();
    viewport = ViewportBounds.fit(width, height, logicalWidth, logicalHeight, FOOTER, integerScale);
    projection.setToOrtho2D(0, 0, width, height);
    if (batch != null) {
      batch.setProjectionMatrix(projection);
    }
  }

  public void releaseInput() {
    if (runtime == null) {
      return;
    }
    for (int code : held.values()) {
      runtime.button(code, '\0', false);
    }
    held.clear();
    if (pointerDown) {
      pointerDown = false;
      runtime.pointer(2, pointerX, pointerY);
    }
  }

  @Override
  public void pause() {
    releaseInput();
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.025f, .035f, .05f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    if (runtime != null && runtime.frames().sequence() != uploaded) {
      uploaded = runtime.frames().copyRgba(pixels.getPixels());
      texture.draw(pixels, 0, 0);
    }
    batch.begin();
    if (viewport != null && uploaded > 0) {
      batch.draw(texture, viewport.left, viewport.bottom, viewport.width, viewport.height);
    }
    font.draw(
        batch,
        error == null ? "SWORDOL | original-compatible core / libGDX desktop" : error,
        12,
        38);
    String controls =
        "Arrows + Enter | F1/F2 soft keys | Esc back | F10 fullscreen | F11 PNG | F12 pixel scale";
    font.draw(batch, notice.isEmpty() ? controls : notice, 12, 17);
    if (error == null && uploaded == 0) {
      font.draw(batch, "Starting original client...", 24, Gdx.graphics.getHeight() / 2f);
    }
    batch.end();
    renderedFrames++;
    int stop = Integer.getInteger("swordol.smokeFrames", 0);
    if (stop > 0 && renderedFrames >= stop) {
      screenshot();
      if (uploaded == 0 || error != null) {
        throw new IllegalStateException("Classic desktop smoke did not produce a game frame");
      }
      Gdx.app.exit();
    }
  }

  private void screenshot() {
    String path =
        System.getProperty(
            "swordol.screenshot", "screenshots/classic-" + System.currentTimeMillis() + ".png");
    Pixmap screenshot =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      PixmapIO.PNG writer = new PixmapIO.PNG();
      try {
        writer.setFlipY(true);
        com.badlogic.gdx.files.FileHandle file =
            Gdx.files.absolute(new java.io.File(path).getAbsolutePath());
        file.parent().mkdirs();
        writer.write(file, screenshot);
        notice = "Saved PNG: " + file.name();
      } finally {
        writer.dispose();
      }
    } catch (java.io.IOException failure) {
      notice = "Cannot write PNG: " + failure.getClass().getSimpleName();
    } finally {
      screenshot.dispose();
    }
  }

  @Override
  public void dispose() {
    releaseInput();
    if (runtime != null) {
      runtime.close();
    }
    if (texture != null) {
      texture.dispose();
    }
    if (pixels != null) {
      pixels.dispose();
    }
    if (font != null) {
      font.dispose();
    }
    if (batch != null) {
      batch.dispose();
    }
  }
}
