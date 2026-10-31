package vn.swordol.protocol.wire;

import java.io.IOException;
import java.util.*;

/** Schemas observed in Business and CmdProcessor; no inferred account or gameplay rules. */
public final class LoginPackets {
  public static final int CLIENT_VERSION = 250615;
  public static final String CLIENT_ID = "JT_N5800F";

  private LoginPackets() {}

  public static byte[] serverListRequest() {
    return new WireWriter(1077)
        .integer(CLIENT_VERSION)
        .text(CLIENT_ID)
        .signedByte(0)
        .text("")
        .build();
  }

  public static byte[] login(String account, char[] password, String token) {
    return new WireWriter(1051)
        .text(account)
        .text(new String(password))
        .text(token)
        .integer(0)
        .signedByte(1)
        .signedByte(0)
        .text(CLIENT_ID)
        .integer(CLIENT_VERSION)
        .signedByte(0)
        .build();
  }

  public static byte[] connect(Handoff handoff, String account, char[] password, int line) {
    return new WireWriter(1052)
        .integer(handoff.account)
        .integer(handoff.authorization)
        .signedByte(line)
        .signedByte(0)
        .signedByte(handoff.tryGame)
        .text(account)
        .text(new String(password))
        .build();
  }

  /** CustomScreen refuses any registration box under six characters with other_str[583]. */
  public static final int REGISTER_FIELD_MINIMUM = 6;

  /**
   * Business.initRegisterPacket over the account channel, the branch openRegisterWnd(0) takes. The
   * third box is sent as the screen collects it; only the first two are ever read back, as the
   * account and password of the login screen, so this does not name it.
   *
   * <p>The two identity ints are zero here: the original reads them from PackageID.o and its own
   * user file, neither of which this build ships.
   */
  public static byte[] register(String account, String password, String third) {
    for (String field : new String[] {account, password, third})
      if (field == null || field.length() < REGISTER_FIELD_MINIMUM)
        throw new IllegalArgumentException("Every registration field needs six characters");
    return new WireWriter(1055)
        .signedByte(0)
        .text(account)
        .text(password)
        .text(third)
        .text("")
        .integer(0)
        .integer(0)
        .integer(9)
        .build();
  }

  /** The creation screen's edit box limits the name with setCharLimit(10). */
  public static final int ROLE_NAME_LIMIT = 10;

  /** The three slots SELROLE_WND holds; the request writes focusedID - 11001. */
  public static final int ROLE_SLOTS = 3;

  /**
   * Business.NewRoleWnd_EditOK, field for field. The user id is TCPBase.usID, which
   * processLoginBackCmd assigns from the account id of the 1052 response, so it comes off the
   * handoff rather than being tracked separately. The discretion byte is the one connect() also
   * sends as zero.
   *
   * @param sex {@link RoleLooks#MALE} or {@link RoleLooks#FEMALE}
   * @param profession 1 to {@link RoleLooks#PROFESSIONS}, as the screen numbers its buttons
   */
  public static byte[] createRole(
      String name, int sex, int profession, int slot, Handoff handoff, int line) {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty() || trimmed.length() > ROLE_NAME_LIMIT)
      throw new IllegalArgumentException("Role name must hold 1 to 10 characters");
    if (!RoleLooks.offers(profession, sex))
      throw new IllegalArgumentException("No look face for this profession and sex");
    if (slot < 0 || slot >= ROLE_SLOTS)
      throw new IllegalArgumentException("Role slot out of range");
    return new WireWriter(1001)
        .text(name)
        .integer(sex)
        .signedByte(profession * 10 + 1)
        .signedByte(0)
        .integer(handoff.account)
        .integer(0)
        .text("")
        .signedByte(slot)
        .integer(0)
        .signedByte(line)
        .signedByte(0)
        .signedByte(handoff.tryGame)
        .build();
  }

  public static byte[] enter(int role) {
    return new WireWriter(1081)
        .integer(role)
        .signedByte(0)
        .integer(CLIENT_VERSION)
        .text(CLIENT_ID)
        .build();
  }

  public static List<ServerLine> servers(Packet packet) throws IOException {
    requireOpcode(packet, 1077);
    int count = number(packet, 2);
    if (count < 0 || count > 127 || packet.fields().size() != 3 + 4 * count)
      throw new IOException("Invalid server list schema");
    List<ServerLine> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int at = 3 + i * 4;
      int channels = number(packet, at + 3);
      if (channels < 0 || channels > 9) throw new IOException("Unsupported server channel count");
      for (int channel = 0; channel < channels; channel++)
        result.add(
            new ServerLine(
                channel, text(packet, at), text(packet, at + 1), number(packet, at + 2)));
    }
    return Collections.unmodifiableList(result);
  }

  public static Handoff handoff(Packet packet) throws IOException {
    requireOpcode(packet, 1052);
    if (packet.fields().size() != 12) throw new IOException("Invalid login response schema");
    if (number(packet, 2) == 0) throw new IOException("Account login rejected");
    int extra = number(packet, 9), port = extra == 0 ? number(packet, 7) : extra;
    String host = text(packet, 3);
    if (port < 1
        || port > 65535
        || host.isEmpty()
        || host.length() > 253
        || host.contains("/")
        || host.contains("@")
        || host.contains("\\")
        || host.chars().anyMatch(Character::isWhitespace))
      throw new IOException("Invalid gameplay destination");
    byte[] header =
        number(packet, 6) == 0
            ? new byte[0]
            : new byte[] {(byte) number(packet, 4), (byte) number(packet, 5)};
    return new Handoff(
        number(packet, 0), number(packet, 1), host, port, header, number(packet, 11));
  }

  public static List<Role> roles(Packet packet) throws IOException {
    requireOpcode(packet, 1080);
    int count = number(packet, 0);
    if (count < 0 || count > 3 || packet.fields().size() != 1 + 30 * count)
      throw new IOException("Invalid role-list schema");
    List<Role> result = new ArrayList<>();
    Set<Integer> slots = new HashSet<>(), ids = new HashSet<>();
    for (int i = 0; i < count; i++) {
      int at = 1 + i * 30, id = number(packet, at), slot = number(packet, at + 6);
      if (slot < 0 || slot > 2 || id <= 0 || !slots.add(slot) || !ids.add(id))
        throw new IOException("Invalid role slot");
      result.add(
          new Role(id, number(packet, at + 1), number(packet, at + 2), text(packet, at + 5), slot));
    }
    result.sort(Comparator.comparingInt(Role::slot));
    return Collections.unmodifiableList(result);
  }

  public static int number(Packet packet, int index) throws IOException {
    try {
      return packet.fields().get(index).number();
    } catch (IndexOutOfBoundsException | IllegalStateException ex) {
      throw new IOException("Invalid numeric field schema");
    }
  }

  public static String text(Packet packet, int index) throws IOException {
    try {
      return packet.fields().get(index).text();
    } catch (IndexOutOfBoundsException | IllegalStateException ex) {
      throw new IOException("Invalid text field schema");
    }
  }

  private static void requireOpcode(Packet p, int code) throws IOException {
    if (p == null || p.opcode() != code) throw new IOException("Unexpected login opcode");
  }

  public static final class ServerLine {
    private final int index, status;
    private final String name, token;

    ServerLine(int index, String name, String token, int status) {
      this.index = index;
      this.name = name;
      this.token = token;
      this.status = status;
    }

    public int index() {
      return index;
    }

    public String name() {
      return name;
    }

    public String token() {
      return token;
    }

    public int status() {
      return status;
    }

    @Override
    public String toString() {
      return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC)
          + " / tuyến "
          + (index + 1);
    }
  }

  public static final class Role {
    private final int id, level, lookId, slot;
    private final String name;

    Role(int id, int level, int lookId, String name, int slot) {
      this.id = id;
      this.level = level;
      this.lookId = lookId;
      this.name = name;
      this.slot = slot;
    }

    public int id() {
      return id;
    }

    public int level() {
      return level;
    }

    public int lookId() {
      return lookId;
    }

    public String name() {
      return name;
    }

    public int slot() {
      return slot;
    }

    @Override
    public String toString() {
      return "["
          + (slot + 1)
          + "] "
          + java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC)
          + "  Lv."
          + level;
    }
  }

  public static final class Handoff {
    private final int account, authorization, port, tryGame;
    private final String host;
    private final byte[] header;

    Handoff(int account, int authorization, String host, int port, byte[] header, int tryGame) {
      this.account = account;
      this.authorization = authorization;
      this.host = host;
      this.port = port;
      this.header = header.clone();
      this.tryGame = tryGame;
    }

    public int account() {
      return account;
    }

    public int authorization() {
      return authorization;
    }

    public String host() {
      return host;
    }

    public int port() {
      return port;
    }

    public byte[] header() {
      return header.clone();
    }
  }
}
