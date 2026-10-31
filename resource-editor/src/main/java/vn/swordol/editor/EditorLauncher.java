package vn.swordol.editor;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop entry point for the map editor. */
public final class EditorLauncher {
  private EditorLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
    configuration.setTitle("SwordOL resource editor");
    configuration.setWindowedMode(1360, 820);
    configuration.useVsync(true);
    new Lwjgl3Application(
        new EditorApp(EditorApp.defaultProject(), System.getProperty("swordol.editor.map")),
        configuration);
  }
}
