package vn.swordol.client.modules.world;

import static org.junit.Assert.*;
import static vn.swordol.client.render.MapPlanTest.fixture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import vn.swordol.client.modules.login.GameSession;
import vn.swordol.client.modules.login.LoginFacade;
import vn.swordol.client.modules.login.LoginView;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;
import vn.swordol.server.gateway.GatewayServer;

/**
 * The composed native session: input before a world exists must not throw, and the world screen
 * must not keep showing a world that has ended. PureMVC's Facade is a process singleton, so this
 * class composes exactly one and surefire gives it its own JVM.
 */
public class WorldScreenLifecycleTest {
  @Test
  public void ignoresWorldInputBeforeAWorldAndClearsTheScreenWhenTheSessionDies() throws Exception {
    BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    RecordingLoginView login = new RecordingLoginView();
    RecordingWorldView world = new RecordingWorldView();
    GatewayServer server = new GatewayServer(verifier());
    server.start();
    try (LoginFacade facade = new LoginFacade(callbacks::add)) {
      facade.attach(login);
      facade.attachWorld(world);
      // Nothing is connected yet, so world input must be ignored rather than thrown at the UI.
      world.input.step(WorldState.EAST);
      world.input.requestPlayer(5);
      assertEquals(0, world.maps);

      login.input.loadServers(server.accountEndpoint().toString());
      run(
          callbacks,
          () ->
              !login.snapshots.isEmpty() && last(login).stage() == GameSession.Stage.SERVERS_READY);
      login.input.login(last(login).servers().get(0), "fixture", "fixture-only".toCharArray());
      run(callbacks, () -> last(login).stage() == GameSession.Stage.ROLES_READY);
      login.input.enter(42);
      run(callbacks, () -> last(login).stage() == GameSession.Stage.IN_WORLD);
      assertEquals(1, world.maps);
      assertNotNull("the screen must have the world it is drawing", world.state);
      int clearedBeforeLoss = world.cleared;

      server.close();
      run(callbacks, () -> last(login).stage() == GameSession.Stage.ERROR);
      assertEquals(
          "a dead session must not leave a world on screen", clearedBeforeLoss + 1, world.cleared);
      assertNull(world.state);
    } finally {
      server.close();
    }
  }

  private static GatewayServer.Authenticator verifier() throws Exception {
    GatewayServer.World map =
        new GatewayServer.World(
            1101400,
            0,
            "Trung nguyên1Tuyến",
            fixture("map-data.bin"),
            fixture("map-ref.bin"),
            20,
            30,
            Collections.<GatewayServer.Portal>emptyList());
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Collections.singletonList(new GatewayServer.Role(42, "Kiếm Khách", 0, map)));
    return (name, password) ->
        "fixture".equals(name) && Arrays.equals(password, "fixture-only".toCharArray())
            ? account
            : null;
  }

  private static GameSession.Snapshot last(RecordingLoginView view) {
    return view.snapshots.get(view.snapshots.size() - 1);
  }

  private interface Condition {
    boolean reached();
  }

  private static void run(BlockingQueue<Runnable> callbacks, Condition condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      Runnable callback = callbacks.poll(20, TimeUnit.MILLISECONDS);
      if (callback != null) callback.run();
      if (condition.reached()) return;
    }
    throw new AssertionError("The session never reached the expected state");
  }

  private static final class RecordingLoginView implements LoginView {
    final List<GameSession.Snapshot> snapshots = new ArrayList<>();
    Input input;

    @Override
    public void setInput(Input input) {
      this.input = input;
    }

    @Override
    public void showSession(GameSession.Snapshot snapshot) {
      snapshots.add(snapshot);
    }

    @Override
    public void showInputError(String message) {}
  }

  private static final class RecordingWorldView implements WorldView {
    Input input;
    WorldState state;
    int maps, cleared, changed;

    @Override
    public void setWorldInput(Input input) {
      if (input != null) this.input = input;
    }

    @Override
    public void showMap(
        WorldPackets.MapIdentity identity,
        MapResource resource,
        MapReferences references,
        Map<Integer, byte[]> images) {
      maps++;
      assertEquals(74, resource.columns());
      assertEquals(236, references.size());
    }

    @Override
    public void showWorld(WorldState state) {
      this.state = state;
    }

    @Override
    public void mapChanged() {
      changed++;
    }

    @Override
    public void clearWorld() {
      cleared++;
      state = null;
    }
  }
}
