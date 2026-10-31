package vn.swordol.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.swordol.client.modules.login.GameSession;
import vn.swordol.client.ui.NativeLoginGame;
import vn.swordol.server.gateway.GatewayServer;

/** Manual GL integration probe. Only synthetic identities against its own loopback server. */
public final class NativeLoginProbe {
  private NativeLoginProbe() {}

  public static void main(String[] args) throws Exception {
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Collections.singletonList(
                new GatewayServer.Role(42, "Kiếm Khách", 2, 1101400, "Trung nguyên")));
    final boolean[] done = {false};
    try (GatewayServer server =
        new GatewayServer((name, password) ->
            name.equals("fixture") && Arrays.equals(password, "fixture-only".toCharArray())
                ? account
                : null)) {
      server.start();
      Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
      config.setTitle("SwordOL native login verification");
      config.setWindowedMode(1060, 720);
      new Lwjgl3Application(
          new ApplicationAdapter() {
            final NativeLoginGame game = new NativeLoginGame();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean signedIn, entered;

            @Override
            public void create() {
              game.create();
              text("endpoint").setText(server.accountEndpoint().toString());
              click("load");
            }

            @Override
            public void render() {
              game.render();
              GameSession.Snapshot state = (GameSession.Snapshot) field("snapshot");
              if (state.stage() == GameSession.Stage.ERROR)
                throw new AssertionError("Native login probe connection failed");
              if (state.stage() == GameSession.Stage.SERVERS_READY && !signedIn) {
                signedIn = true;
                text("account").setText("fixture");
                text("password").setText("fixture-only");
                click("login");
                if (!text("password").getText().isEmpty())
                  throw new AssertionError("Password remained in UI");
              } else if (state.stage() == GameSession.Stage.ROLES_READY && !entered) {
                entered = true;
                NativeLoginGame.saveScreenshot(args[0]);
                click("enter");
              } else if (state.stage() == GameSession.Stage.MAP_RECEIVED) {
                if (state.mapId() != 1101400) throw new AssertionError("Wrong map");
                NativeLoginGame.saveScreenshot(args[1]);
                done[0] = true;
                System.out.println("NATIVE_LOGIN_PROBE_PASS");
                Gdx.app.exit();
              }
              if (System.nanoTime() > deadline)
                throw new AssertionError("Native login probe timed out");
            }

            private Object field(String name) {
              try {
                Field f = NativeLoginGame.class.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(game);
              } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
              }
            }

            private TextField text(String name) {
              return (TextField) field(name);
            }

            private void click(String name) {
              ((TextButton) field(name)).fire(new ChangeListener.ChangeEvent());
            }

            @Override
            public void resize(int w, int h) {
              game.resize(w, h);
            }

            @Override
            public void dispose() {
              game.dispose();
            }
          },
          config);
    }
    if (!done[0]) throw new AssertionError("Native flow did not reach map announcement");
  }
}
