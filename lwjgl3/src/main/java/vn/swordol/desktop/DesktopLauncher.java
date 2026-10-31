package vn.swordol.desktop;

import com.badlogic.gdx.backends.lwjgl3.*;
import vn.swordol.client.SwordGame;
import vn.swordol.client.ui.NativeLoginGame;

public final class DesktopLauncher {
  private DesktopLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle(
        Boolean.getBoolean("swordol.demo") ? "SwordOL · Local Demo" : "SwordOL · Native Client");
    config.setWindowedMode(1060, 720);
    config.setResizable(true);
    config.useVsync(true);
    config.setForegroundFPS(60);
    new Lwjgl3Application(
        Boolean.getBoolean("swordol.demo") ? new SwordGame() : new NativeLoginGame(), config);
  }
}
