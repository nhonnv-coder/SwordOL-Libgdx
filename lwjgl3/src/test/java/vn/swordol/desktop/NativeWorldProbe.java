package vn.swordol.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import vn.swordol.client.modules.login.GameSession;
import vn.swordol.client.modules.world.WorldState;
import vn.swordol.client.modules.world.WorldView;
import vn.swordol.client.render.WorldRenderer;
import vn.swordol.client.ui.NativeLoginGame;
import vn.swordol.client.ui.NativeWorldScreen;
import vn.swordol.server.gateway.GatewayServer;

/**
 * Manual GL integration probe for the native world: logs in to its own loopback gateway, which
 * serves the map resources captured from the real server, then walks the character on the rendered
 * map through the same input path the keyboard uses. Synthetic identity only, no live server.
 *
 * <p>Arguments: map screenshot, walked screenshot, map-data file, map-ref file, legacy image set.
 */
public final class NativeWorldProbe {
  private NativeWorldProbe() {}

  public static void main(String[] args) throws Exception {
    byte[] mapData = Files.readAllBytes(Paths.get(args[2]));
    byte[] mapRef = Files.readAllBytes(Paths.get(args[3]));
    System.setProperty("swordol.legacyAssets", Paths.get(args[4]).toAbsolutePath().toString());
    GatewayServer.World world =
        new GatewayServer.World(
            1101400,
            0,
            "Trung nguyên1Tuyến",
            mapData,
            mapRef,
            20,
            30,
            Collections.singletonList(new GatewayServer.Portal(2, 4, 26, "Sườn hắc mộc 1Tuyến")));
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Collections.singletonList(new GatewayServer.Role(42, "Kiếm Khách", 0, world)));
    final boolean[] done = {false};
    try (GatewayServer server =
        new GatewayServer((name, password) ->
            name.equals("fixture") && Arrays.equals(password, "fixture-only".toCharArray())
                ? account
                : null)) {
      server.start();
      Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
      config.setTitle("SwordOL native world verification");
      config.setWindowedMode(1060, 720);
      new Lwjgl3Application(
          new ApplicationAdapter() {
            final NativeLoginGame game = new NativeLoginGame();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            boolean signedIn, entered, captured;
            int steps, settle;

            @Override
            public void create() {
              game.create();
              text("endpoint").setText(server.accountEndpoint().toString());
              click("load");
            }

            @Override
            public void render() {
              game.render();
              GameSession.Snapshot state =
                  (GameSession.Snapshot) field(game, NativeLoginGame.class, "snapshot");
              if (state.stage() == GameSession.Stage.ERROR)
                throw new AssertionError("World probe failed: " + state.status());
              if (state.stage() == GameSession.Stage.SERVERS_READY && !signedIn) {
                signedIn = true;
                text("account").setText("fixture");
                text("password").setText("fixture-only");
                click("login");
              } else if (state.stage() == GameSession.Stage.ROLES_READY && !entered) {
                entered = true;
                click("enter");
              } else if (state.stage() == GameSession.Stage.IN_WORLD) {
                NativeWorldScreen screen =
                    (NativeWorldScreen) field(game, NativeLoginGame.class, "worldScreen");
                if (!screen.isShowing()) return;
                WorldState world = (WorldState) field(screen, NativeWorldScreen.class, "world");
                WorldRenderer renderer =
                    (WorldRenderer) field(screen, NativeWorldScreen.class, "renderer");
                if (!captured) {
                  captured = true;
                  if (renderer.plan().draws().size() < 1000)
                    throw new AssertionError(
                        "Map drew only " + renderer.plan().draws().size() + " images");
                  if (!renderer.missingImages().isEmpty())
                    throw new AssertionError("Missing map images: " + renderer.missingImages());
                  if (!renderer.warnings().isEmpty())
                    throw new AssertionError("Map warnings: " + renderer.warnings());
                  if (world.userX() != 20 || world.userY() != 30)
                    throw new AssertionError(
                        "Server spawn not applied: " + world.userX() + "," + world.userY());
                  NativeLoginGame.saveScreenshot(args[0]);
                  return;
                }
                if (steps < 3) {
                  steps++;
                  ((WorldView.Input) field(screen, NativeWorldScreen.class, "input"))
                      .step(WorldState.EAST);
                  return;
                }
                if (++settle < 60) return;
                // The third step runs into a cell the captured map blocks, so the client keeps22,30
                // and
                // the server never has to correct it.
                if (renderer.map().walkable(23, 30))
                  throw new AssertionError("Fixture changed: cell23,30 is no longer blocked");
                if (world.userX() != 22 || world.userY() != 30)
                  throw new AssertionError(
                      "Unexpected position after walking: " + world.userX() + "," + world.userY());
                NativeLoginGame.saveScreenshot(args[1]);
                done[0] = true;
                System.out.println(
                    "NATIVE_WORLD_PROBE_PASS draws="
                        + renderer.plan().draws().size()
                        + " cell="
                        + world.userX()
                        + ","
                        + world.userY());
                Gdx.app.exit();
              }
              if (System.nanoTime() > deadline) throw new AssertionError("World probe timed out");
            }

            private Object field(Object target, Class<?> type, String name) {
              try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
              } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
              }
            }

            private TextField text(String name) {
              return (TextField) field(game, NativeLoginGame.class, name);
            }

            private void click(String name) {
              ((TextButton) field(game, NativeLoginGame.class, name))
                  .fire(new ChangeListener.ChangeEvent());
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
    if (!done[0]) throw new AssertionError("Native world probe did not finish");
  }
}
