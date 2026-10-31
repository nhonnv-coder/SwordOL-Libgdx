package vn.swordol.client.modules.login;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.concurrent.*;
import org.junit.Test;
import vn.swordol.protocol.wire.LoginPackets;
import vn.swordol.protocol.wire.RoleLooks;
import vn.swordol.server.gateway.GatewayServer;

public class GameSessionTest {
  @Test
  public void terminalFailureInvalidatesHttpAlreadyDecodedButNotPublished() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 4);
    CountDownLatch accepted = new CountDownLatch(1), release = new CountDownLatch(1);
    server.createContext(
        "/account",
        exchange -> {
          vn.swordol.protocol.wire.Wire.readHttpRequest(exchange.getRequestBody());
          accepted.countDown();
          try {
            release.await(3, TimeUnit.SECONDS);
            byte[] packet =
                new vn.swordol.protocol.wire.WireWriter(1077)
                    .integer(0)
                    .integer(0)
                    .signedByte(1)
                    .text("Local")
                    .text("local")
                    .signedByte(1)
                    .integer(1)
                    .build();
            byte[] response =
                vn.swordol.protocol.wire.Wire.httpResponse(Arrays.asList(packet), 1, 1);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
    BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    try (GameSession session = new GameSession(callbacks::add, states::add)) {
      session.loadServers(
          java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/account"));
      assertTrue(accepted.await(3, TimeUnit.SECONDS));
      synchronized (session) {
        release.countDown();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean blocked = false;
        while (System.nanoTime() < until && !blocked) {
          for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.getName().equals("native-login-io") && t.getState() == Thread.State.BLOCKED)
              blocked = true;
          if (!blocked) Thread.sleep(5);
        }
        assertTrue("Expected decoded HTTP worker to wait for session lock", blocked);
        java.lang.reflect.Field generation = GameSession.class.getDeclaredField("generation");
        generation.setAccessible(true);
        java.lang.reflect.Method fail =
            GameSession.class.getDeclaredMethod("fail", long.class, String.class);
        fail.setAccessible(true);
        fail.invoke(session, generation.getLong(session), "Controlled handshake timeout");
      }
      await(GameSession.Stage.ERROR, states, callbacks);
      for (int i = 0; i < 8; i++) {
        Runnable callback = callbacks.poll(50, TimeUnit.MILLISECONDS);
        if (callback != null) callback.run();
      }
      assertEquals("Timed-out work resumed", GameSession.Stage.ERROR, session.snapshot().stage());
    } finally {
      release.countDown();
      server.stop(0);
    }
  }

  @Test
  public void fourWaitingPlayersDoNotBlockAccountService() throws Exception {
    GatewayServer.Account user =
        new GatewayServer.Account(
            7,
            "fixture",
            Arrays.asList(new GatewayServer.Role(42, "Local role", 0, 1101400, "Trung nguyen")));
    java.util.List<GameSession> sessions = new java.util.ArrayList<>();
    try (GatewayServer server = new GatewayServer((name, password) -> user)) {
      server.start();
      for (int i = 0; i < 5; i++) {
        BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        GameSession session = new GameSession(callbacks::add, states::add);
        sessions.add(session);
        session.loadServers(server.accountEndpoint());
        GameSession.Snapshot list = await(GameSession.Stage.SERVERS_READY, states, callbacks);
        if (i < 4) {
          session.login(list.servers().get(0), "fixture", "fixture-only".toCharArray());
          await(GameSession.Stage.ROLES_READY, states, callbacks);
        }
      }
    } finally {
      for (GameSession session : sessions) session.close();
    }
  }

  @Test
  public void originalWireLoginSelectRoleEnterMapAndRejectBadPassword() throws Exception {
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Arrays.asList(new GatewayServer.Role(42, "Local role", 2, 1101400, "Trung nguyen")));
    try (GatewayServer server =
        new GatewayServer((name, password) ->
            name.equals("fixture") && Arrays.equals(password, "fixture-only".toCharArray())
                ? account
                : null)) {
      server.start();
      BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
      BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
      try (GameSession session = new GameSession(callbacks::add, states::add)) {
        session.loadServers(server.accountEndpoint());
        GameSession.Snapshot list = await(GameSession.Stage.SERVERS_READY, states, callbacks);
        session.login(list.servers().get(0), "fixture", "wrong".toCharArray());
        await(GameSession.Stage.ERROR, states, callbacks);
        session.loadServers(server.accountEndpoint());
        list = await(GameSession.Stage.SERVERS_READY, states, callbacks);
        char[] secret = "fixture-only".toCharArray();
        session.login(list.servers().get(0), "fixture", secret);
        for (char c : secret) assertEquals('\0', c);
        GameSession.Snapshot roles = await(GameSession.Stage.ROLES_READY, states, callbacks);
        assertEquals(42, roles.roles().get(0).id());
        assertEquals(2, roles.roles().get(0).slot());
        try {
          session.enter(99);
          fail("Role from another account was allowed");
        } catch (IllegalStateException expected) {
        }
        session.enter(42);
        GameSession.Snapshot map = await(GameSession.Stage.MAP_RECEIVED, states, callbacks);
        assertEquals(1101400, map.mapId());
        session.disconnect();
        await(GameSession.Stage.DISCONNECTED, states, callbacks);
      }
    }
  }

  @Test
  public void cancelledHttpDoesNotBlockCallerOrDeliverStaleState() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 4);
    CountDownLatch accepted = new CountDownLatch(1), release = new CountDownLatch(1);
    server.createContext(
        "/account",
        exchange -> {
          accepted.countDown();
          try {
            release.await(4, TimeUnit.SECONDS);
            exchange.sendResponseHeaders(500, -1);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
    BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    try (GameSession session = new GameSession(callbacks::add, states::add)) {
      session.loadServers(
          java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/account"));
      assertTrue(accepted.await(3, TimeUnit.SECONDS));
      long begin = System.nanoTime();
      session.disconnect();
      assertTrue(
          "Disconnect blocked UI", System.nanoTime() - begin < TimeUnit.MILLISECONDS.toNanos(300));
      release.countDown();
      await(GameSession.Stage.DISCONNECTED, states, callbacks);
      Runnable callback;
      while ((callback = callbacks.poll(100, TimeUnit.MILLISECONDS)) != null) callback.run();
      assertTrue(states.isEmpty());
    } finally {
      release.countDown();
      server.stop(0);
    }
  }

  @Test
  public void createsACharacterOnTheGatewayAndSeesItInTheRoleList() throws Exception {
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Arrays.asList(new GatewayServer.Role(42, "Local role", 0, 1101400, "Trung nguyen")));
    try (GatewayServer server =
        new GatewayServer((name, password) ->
            name.equals("fixture") && Arrays.equals(password, "fixture-only".toCharArray())
                ? account
                : null)) {
      server.start();
      BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
      BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
      try (GameSession session = new GameSession(callbacks::add, states::add)) {
        session.loadServers(server.accountEndpoint());
        GameSession.Snapshot list = await(GameSession.Stage.SERVERS_READY, states, callbacks);
        session.login(list.servers().get(0), "fixture", "fixture-only".toCharArray());
        assertEquals(1, await(GameSession.Stage.ROLES_READY, states, callbacks).roles().size());

        session.createRole("Tan Thu", RoleLooks.MALE, 1, 1);
        GameSession.Snapshot after = awaitRoleCount(2, states, callbacks);
        LoginPackets.Role created = after.roles().get(1);
        assertEquals("Tan Thu", created.name());
        assertEquals(1, created.slot());
        assertEquals(RoleLooks.lookFace(1, RoleLooks.MALE), created.lookId());
        assertNotEquals(42, created.id());

        // The created character is a real role: entering it serves a world like any other.
        session.enter(created.id());
        await(GameSession.Stage.ENTERING_WORLD, states, callbacks);
      }
    }
  }

  @Test
  public void refusesToCreateIntoAnOccupiedSlot() throws Exception {
    GatewayServer.Account account =
        new GatewayServer.Account(
            7,
            "fixture",
            Arrays.asList(new GatewayServer.Role(42, "Local role", 0, 1101400, "Trung nguyen")));
    try (GatewayServer server = new GatewayServer((name, password) -> account)) {
      server.start();
      BlockingQueue<GameSession.Snapshot> states = new LinkedBlockingQueue<>();
      BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
      try (GameSession session = new GameSession(callbacks::add, states::add)) {
        session.loadServers(server.accountEndpoint());
        GameSession.Snapshot list = await(GameSession.Stage.SERVERS_READY, states, callbacks);
        session.login(list.servers().get(0), "fixture", "fixture-only".toCharArray());
        await(GameSession.Stage.ROLES_READY, states, callbacks);
        // Slot 0 already holds a role; the gateway drops the connection rather than overwriting.
        session.createRole("Tan Thu", RoleLooks.MALE, 1, 0);
        await(GameSession.Stage.ERROR, states, callbacks);
      }
    }
  }

  private static GameSession.Snapshot awaitRoleCount(
      int wanted, BlockingQueue<GameSession.Snapshot> states, BlockingQueue<Runnable> callbacks)
      throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < until) {
      Runnable run = callbacks.poll(50, TimeUnit.MILLISECONDS);
      if (run != null) run.run();
      GameSession.Snapshot s;
      while ((s = states.poll()) != null) if (s.roles().size() == wanted) return s;
    }
    throw new AssertionError("Role list never reached " + wanted + " roles");
  }

  private static GameSession.Snapshot await(
      GameSession.Stage wanted,
      BlockingQueue<GameSession.Snapshot> states,
      BlockingQueue<Runnable> callbacks)
      throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < until) {
      Runnable run = callbacks.poll(50, TimeUnit.MILLISECONDS);
      if (run != null) run.run();
      GameSession.Snapshot s;
      while ((s = states.poll()) != null) if (s.stage() == wanted) return s;
    }
    throw new AssertionError("Missing session stage: " + wanted);
  }
}
