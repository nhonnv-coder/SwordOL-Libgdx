package vn.swordol.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import vn.swordol.client.SwordGame;

/** Explicit GL integration probe; run manually, never connects or enters account information. */
public final class NativeRenderProbe {
  private NativeRenderProbe() {}

  public static void main(String[] args) {
    System.setProperty("swordol.smokeFrames", "30");
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("SwordOL native render verification");
    config.setWindowedMode(960, 640);
    new Lwjgl3Application(
        new ApplicationAdapter() {
          private final SwordGame game = new SwordGame();

          @Override
          public void create() {
            game.create();
            JsonObject state =
                JsonParser.parseString(
                        "{\"player\":{\"name\":\"Local fixture\","
                            + "\"hp\":100,\"gold\":0,\"x\":2,\"y\":3},\"map\":{"
                            + "\"id\":\"world.demo-field\",\"width\":20,\"height\":15}}")
                    .getAsJsonObject();
            game.showState(state);
            Object first = sheets();
            check(first != null, "Initial map must have textures");
            state.getAsJsonObject("player").addProperty("x", 3);
            game.showState(state);
            check(sheets() == first, "Unchanged map update disposed or reloaded its textures");
            state.getAsJsonObject("map").addProperty("version", "different-version");
            game.showState(state);
            check(sheets() == null, "Mismatched version retained drawable textures");
            state.getAsJsonObject("map").remove("version");
            game.showState(state);
            check(sheets() != null, "Restored map failed to reload textures");
            System.out.println("NATIVE_RENDER_PROBE_PASS");
          }

          private Object sheets() {
            try {
              Field field = SwordGame.class.getDeclaredField("sheets");
              field.setAccessible(true);
              return field.get(game);
            } catch (ReflectiveOperationException ex) {
              throw new AssertionError(ex);
            }
          }

          @Override
          public void render() {
            game.render();
          }

          @Override
          public void resize(int width, int height) {
            game.resize(width, height);
          }

          @Override
          public void dispose() {
            game.dispose();
          }
        },
        config);
  }

  private static void check(boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
  }
}
