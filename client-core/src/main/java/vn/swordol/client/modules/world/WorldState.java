package vn.swordol.client.modules.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.swordol.client.render.WorldMap;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * The world as this client currently knows it: the loaded map, the user's own entity, the other
 * players inside the original's window, and the portals the server announced. Rules follow the
 * original client: MapObject.checkmove refuses blocked cells and the first row/column,
 * processWalkCmd treats action3 as the authoritative correction and ignores its own requested move
 * coming back, and drops any other player further than24 cells away. Pure state: it holds no
 * socket, builds request bytes for its caller to send, and never touches GL.
 */
public final class WorldState {
  /** Direction values MapObject.checkmove assigns:0 is +y,1 is +x,2 is -y,3 is -x. */
  public static final int SOUTH = 0, EAST = 1, NORTH = 2, WEST = 3;

  /** processWalkCmd keeps other players only inside this many cells. */
  public static final int WINDOW = 24;

  private WorldMap map;
  private int mapId, resourceId, mapType;
  private String mapName = "";
  private int userId, userX, userY, userDirection, userLook;
  private String userName = "";
  private final Map<Integer, Player> others = new LinkedHashMap<>();
  private List<WorldPackets.Portal> portals = Collections.emptyList();
  private final Deque<Integer> playerRequests = new ArrayDeque<>();
  private byte[] walkRequest;

  public void map(WorldPackets.MapIdentity identity, MapResource resource) {
    Objects.requireNonNull(identity, "identity");
    map = new WorldMap(resource);
    mapId = identity.mapId();
    resourceId = identity.resourceId();
    mapType = identity.mapType();
    mapName = identity.name();
    others.clear();
    portals = Collections.emptyList();
    playerRequests.clear();
    walkRequest = null;
  }

  public void leave() {
    map = null;
    mapId = 0;
    resourceId = 0;
    mapType = 0;
    mapName = "";
    userId = 0;
    userX = 0;
    userY = 0;
    userDirection = SOUTH;
    userName = "";
    userLook = 0;
    others.clear();
    portals = Collections.emptyList();
    playerRequests.clear();
    walkRequest = null;
  }

  /**
   * The user's own entity from1006/1082 action2. Captured sessions send it before the map
   * resources, so it is kept whether or not a map is loaded yet, and a later map load does not drop
   * it.
   */
  public void user(WorldPackets.UserInfo info) {
    if (info == null) return;
    userId = info.id();
    userName = info.name();
    userLook = info.look();
    userX = info.x();
    userY = info.y();
  }

  public void otherPlayer(WorldPackets.PlayerInfo info) {
    if (info == null || info.id() == userId || info.id() <= 0) return;
    others.put(info.id(), new Player(info.id(), info.name(), info.look(), info.x(), info.y()));
  }

  /**
   * One walk frame. Captured frames carry0 in the map field, so0 means the current map; a different
   * map id is dropped rather than applied to the map on screen.
   */
  public void walk(WorldPackets.Walk walk) {
    if (walk == null) return;
    if (walk.mapId() != 0 && walk.mapId() != mapId) return;
    if (walk.action() != WorldPackets.WALK_REQUESTED
        && walk.action() != WorldPackets.WALK_CORRECTED) return;
    if (walk.id() == userId) {
      if (walk.action() == WorldPackets.WALK_CORRECTED
          && (map == null || map.holds(walk.x(), walk.y()))) {
        userX = walk.x();
        userY = walk.y();
      }
      return;
    }
    Player player = others.get(walk.id());
    boolean outside = Math.abs(userX - walk.x()) > WINDOW || Math.abs(userY - walk.y()) > WINDOW;
    if (player != null) {
      if (outside) others.remove(walk.id());
      else player.moveTo(walk.x(), walk.y(), walk.direction());
      return;
    }
    if (!outside && walk.id() > 0 && !playerRequests.contains(walk.id()))
      playerRequests.add(walk.id());
  }

  public void mapUpdate(WorldPackets.MapUpdate update) {
    if (map == null || update == null) return;
    if (update.action() == 0) {
      portals = update.portals();
      return;
    }
    for (WorldPackets.CellUpdate cell : update.cells())
      map.applyCell(cell.x(), cell.y(), cell.blocked(), cell.flightBlocked());
    WorldPackets.TileUpdate tile = update.tile();
    if (tile != null) map.applyTile(tile.x(), tile.y(), tile.tile(), tile.rotated());
  }

  /**
   * Moves the user one cell if the original would allow it and queues the walk the original sends.
   * The server can still correct the position afterwards.
   */
  public boolean step(int direction) {
    if (map == null || userId == 0) return false;
    int targetX = userX, targetY = userY;
    switch (direction) {
      case SOUTH:
        targetY++;
        break;
      case EAST:
        targetX++;
        break;
      case NORTH:
        targetY--;
        break;
      case WEST:
        targetX--;
        break;
      default:
        return false;
    }
    if (!map.walkable(targetX, targetY)) return false;
    userX = targetX;
    userY = targetY;
    userDirection = direction;
    walkRequest = WorldPackets.walkRequest(userId, direction, userX, userY, mapId);
    return true;
  }

  /** The walk bytes the caller must send, or null when there is nothing to send. */
  public byte[] takeWalkRequest() {
    byte[] pending = walkRequest;
    walkRequest = null;
    return pending;
  }

  /** A1014 request for a player the server mentioned but this client does not know yet. */
  public byte[] takePlayerRequest() {
    Integer id = playerRequests.poll();
    return id == null ? null : WorldPackets.playerRequest(id);
  }

  public boolean hasMap() {
    return map != null;
  }

  public WorldMap map() {
    return map;
  }

  public int mapId() {
    return mapId;
  }

  public int resourceId() {
    return resourceId;
  }

  public int mapType() {
    return mapType;
  }

  public String mapName() {
    return mapName;
  }

  public int userId() {
    return userId;
  }

  public String userName() {
    return userName;
  }

  public int userLook() {
    return userLook;
  }

  public int userX() {
    return userX;
  }

  public int userY() {
    return userY;
  }

  public int userDirection() {
    return userDirection;
  }

  public List<WorldPackets.Portal> portals() {
    return portals;
  }

  public List<Player> otherPlayers() {
    return Collections.unmodifiableList(new ArrayList<>(others.values()));
  }

  public static final class Player {
    private final int id, look;
    private final String name;
    private int x, y, direction;

    Player(int id, String name, int look, int x, int y) {
      this.id = id;
      this.name = name;
      this.look = look;
      this.x = x;
      this.y = y;
    }

    void moveTo(int x, int y, int direction) {
      this.x = x;
      this.y = y;
      this.direction = direction;
    }

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    public int look() {
      return look;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int direction() {
      return direction;
    }
  }
}
