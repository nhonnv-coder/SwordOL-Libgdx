package vn.swordol.server;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import vn.swordol.assets.runtime.MapData;
import vn.swordol.protocol.*;

/** Deliberately simple demo rules, not recovered SwordOL combat/economy formulas. */
final class DemoSession {
  static final int REPLAY_LIMIT = 256;
  private final DemoWorld world;
  private String sessionId, name;
  private boolean entered;
  private MapData map;
  private String mapId, monsterId;
  private int x, y, monsterX, monsterY, monsterHp, gold;
  private long lastAttack;
  private final LinkedHashMap<String, Cached> replay =
      new LinkedHashMap<String, Cached>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> entry) {
          return size() > REPLAY_LIMIT;
        }
      };

  DemoSession(DemoWorld world) {
    this.world = world;
  }

  Response handle(Request request) {
    Cached previous = replay.get(request.requestId);
    if (previous != null) {
      if (previous.type.equals(request.type) && previous.payload.equals(request.payload))
        return previous.response;
      return Response.failure(
          request, "DUPLICATE_REQUEST", "requestId already used with different command");
    }
    Response response;
    try {
      response = dispatch(request);
    } catch (IllegalArgumentException ex) {
      response = Response.failure(request, "BAD_PAYLOAD", ex.getMessage());
    } catch (IOException ex) {
      response =
          Response.failure(request, "WORLD_UNAVAILABLE", "Cannot load map: " + ex.getMessage());
    }
    replay.put(request.requestId, new Cached(request, response));
    return response;
  }

  private Response dispatch(Request r) throws IOException {
    if ("PING".equals(r.type)) {
      JsonObject p = new JsonObject();
      p.addProperty("pong", true);
      return Response.success(r, p);
    }
    if ("LOGIN".equals(r.type)) {
      if (sessionId != null)
        return Response.failure(r, "ALREADY_LOGGED_IN", "This socket already has a demo session");
      String candidate = string(r.payload, "name");
      for (int index = 0; index < candidate.length(); index++) {
        if (Character.isISOControl(candidate.charAt(index))) {
          throw new IllegalArgumentException("Demo name must contain 1 to 24 printable characters");
        }
      }
      candidate = candidate.trim();
      if (candidate.isEmpty() || candidate.length() > 24)
        throw new IllegalArgumentException("Demo name must contain 1 to 24 printable characters");
      name = candidate;
      sessionId = UUID.randomUUID().toString();
      JsonObject p = new JsonObject();
      p.addProperty("sessionId", sessionId);
      return Response.success(r, p);
    }
    if (!"ENTER_WORLD".equals(r.type) && !"MOVE".equals(r.type) && !"ATTACK".equals(r.type))
      return Response.failure(
          r,
          "NOT_IMPLEMENTED",
          "TODO: original rules and legacy protocol are not reconstructed for " + r.type);
    if (sessionId == null)
      return Response.failure(r, "UNAUTHENTICATED", "LOGIN on this socket first");
    if ("ENTER_WORLD".equals(r.type)) {
      if (!entered) {
        enter(DemoWorld.START_MAP, null, null);
        entered = true;
      }
      return Response.success(r, state());
    }
    if (!entered) return Response.failure(r, "NOT_IN_WORLD", "ENTER_WORLD first");
    if ("MOVE".equals(r.type)) {
      int nextX = integer(r.payload, "x"), nextY = integer(r.payload, "y");
      if (!map.inBounds(nextX, nextY))
        return Response.failure(
            r, "OUT_OF_BOUNDS", "Map " + mapId + " is " + map.width() + " by " + map.height());
      if (map.isBlocked(nextX, nextY))
        return Response.failure(r, "BLOCKED", "That cell is blocked on " + mapId);
      x = nextX;
      y = nextY;
      MapData.Portal portal = map.portalAt(x, y);
      if (portal != null) enter(portal.targetMap(), portal.targetX(), portal.targetY());
      return Response.success(r, state());
    }
    if (monsterId == null || !monsterId.equals(string(r.payload, "targetId")))
      return Response.failure(r, "UNKNOWN_TARGET", "No such monster on " + mapId);
    if (monsterHp == 0)
      return Response.failure(r, "TARGET_DEAD", "Reward has already been granted");
    if (Math.abs(x - monsterX) + Math.abs(y - monsterY) > 1)
      return Response.failure(
          r, "OUT_OF_RANGE", "Demo attack requires Manhattan distance at most 1");
    long now = System.nanoTime();
    if (lastAttack != 0 && now - lastAttack < 250000000L)
      return Response.failure(r, "COOLDOWN", "Demo attack cooldown is 250 ms");
    lastAttack = now;
    monsterHp = Math.max(0, monsterHp - 10);
    if (monsterHp == 0) gold += 10;
    return Response.success(r, state());
  }

  /** Loads a map and places the player from its spawns, unless a portal chose the cell. */
  private void enter(String id, Integer atX, Integer atY) throws IOException {
    MapData loaded = world.map(id);
    Integer startX = atX, startY = atY;
    String monster = null;
    int spawnX = 0, spawnY = 0, spawnHp = 0;
    for (MapData.Spawn spawn : loaded.spawns()) {
      if (startX == null && "player-start".equals(spawn.kind())) {
        startX = spawn.x();
        startY = spawn.y();
      }
      if (monster == null && "monster".equals(spawn.kind())) {
        monster = spawn.id();
        spawnX = spawn.x();
        spawnY = spawn.y();
        spawnHp = spawn.hp();
      }
    }
    if (startX == null) throw new IOException("Map " + id + " has no player-start spawn");
    if (!loaded.inBounds(startX, startY) || loaded.isBlocked(startX, startY))
      throw new IOException("Map " + id + " would place the player on an unusable cell");
    map = loaded;
    mapId = id;
    x = startX;
    y = startY;
    monsterId = monster;
    monsterX = spawnX;
    monsterY = spawnY;
    monsterHp = spawnHp;
  }

  private JsonObject state() {
    JsonObject state = new JsonObject(), player = new JsonObject(), area = new JsonObject();
    player.addProperty("id", sessionId);
    player.addProperty("name", name);
    player.addProperty("x", x);
    player.addProperty("y", y);
    player.addProperty("hp", 100);
    player.addProperty("gold", gold);
    area.addProperty("id", mapId);
    area.addProperty("version", map.version());
    area.addProperty("width", map.width());
    area.addProperty("height", map.height());
    state.add("player", player);
    if (monsterId != null) {
      JsonObject monster = new JsonObject();
      monster.addProperty("id", monsterId);
      monster.addProperty("x", monsterX);
      monster.addProperty("y", monsterY);
      monster.addProperty("hp", monsterHp);
      state.add("monster", monster);
    }
    state.add("map", area);
    return state;
  }

  private static String string(JsonObject p, String key) {
    JsonElement value = p.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException(key + " must be a string");
    return value.getAsString();
  }

  private static int integer(JsonObject p, String key) {
    JsonElement value = p.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(key + " must be an integer");
    try {
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException ex) {
      throw new IllegalArgumentException(key + " must be a finite 32-bit integer");
    }
  }

  private static final class Cached {
    final String type;
    final JsonObject payload;
    final Response response;

    Cached(Request r, Response response) {
      this.type = r.type;
      this.payload = r.payload.deepCopy();
      this.response = response;
    }
  }
}
