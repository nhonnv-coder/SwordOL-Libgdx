package vn.swordol.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.Collections;
import vn.swordol.assets.runtime.UiLayout;
import vn.swordol.client.render.ImageSet;
import vn.swordol.client.ui.NativeLoginGame;
import vn.swordol.client.ui.legacy.LegacyImages;
import vn.swordol.client.ui.legacy.LegacySkin;
import vn.swordol.client.ui.legacy.LegacyWindow;

/**
 * Draws one original window straight from its decoded layout, with no coordinates of this build's
 * own, and saves the frame. Arguments: window id, screenshot, legacy asset bundle or directory.
 */
public final class LegacyWindowProbe {
  private LegacyWindowProbe() {}

  public static void main(String[] args) throws Exception {
    final int windowId = Integer.parseInt(args[0]);
    final String target = args[1];
    final String assets = args[2];
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("SwordOL legacy window " + windowId);
    config.setWindowedMode(1280, 720);
    new Lwjgl3Application(
        new ApplicationAdapter() {
          Stage stage;
          int frames;

          @Override
          public void create() {
            try {
              ImageSet images = ImageSet.open(Collections.<Integer, byte[]>emptyMap(), assets);
              byte[] document = images.readUi(windowId);
              if (document == null) throw new AssertionError("No layout for window " + windowId);
              UiLayout layout = UiLayout.read(document);
              // The original draws every window on this one surface, so the viewport is the window.
              stage = new Stage(new FitViewport(layout.width(), layout.height()));
              LegacyImages pictures = new LegacyImages(images);
              LegacyWindow window = new LegacyWindow(layout, LegacySkin.create(), pictures);
              stage.addActor(window);
              System.out.println(
                  "LEGACY_WINDOW "
                      + windowId
                      + " "
                      + layout.width()
                      + "x"
                      + layout.height()
                      + " controls="
                      + layout.controls().size()
                      + " missingImages="
                      + pictures.missingImages());
            } catch (Exception ex) {
              throw new AssertionError("Legacy window probe failed", ex);
            }
          }

          @Override
          public void render() {
            ScreenUtils.clear(.02f, .03f, .05f, 1);
            stage.act(Gdx.graphics.getDeltaTime());
            stage.draw();
            if (++frames == 20) {
              NativeLoginGame.saveScreenshot(target);
              System.out.println("LEGACY_WINDOW_PROBE_PASS");
              Gdx.app.exit();
            }
          }

          @Override
          public void resize(int width, int height) {
            stage.getViewport().update(width, height, true);
          }
        },
        config);
  }
}
