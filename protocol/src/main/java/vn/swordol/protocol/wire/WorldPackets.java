package vn.swordol.protocol.wire;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * World schemas taken from the original readers: processMapInfoCmd (1110), processUserInfoCmd (1006
 * and1082 action2), processOtherPlayerCmd (1014), processWalkCmd (1005) and processMapPortalCmd
 * (1126), with Engine.packetUserWalk for the outbound walk. Field meanings are only those the
 * client itself reads; nothing else in these packets is interpreted here.
 */
public final class WorldPackets {
  /** Walk fields pack one value: action*10000000 + direction*1000000 + x*1000 + y. */
  public static final int WALK_REQUESTED = 0, WALK_CORRECTED = 3;

  private WorldPackets() {}

  public static MapIdentity identity(Packet packet) throws IOException {
    require(packet, 1110, 4);
    return new MapIdentity(
        LoginPackets.number(packet, 0),
        LoginPackets.number(packet, 1),
        LoginPackets.number(packet, 2),
        LoginPackets.text(packet, 3));
  }

  /** The user's own entity; opcode1006 and1082 share processUserInfoCmd and its action byte. */
  public static UserInfo user(Packet packet) throws IOException {
    if (packet == null || (packet.opcode() != 1006 && packet.opcode() != 1082))
      throw new IOException("Not a user info packet");
    if (packet.fields().size() != 49 || LoginPackets.number(packet, 0) != 2)
      throw new IOException("Unsupported user info schema");
    return new UserInfo(
        LoginPackets.number(packet, 2),
        LoginPackets.text(packet, 4),
        LoginPackets.number(packet, 3),
        LoginPackets.number(packet, 1),
        LoginPackets.number(packet, 34),
        cell(LoginPackets.number(packet, 35)),
        cell(LoginPackets.number(packet, 36)),
        LoginPackets.number(packet, 47));
  }

  public static PlayerInfo otherPlayer(Packet packet) throws IOException {
    require(packet, 1014, 37);
    return new PlayerInfo(
        LoginPackets.number(packet, 0),
        LoginPackets.text(packet, 17),
        LoginPackets.number(packet, 1),
        LoginPackets.number(packet, 3),
        cell(LoginPackets.number(packet, 6)),
        cell(LoginPackets.number(packet, 7)),
        LoginPackets.number(packet, 35));
  }

  public static Walk walk(Packet packet) throws IOException {
    require(packet, 1005, 3);
    int packed = LoginPackets.number(packet, 1);
    if (packed < 0) throw new IOException("Invalid walk field");
    return new Walk(
        LoginPackets.number(packet, 0),
        packed / 10000000,
        packed / 1000000 % 10,
        cell(packed / 1000 % 1000),
        cell(packed % 1000),
        LoginPackets.number(packet, 2));
  }

  public static MapUpdate mapUpdate(Packet packet) throws IOException {
    if (packet == null || packet.opcode() != 1126 || packet.fields().isEmpty())
      throw new IOException("Not a map update packet");
    int action = LoginPackets.number(packet, 0);
    if (action == 0) {
      int count = LoginPackets.number(packet, 1);
      if (count < 0 || packet.fields().size() != 2 + 4 * count)
        throw new IOException("Invalid portal list");
      List<Portal> portals = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        int at = 2 + index * 4;
        portals.add(
            new Portal(
                LoginPackets.number(packet, at),
                cell(LoginPackets.number(packet, at + 1)),
                cell(LoginPackets.number(packet, at + 2)),
                LoginPackets.text(packet, at + 3)));
      }
      return new MapUpdate(action, portals, Collections.<CellUpdate>emptyList(), null);
    }
    if (action == 2) {
      int count = LoginPackets.number(packet, 1);
      if (count < 0 || packet.fields().size() != 2 + 3 * count)
        throw new IOException("Invalid cell update");
      List<CellUpdate> cells = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        int at = 2 + index * 3, sign = LoginPackets.number(packet, at + 2);
        cells.add(
            new CellUpdate(
                cell(LoginPackets.number(packet, at)),
                cell(LoginPackets.number(packet, at + 1)),
                sign % 10 != 0,
                sign / 10 != 0));
      }
      return new MapUpdate(action, Collections.<Portal>emptyList(), cells, null);
    }
    if (action == 3) {
      if (packet.fields().size() != 5) throw new IOException("Invalid tile update");
      int rotation = LoginPackets.number(packet, 4);
      if (rotation != 0 && rotation != 1) throw new IOException("Invalid tile rotation");
      return new MapUpdate(
          action,
          Collections.<Portal>emptyList(),
          Collections.<CellUpdate>emptyList(),
          new TileUpdate(
              cell(LoginPackets.number(packet, 1)),
              cell(LoginPackets.number(packet, 2)),
              LoginPackets.number(packet, 3) & 255,
              rotation == 1));
    }
    throw new IOException("Unsupported map update action");
  }

  /** The walk the original client sends for its own move; action is always the requested move. */
  public static byte[] walkRequest(int roleId, int direction, int x, int y, int mapId) {
    if (roleId <= 0 || direction < 0 || direction > 7 || x < 0 || x > 999 || y < 0 || y > 999)
      throw new IllegalArgumentException("Invalid walk request");
    return new WireWriter(1005)
        .integer(roleId)
        .integer(direction * 1000000 + x * 1000 + y)
        .integer(mapId)
        .build();
  }

  public static byte[] playerRequest(int id) {
    if (id <= 0) throw new IllegalArgumentException("Invalid player id");
    return new WireWriter(1014).integer(id).build();
  }

  private static void require(Packet packet, int opcode, int fields) throws IOException {
    if (packet == null || packet.opcode() != opcode)
      throw new IOException("Unexpected world opcode");
    if (packet.fields().size() != fields) throw new IOException("Unsupported world packet schema");
  }

  private static int cell(int value) throws IOException {
    if (value < 0 || value > 999) throw new IOException("Cell coordinate outside the packet range");
    return value;
  }

  public static final class MapIdentity {
    private final int mapId, resourceId, mapType;
    private final String name;

    MapIdentity(int mapId, int resourceId, int mapType, String name) {
      this.mapId = mapId;
      this.resourceId = resourceId;
      this.mapType = mapType;
      this.name = name;
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

    public String name() {
      return name;
    }
  }

  public static final class UserInfo {
    private final int id, look, hair, weaponType, x, y, moveSpeed;
    private final String name;

    UserInfo(int id, String name, int look, int hair, int weaponType, int x, int y, int moveSpeed) {
      this.id = id;
      this.name = name;
      this.look = look;
      this.hair = hair;
      this.weaponType = weaponType;
      this.x = x;
      this.y = y;
      this.moveSpeed = moveSpeed;
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

    public int hair() {
      return hair;
    }

    public int weaponType() {
      return weaponType;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int moveSpeed() {
      return moveSpeed;
    }
  }

  public static final class PlayerInfo {
    private final int id, look, weaponType, x, y, moveSpeed;
    private final String name;

    PlayerInfo(int id, String name, int look, int weaponType, int x, int y, int moveSpeed) {
      this.id = id;
      this.name = name;
      this.look = look;
      this.weaponType = weaponType;
      this.x = x;
      this.y = y;
      this.moveSpeed = moveSpeed;
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

    public int weaponType() {
      return weaponType;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int moveSpeed() {
      return moveSpeed;
    }
  }

  public static final class Walk {
    private final int id, action, direction, x, y, mapId;

    Walk(int id, int action, int direction, int x, int y, int mapId) {
      this.id = id;
      this.action = action;
      this.direction = direction;
      this.x = x;
      this.y = y;
      this.mapId = mapId;
    }

    public int id() {
      return id;
    }

    public int action() {
      return action;
    }

    public int direction() {
      return direction;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int mapId() {
      return mapId;
    }
  }

  public static final class MapUpdate {
    private final int action;
    private final List<Portal> portals;
    private final List<CellUpdate> cells;
    private final TileUpdate tile;

    MapUpdate(int action, List<Portal> portals, List<CellUpdate> cells, TileUpdate tile) {
      this.action = action;
      this.tile = tile;
      this.portals = Collections.unmodifiableList(portals);
      this.cells = Collections.unmodifiableList(cells);
    }

    public int action() {
      return action;
    }

    public List<Portal> portals() {
      return portals;
    }

    public List<CellUpdate> cells() {
      return cells;
    }

    public TileUpdate tile() {
      return tile;
    }
  }

  public static final class Portal {
    private final int id, x, y;
    private final String name;

    Portal(int id, int x, int y, String name) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.name = name;
    }

    public int id() {
      return id;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public String name() {
      return name;
    }
  }

  public static final class CellUpdate {
    private final int x, y;
    private final boolean blocked, flightBlocked;

    CellUpdate(int x, int y, boolean blocked, boolean flightBlocked) {
      this.x = x;
      this.y = y;
      this.blocked = blocked;
      this.flightBlocked = flightBlocked;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public boolean blocked() {
      return blocked;
    }

    public boolean flightBlocked() {
      return flightBlocked;
    }
  }

  public static final class TileUpdate {
    private final int x, y, tile;
    private final boolean rotated;

    TileUpdate(int x, int y, int tile, boolean rotated) {
      this.x = x;
      this.y = y;
      this.tile = tile;
      this.rotated = rotated;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int tile() {
      return tile;
    }

    public boolean rotated() {
      return rotated;
    }
  }
}
