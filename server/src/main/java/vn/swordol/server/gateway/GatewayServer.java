package vn.swordol.server.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import vn.swordol.protocol.wire.*;

/**
 * Local binary-protocol authentication/role vertical slice. No production account store or world.
 */
public final class GatewayServer implements AutoCloseable {
  public interface Authenticator {
    Account verify(String name, char[] password);
  }

  /** Creates a local account, or returns null when the name is taken or the server refuses it. */
  public interface Registrar {
    Account register(String name, char[] password, String repeated);
  }

  private final Authenticator authenticator;
  private volatile Registrar registrar;
  private final HttpServer http;
  private final ServerSocket tcp;
  private final ThreadPoolExecutor accountWorkers =
      new ThreadPoolExecutor(
          4,
          4,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<Runnable>(16),
          r -> {
            Thread t = new Thread(r, "legacy-account-worker");
            t.setDaemon(true);
            return t;
          });
  private final ThreadPoolExecutor gameWorkers =
      new ThreadPoolExecutor(
          8,
          8,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<Runnable>(16),
          r -> {
            Thread t = new Thread(r, "legacy-game-worker");
            t.setDaemon(true);
            return t;
          });
  private final Map<Integer, Ticket> tickets = new ConcurrentHashMap<>();
  private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
  private final SecureRandom random = new SecureRandom();
  private volatile boolean running;
  private Thread acceptor;

  public GatewayServer(Authenticator authenticator) throws IOException {
    this(authenticator, 0, 0);
  }

  public GatewayServer(Authenticator authenticator, int accountPort, int gamePort)
      throws IOException {
    this.authenticator = Objects.requireNonNull(authenticator);
    tcp = new ServerSocket();
    tcp.bind(new InetSocketAddress("127.0.0.1", gamePort), 16);
    try {
      http = HttpServer.create(new InetSocketAddress("127.0.0.1", accountPort), 16);
    } catch (IOException ex) {
      tcp.close();
      throw ex;
    }
    http.createContext("/account", this::account);
    http.setExecutor(accountWorkers);
  }

  /** Without one, 1055 is refused: a gateway that cannot store accounts must not pretend to. */
  public void setRegistrar(Registrar registrar) {
    this.registrar = registrar;
  }

  public URI accountEndpoint() {
    return URI.create("http://127.0.0.1:" + http.getAddress().getPort() + "/account");
  }

  public void start() {
    if (running) throw new IllegalStateException("Already started");
    running = true;
    http.start();
    acceptor =
        new Thread(
            () -> {
              while (running) {
                try {
                  Socket socket = tcp.accept();
                  socket.setSoTimeout(15000);
                  sockets.add(socket);
                  try {
                    gameWorkers.execute(() -> game(socket));
                  } catch (RejectedExecutionException ex) {
                    sockets.remove(socket);
                    socket.close();
                  }
                } catch (IOException ex) {
                  if (running) running = false;
                }
              }
            },
            "legacy-local-accept");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  private void account(HttpExchange exchange) throws IOException {
    try {
      if (!"POST".equals(exchange.getRequestMethod())
          || !"/account".equals(exchange.getRequestURI().getPath())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      Wire.Batch request = Wire.readHttpRequest(exchange.getRequestBody());
      if (request.packets().size() != 1) throw new IOException("One account command required");
      Packet packet = request.packets().get(0);
      byte[] response;
      if (packet.opcode() == 1077) {
        response =
            new WireWriter(1077)
                .integer(0)
                .integer(0)
                .signedByte(1)
                .text("Máy chủ thử nghiệm")
                .text("local")
                .signedByte(1)
                .integer(1)
                .build();
      } else if (packet.opcode() == 1055) {
        response = registerAccount(packet);
      } else if (packet.opcode() == 1051) {
        if (packet.fields().size() != 9
            || LoginPackets.number(packet, 4) != 1
            || !"local".equals(LoginPackets.text(packet, 2)))
          throw new IOException("Invalid login schema");
        String name = LoginPackets.text(packet, 0);
        char[] password = LoginPackets.text(packet, 1).toCharArray();
        Account user;
        try {
          user = authenticator.verify(name, password);
        } finally {
          Arrays.fill(password, '\0');
        }
        int authorization = 0;
        if (user != null && !name.equals(user.name)) user = null;
        if (user != null) {
          tickets.entrySet().removeIf(e -> System.nanoTime() > e.getValue().expires);
          if (tickets.size() >= 64) throw new IOException("Too many pending logins");
          do {
            authorization = random.nextInt(Integer.MAX_VALUE - 1) + 1;
          } while (tickets.containsKey(authorization));
          tickets.put(authorization, new Ticket(user));
        }
        int port = user == null ? 0 : tcp.getLocalPort();
        response =
            new WireWriter(1052)
                .integer(user == null ? 0 : user.id)
                .integer(authorization)
                .integer(port)
                .text("127.0.0.1")
                .signedByte(66)
                .signedByte(49)
                .signedByte(0)
                .integer(port)
                .text("127.0.0.1")
                .integer(0)
                .text("")
                .signedByte(0)
                .build();
      } else {
        throw new IOException("Unsupported account command");
      }
      byte[] body = Wire.httpResponse(Collections.singletonList(response), 1, 1);
      exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    } catch (IOException | IllegalArgumentException ex) {
      if (exchange.getResponseCode() == -1) exchange.sendResponseHeaders(400, -1);
    } finally {
      exchange.close();
    }
  }

  private void game(Socket socket) {
    try (Socket owned = socket) {
      byte[] raw = Wire.readTcpRequest(owned.getInputStream());
      if (raw == null) return;
      Packet connect = Wire.logical(raw);
      if (connect.opcode() != 1052 || connect.fields().size() != 7)
        throw new IOException("Expected gameplay connect");
      Ticket ticket = tickets.remove(LoginPackets.number(connect, 1));
      if (ticket == null
          || System.nanoTime() > ticket.expires
          || ticket.account.id != LoginPackets.number(connect, 0))
        throw new IOException("Invalid gameplay ticket");
      char[] password = LoginPackets.text(connect, 6).toCharArray();
      Account verified;
      try {
        verified = authenticator.verify(LoginPackets.text(connect, 5), password);
      } finally {
        Arrays.fill(password, '\0');
      }
      if (verified == null
          || verified.id != ticket.account.id
          || !verified.name.equals(ticket.account.name)
          || !verified.name.equals(LoginPackets.text(connect, 5)))
        throw new IOException("Gameplay identity rejected");
      owned.setSoTimeout(0);
      sendRoleList(owned, ticket.account);
      while (running && (raw = Wire.readTcpRequest(owned.getInputStream())) != null) {
        Packet command = Wire.logical(raw);
        if (command.opcode() == 1001) {
          createRole(owned, ticket.account, command);
          continue;
        }
        if (command.opcode() != 1081
            || command.fields().size() != 4
            || LoginPackets.number(command, 1) != 0)
          throw new IOException("Only enter and create role supported");
        int id = LoginPackets.number(command, 0);
        Role selected = null;
        for (Role role : ticket.account.roles()) if (role.id == id) selected = role;
        if (selected == null) throw new IOException("Role ownership rejected");
        if (selected.world == null) {
          byte[] map =
              new WireWriter(1110)
                  .integer(selected.mapId)
                  .integer(selected.mapId)
                  .integer(0)
                  .text(selected.mapName)
                  .build();
          owned.getOutputStream().write(Wire.tcpResponse(map));
          owned.getOutputStream().flush();
          continue;
        }
        serveWorld(owned, selected);
        int[] position = {selected.world.spawnX(), selected.world.spawnY()};
        while (running && (raw = Wire.readTcpRequest(owned.getInputStream())) != null)
          handleWorldCommand(owned, selected, position, Wire.logical(raw));
        return;
      }
    } catch (IOException ex) {
      /* Invalid input/timeout closes this connection; never log payloads. */
    } finally {
      sockets.remove(socket);
    }
  }

  /**
   * Business.initRegisterPacket over the account channel. The reply is the shape CmdProcessor reads
   * at case 0: a status byte then three parameters, where a non-empty first parameter is what makes
   * the client report success.
   */
  private byte[] registerAccount(Packet packet) throws IOException {
    if (packet.fields().size() != 8) throw new IOException("Invalid registration schema");
    String name = LoginPackets.text(packet, 1);
    String repeated = LoginPackets.text(packet, 3);
    char[] password = LoginPackets.text(packet, 2).toCharArray();
    Account created;
    try {
      Registrar store = registrar;
      created =
          store == null
                  || name.length() < LoginPackets.REGISTER_FIELD_MINIMUM
                  || password.length < LoginPackets.REGISTER_FIELD_MINIMUM
                  || repeated.length() < LoginPackets.REGISTER_FIELD_MINIMUM
                  || name.length() > LoginPackets.REGISTER_FIELD_LIMIT
              ? null
              : store.register(name, password, repeated);
    } finally {
      Arrays.fill(password, '\0');
    }
    return new WireWriter(1055)
        .signedByte(0)
        .text(created == null ? "" : created.name)
        .text("")
        .text("")
        .build();
  }

  /** The character list processLoginRoleCmd reads: a count byte then 30 fields per role. */
  private void sendRoleList(Socket owned, Account account) throws IOException {
    List<Role> roles = account.roles();
    WireWriter list = new WireWriter(1080).signedByte(roles.size());
    for (Role role : roles) {
      list.integer(role.id)
          .integer(1)
          .integer(role.lookFace)
          .signedByte(0)
          .shortValue(10)
          .text(role.name)
          .signedByte(role.slot);
      for (int i = 7; i < 30; i++) list.integer(0);
    }
    owned.getOutputStream().write(Wire.tcpResponse(list.build()));
    owned.getOutputStream().flush();
  }

  /**
   * Handles the creation request Business.NewRoleWnd_EditOK sends, then answers with the whole list
   * again. The look face comes from the creation screen's own table rather than the request, so a
   * client cannot ask for an appearance the screen does not offer.
   */
  private void createRole(Socket owned, Account account, Packet command) throws IOException {
    if (command.fields().size() != 12) throw new IOException("Invalid create-role schema");
    String name = LoginPackets.text(command, 0);
    int sex = LoginPackets.number(command, 1);
    int profession = (LoginPackets.number(command, 2) - 1) / 10;
    int slot = LoginPackets.number(command, 7);
    int lookFace = RoleLooks.lookFace(profession, sex);
    if (lookFace == 0 || name.trim().isEmpty() || name.length() > LoginPackets.ROLE_NAME_LIMIT)
      throw new IOException("Create-role rejected");
    try {
      account.create(name, slot, lookFace);
    } catch (IllegalArgumentException ex) {
      throw new IOException("Create-role rejected");
    }
    sendRoleList(owned, account);
  }

  /**
   * Sends the world the original server sends after1081: the map resources in1407 chunks, the map
   * identity, its portals and the user's own entity. The transfer is split over several frames, as
   * the captured sessions are, instead of one oversized frame.
   */
  private void serveWorld(Socket owned, Role role) throws IOException {
    World world = role.world;
    OutputStream out = owned.getOutputStream();
    sendResource(out, 15, 16, zip(world.mapData));
    sendResource(out, 11, 12, zip(world.mapRef));
    out.write(
        Wire.tcpResponse(
            new WireWriter(1110)
                .integer(world.mapId)
                .integer(world.mapId)
                .integer(world.mapType)
                .text(world.mapName)
                .build()));
    WireWriter portals = new WireWriter(1126).signedByte(0).signedByte(world.portals.size());
    for (Portal portal : world.portals)
      portals.signedByte(portal.id).signedByte(portal.x).signedByte(portal.y).text(portal.name);
    out.write(Wire.tcpResponse(portals.build()));
    out.write(Wire.tcpResponse(userInfo(role)));
    out.flush();
  }

  /** The49-field1006 action2 shape; only the fields the original client reads carry values. */
  private static byte[] userInfo(Role role) {
    WireWriter writer =
        new WireWriter(1006)
            .signedByte(2)
            .shortValue(0)
            .integer(role.id)
            .integer(220000)
            .text(role.name)
            .signedByte(0)
            .signedByte(0)
            .signedByte(0);
    for (int index = 8; index < 49; index++) {
      if (index == 35) writer.shortValue(role.world.spawnX());
      else if (index == 36) writer.shortValue(role.world.spawnY());
      else if (index == 47) writer.shortValue(6);
      else writer.integer(0);
    }
    return writer.build();
  }

  private static void sendResource(
      OutputStream out, int chunkAction, int lastAction, byte[] payload) throws IOException {
    int limit = 4096;
    for (int at = 0; at < payload.length; at += limit) {
      int size = Math.min(limit, payload.length - at);
      boolean last = at + size >= payload.length;
      out.write(
          Wire.tcpResponse(
              new WireWriter(1407)
                  .signedByte(last ? lastAction : chunkAction)
                  .shortValue(size)
                  .binary(Arrays.copyOfRange(payload, at, at + size))
                  .build()));
    }
  }

  /** A deterministic single-member stored archive, the shape MapEx.loadZipData reads. */
  private static byte[] zip(byte[] content) throws IOException {
    CRC32 crc = new CRC32();
    crc.update(content);
    ZipEntry entry = new ZipEntry("data");
    entry.setMethod(ZipEntry.STORED);
    entry.setSize(content.length);
    entry.setCompressedSize(content.length);
    entry.setCrc(crc.getValue());
    entry.setTime(0L);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
      archive.putNextEntry(entry);
      archive.write(content);
      archive.closeEntry();
    }
    return bytes.toByteArray();
  }

  /**
   * Only the walk of the role this connection entered is accepted, and only to a neighbouring
   * walkable cell. Anything else gets the authoritative position back as a correction.
   */
  private void handleWorldCommand(Socket owned, Role role, int[] position, Packet command)
      throws IOException {
    if (command.opcode() != 1005) throw new IOException("Only walk supported in this local world");
    WorldPackets.Walk walk = WorldPackets.walk(command);
    if (walk.id() != role.id) throw new IOException("Walk ownership rejected");
    boolean step = Math.abs(walk.x() - position[0]) + Math.abs(walk.y() - position[1]) == 1;
    if (step && walkable(role.world.map, walk.x(), walk.y())) {
      position[0] = walk.x();
      position[1] = walk.y();
      return;
    }
    owned
        .getOutputStream()
        .write(
            Wire.tcpResponse(
                new WireWriter(1005)
                    .integer(role.id)
                    .integer(
                        WorldPackets.WALK_CORRECTED * 10000000 + position[0] * 1000 + position[1])
                    .integer(role.world.mapId)
                    .build()));
    owned.getOutputStream().flush();
  }

  @Override
  public void close() throws IOException {
    running = false;
    http.stop(0);
    tcp.close();
    for (Socket socket : sockets)
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    tickets.clear();
    accountWorkers.shutdownNow();
    gameWorkers.shutdownNow();
    if (acceptor != null)
      try {
        acceptor.join(1000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
  }

  public static final class Account {
    /** The three slots the original SELROLE_WND holds. */
    static final int SLOTS = 3;

    private final int id;
    private final String name;
    private final List<Role> roles;
    private final World startingWorld;
    private int nextRoleId;

    public Account(int id, String name, List<Role> roles) {
      this(id, name, roles, null);
    }

    /**
     * An account whose characters are yet to be created needs somewhere to put them, so a fresh one
     * carries the world the launcher serves.
     */
    public Account(int id, String name, List<Role> roles, World startingWorld) {
      if (id <= 0 || name == null || roles.size() > SLOTS)
        throw new IllegalArgumentException("Invalid local account");
      Set<Integer> slots = new HashSet<>(), ids = new HashSet<>();
      int highest = 0;
      for (Role role : roles) {
        if (!slots.add(role.slot) || !ids.add(role.id))
          throw new IllegalArgumentException("Duplicate role");
        highest = Math.max(highest, role.id);
      }
      this.id = id;
      this.name = name;
      this.roles = new ArrayList<>(roles);
      this.startingWorld = startingWorld;
      this.nextRoleId = highest + 1;
    }

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    /** A snapshot: callers iterate it while another connection may be creating a character. */
    synchronized List<Role> roles() {
      return new ArrayList<>(roles);
    }

    /**
     * Adds a character the way the creation screen asks for one: into a free slot, with a name no
     * other role here holds. The new role enters the same world as an existing one, because the
     * local gateway has no starting map of its own.
     *
     * <p>shortcut: every character starts in the same world, when to upgrade: a local server that
     * owns a starting map per profession.
     */
    synchronized Role create(String name, int slot, int lookFace) {
      if (roles.size() >= SLOTS) throw new IllegalArgumentException("No free character slot");
      for (Role role : roles) {
        if (role.slot == slot) throw new IllegalArgumentException("Slot already holds a character");
        if (role.name.equals(name)) throw new IllegalArgumentException("Name already taken");
      }
      World where = roles.isEmpty() ? startingWorld : roles.get(0).world;
      Role created;
      if (where != null) {
        created = new Role(nextRoleId, name, slot, where, lookFace);
      } else if (roles.isEmpty()) {
        throw new IllegalArgumentException("No world to place a character in");
      } else {
        Role template = roles.get(0);
        created = new Role(nextRoleId, name, slot, template.mapId, template.mapName, lookFace);
      }
      nextRoleId++;
      roles.add(created);
      return created;
    }
  }

  public static final class Role {
    /** The look the earlier slices announced before characters could be created. */
    static final int DEFAULT_LOOK_FACE = 100000;

    private final int id, slot, mapId, lookFace;
    private final String name, mapName;
    private final World world;

    /** Announces a map only, as the first native slice did. */
    public Role(int id, String name, int slot, int mapId, String mapName) {
      this(id, name, slot, mapId, mapName, null, DEFAULT_LOOK_FACE);
    }

    public Role(int id, String name, int slot, int mapId, String mapName, int lookFace) {
      this(id, name, slot, mapId, mapName, null, lookFace);
    }

    /** Serves a real map: resources, identity, portals, the user entity and authoritative walks. */
    public Role(int id, String name, int slot, World world) {
      this(id, name, slot, world, DEFAULT_LOOK_FACE);
    }

    public Role(int id, String name, int slot, World world, int lookFace) {
      this(
          id,
          name,
          slot,
          Objects.requireNonNull(world, "world").mapId,
          world.mapName,
          world,
          lookFace);
    }

    private Role(
        int id, String name, int slot, int mapId, String mapName, World world, int lookFace) {
      if (id <= 0
          || slot < 0
          || slot >= Account.SLOTS
          || name == null
          || mapName == null
          || lookFace <= 0) throw new IllegalArgumentException("Invalid local role");
      this.id = id;
      this.name = name;
      this.slot = slot;
      this.mapId = mapId;
      this.mapName = mapName;
      this.world = world;
      this.lookFace = lookFace;
    }
  }

  /** One local map the gateway can actually serve, built from map bytes the real server sent. */
  public static final class World {
    private final int mapId, mapType, spawnX, spawnY;
    private final String mapName;
    private final byte[] mapData, mapRef;
    private final List<Portal> portals;
    private final MapResource map;

    public World(
        int mapId,
        int mapType,
        String mapName,
        byte[] mapData,
        byte[] mapRef,
        int spawnX,
        int spawnY,
        List<Portal> portals) {
      if (mapId <= 0
          || mapName == null
          || mapData == null
          || mapRef == null
          || portals == null
          || portals.size() > 127) throw new IllegalArgumentException("Invalid local world");
      try {
        map = MapResource.read(mapData);
      } catch (IOException ex) {
        throw new IllegalArgumentException("Invalid map document", ex);
      }
      if (!walkable(map, spawnX, spawnY))
        throw new IllegalArgumentException("Spawn is not a walkable cell");
      this.mapId = mapId;
      this.mapType = mapType;
      this.mapName = mapName;
      this.mapData = mapData.clone();
      this.mapRef = mapRef.clone();
      this.spawnX = spawnX;
      this.spawnY = spawnY;
      this.portals = Collections.unmodifiableList(new ArrayList<>(portals));
    }

    public int mapId() {
      return mapId;
    }

    public int spawnX() {
      return spawnX;
    }

    public int spawnY() {
      return spawnY;
    }
  }

  public static final class Portal {
    private final int id, x, y;
    private final String name;

    public Portal(int id, int x, int y, String name) {
      if (id < 0 || id > 127 || x < 0 || x > 127 || y < 0 || y > 127 || name == null)
        throw new IllegalArgumentException("Invalid local portal");
      this.id = id;
      this.x = x;
      this.y = y;
      this.name = name;
    }
  }

  /** MapEx.isBlock for a walking object. */
  private static boolean walkable(MapResource map, int x, int y) {
    return x >= 1 && y >= 1 && x < map.columns() && y < map.rows() && !map.blocked(x, y);
  }

  private static final class Ticket {
    final Account account;
    final long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

    Ticket(Account account) {
      this.account = account;
    }
  }
}
