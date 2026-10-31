package vn.swordol.classic;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ClassicLauncher {
  private ClassicLauncher() {}

  public static void main(String[] args) {
    Path jar = Paths.get(System.getProperty("swordol.clientJar", "docs/files/VoLamHoiTu.jar"));
    int width = Integer.getInteger("swordol.logicalWidth", 640);
    int height = Integer.getInteger("swordol.logicalHeight", 480);
    if (width < 240 || height < 240 || width > 1280 || height > 960) {
      throw new IllegalArgumentException("Unsupported logical resolution");
    }
    final ClassicApplication app = new ClassicApplication(jar, width, height);
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("SwordOL | libGDX Desktop - Original Compatibility");
    config.setWindowedMode(1024, 820);
    config.setWindowSizeLimits(480, 400, -1, -1);
    config.useVsync(true);
    config.setForegroundFPS(60);
    config.setIdleFPS(20);
    config.setWindowListener(
        new Lwjgl3WindowAdapter() {
          @Override
          public void focusLost() {
            app.releaseInput();
          }
        });
    new Lwjgl3Application(app, config);
    // Original JavaME workers may ignore interruption. This host owns the process lifecycle.
    System.exit(0);
  }
}
