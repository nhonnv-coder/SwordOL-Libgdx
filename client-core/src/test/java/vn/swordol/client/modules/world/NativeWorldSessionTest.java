package vn.swordol.client.modules.world;

import static org.junit.Assert.*;
import static vn.swordol.client.render.MapPlanTest.fixture;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import vn.swordol.protocol.wire.LoginPackets;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.Packet;
import vn.swordol.protocol.wire.TcpReader;
import vn.swordol.protocol.wire.Wire;
import vn.swordol.protocol.wire.WorldPackets;
import vn.swordol.server.gateway.GatewayServer;

/**
 * Drives the native session against the local gateway with the map resources captured from the real
 * server, so the whole1407 →1110 →1126 →1006 →1005 path runs over real sockets. Synthetic local
 * identity only; no live account and no live server.
 */
public class NativeWorldSessionTest {
  @Test
  public void entersTheCapturedMapAndMovesUnderServerAuthority() throws Exception {
    BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
    WorldState world = new WorldState();
    List<WorldPackets.Walk> walks = new ArrayList<>();
    List<Integer> referenceCount = new ArrayList<>();
    WorldListener listener =
        new WorldListener() {
          @Override
          public void mapLoaded(
              WorldPackets.MapIdentity identity,
              MapResource resource,
              MapReferences references,
              Map<Integer, byte[]> images) {
            referenceCount.add(references.size());
            world.map(identity, resource);
          }

          @Override
          public void mapUpdate(WorldPackets.MapUpdate update) {
            world.mapUpdate(update);
          }

          @Override
          public void user(WorldPackets.UserInfo info) {
            world.user(info);
          }

          @Override
          public void otherPlayer(WorldPackets.PlayerInfo info) {
            world.otherPlayer(info);
          }

          @Override
          public void walk(WorldPackets.Walk walk) {
            walks.add(walk);
            world.walk(walk);
          }
        };
    try (GatewayServer server = new GatewayServer(verifier(account()))) {
      server.start();
      try (GameSession session = new GameSession(callbacks::add, states::add, listener)) {
        enterWorld(server, session, states, callbacks);

        assertTrue(world.hasMap());
        assertEquals(1101400, world.mapId());
        assertEquals("Trung nguyên1Tuyến", world.mapName());
        assertEquals(74, world.map().columns());
        assertEquals(74, world.map().rows());
        assertEquals(Integer.valueOf(236), referenceCount.get(0));
        assertEquals(42, world.userId());
        assertEquals(20, world.userX());
        assertEquals(30, world.userY());
        assertEquals(1, world.portals().size());
        assertEquals("Sườn hắc mộc 1Tuyến", world.portals().get(0).name());

        assertTrue(world.step(WorldState.EAST));
        assertEquals(21, world.userX());
        session.requestWalk(WorldState.EAST, world.userX(), world.userY());
        // An accepted move needs no answer, exactly as the original client ignores its own walk.
        drain(callbacks, 400);
        assertTrue("an accepted step must not be corrected", walks.isEmpty());

        session.requestWalk(WorldState.EAST, 60, 60);
        WorldPackets.Walk correction = awaitWalk(walks, callbacks);
        assertEquals(WorldPackets.WALK_CORRECTED, correction.action());
        assertEquals(42, correction.id());
        assertEquals("the server keeps the position it accepted", 21, correction.x());
        assertEquals(30, correction.y());
        assertEquals(21, world.userX());
        assertEquals(30, world.userY());
      }
    }
  }

  @Test
  public void refusesAWalkForARoleTheConnectionDoesNotOwn() throws Exception {
    try (GatewayServer server = new GatewayServer(verifier(account()))) {
      server.start();
      try (Socket socket = rawEnteredSocket(server)) {
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();
        boolean sawUser = false;
        for (int frame = 0; frame < 32 && !sawUser; frame++) {
          Packet packet = TcpReader.read(input);
          assertNotNull("gateway closed before sending the world", packet);
          if (packet.opcode() == 1006) sawUser = true;
        }
        assertTrue(sawUser);
        output.write(Wire.tcpRequest(WorldPackets.walkRequest(99, 1, 21, 30, 1101400), 9));
        output.flush();
        socket.setSoTimeout(3000);
        assertNull("a walk for another role must end the connection", TcpReader.read(input));
      }
    }
  }

  private static GatewayServer.Account account() throws Exception {
    GatewayServer.World world =
        new GatewayServer.World(
            1101400,
            0,
            "Trung nguyên1Tuyến",
            fixture("map-data.bin"),
            fixture("map-ref.bin"),
            20,
            30,
            Collections.singletonList(new GatewayServer.Portal(2, 4, 26, "Sườn hắc mộc 1Tuyến")));
    return new GatewayServer.Account(
        7,
        "fixture",
        Collections.singletonList(new GatewayServer.Role(42, "Kiếm Khách", 0, world)));
  }

  private static GatewayServer.Authenticator verifier(GatewayServer.Account account) {
    return (name, password) ->
        "fixture".equals(name) && Arrays.equals(password, "fixture-only".toCharArray())
            ? account
            : null;
  }

  private static void enterWorld(
      GatewayServer server,
      GameSession session,
      BlockingQueue<GameSession.Snapshot> states,
      BlockingQueue<Runnable> callbacks)
      throws Exception {
    session.loadServers(server.accountEndpoint());
    GameSession.Snapshot ready = await(GameSession.Stage.SERVERS_READY, states, callbacks);
    session.login(ready.servers().get(0), "fixture", "fixture-only".toCharArray());
    await(GameSession.Stage.ROLES_READY, states, callbacks);
    session.enter(42);
    await(GameSession.Stage.IN_WORLD, states, callbacks);
  }

  /** Completes the account and gameplay handshake by hand, to send a packet the UI cannot send. */
  private static Socket rawEnteredSocket(GatewayServer server) throws Exception {
    byte[] request =
        Wire.httpRequest(
            LoginPackets.login("fixture", "fixture-only".toCharArray(), "local"), 0, 0, 1);
    java.net.HttpURLConnection http =
        (java.net.HttpURLConnection) server.accountEndpoint().toURL().openConnection();
    http.setRequestMethod("POST");
    http.setDoOutput(true);
    LoginPackets.Handoff handoff;
    try {
      try (OutputStream out = http.getOutputStream()) {
        out.write(request);
      }
      Wire.Batch batch;
      try (InputStream in = http.getInputStream()) {
        batch = Wire.readHttpResponse(in);
      }
      handoff = LoginPackets.handoff(batch.packets().get(0));
    } finally {
      http.disconnect();
    }
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(handoff.host(), handoff.port()), 4000);
    socket.setSoTimeout(5000);
    socket.getOutputStream().write(handoff.header());
    socket
        .getOutputStream()
        .write(
            Wire.tcpRequest(
                LoginPackets.connect(handoff, "fixture", "fixture-only".toCharArray(), 0), 1));
    socket.getOutputStream().flush();
    Packet roles = TcpReader.read(socket.getInputStream());
    assertNotNull(roles);
    assertEquals(1080, roles.opcode());
    socket.getOutputStream().write(Wire.tcpRequest(LoginPackets.enter(42), 2));
    socket.getOutputStream().flush();
    return socket;
  }

  private static GameSession.Snapshot await(
      GameSession.Stage stage,
      BlockingQueue<GameSession.Snapshot> states,
      BlockingQueue<Runnable> callbacks)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      Runnable callback = callbacks.poll(20, TimeUnit.MILLISECONDS);
      if (callback != null) callback.run();
      GameSession.Snapshot state = states.poll();
      while (state != null) {
        if (state.stage() == GameSession.Stage.ERROR && stage != GameSession.Stage.ERROR)
          throw new AssertionError("Session failed: " + state.status());
        if (state.stage() == stage) return state;
        state = states.poll();
      }
    }
    throw new AssertionError("Session never reached " + stage);
  }

  private static WorldPackets.Walk awaitWalk(
      List<WorldPackets.Walk> walks, BlockingQueue<Runnable> callbacks) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Runnable callback = callbacks.poll(20, TimeUnit.MILLISECONDS);
      if (callback != null) callback.run();
      if (!walks.isEmpty()) return walks.remove(0);
    }
    throw new AssertionError("No walk arrived");
  }

  private static void drain(BlockingQueue<Runnable> callbacks, long millis) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    while (System.nanoTime() < deadline) {
      Runnable callback = callbacks.poll(20, TimeUnit.MILLISECONDS);
      if (callback != null) callback.run();
    }
  }
}
