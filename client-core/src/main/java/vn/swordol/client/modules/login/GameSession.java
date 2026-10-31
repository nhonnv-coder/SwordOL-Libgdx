package vn.swordol.client.modules.login;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.swordol.client.modules.world.WorldListener;
import vn.swordol.protocol.wire.*;

/** Native original-protocol session. Every network action is explicitly initiated by its caller. */
public final class GameSession implements AutoCloseable {
  public enum Stage {
    DISCONNECTED,
    LOADING_SERVERS,
    SERVERS_READY,
    AUTHENTICATING,
    CONNECTING_GAME,
    ROLES_READY,
    ENTERING_WORLD,
    MAP_RECEIVED,
    IN_WORLD,
    ERROR
  }

  private final Consumer<Runnable> dispatcher;
  private final Consumer<Snapshot> listener;
  private final WorldListener world;
  private final MapResourceAssembly resources = new MapResourceAssembly();
  private final ExecutorService io =
      Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "native-login-io");
        t.setDaemon(true);
        return t;
      });
  private final ExecutorService cancellation =
      Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "native-login-cancel");
        t.setDaemon(true);
        return t;
      });
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "native-login-watchdog");
        t.setDaemon(true);
        return t;
      });
  private volatile long generation;
  private volatile Socket socket;
  private volatile HttpURLConnection http;
  private boolean closed;
  private char[] pendingPassword;
  private Stage stage = Stage.DISCONNECTED;
  private String status = "Chưa kết nối", mapName = "";
  private int mapId, sessionId, random, key = 1, enteredRole, serverLine;
  private LoginPackets.Handoff handoff;
  private long stateSince = System.nanoTime();
  private URI endpoint;
  private List<LoginPackets.ServerLine> servers = Collections.emptyList();
  private List<LoginPackets.Role> roles = Collections.emptyList();

  public GameSession(Consumer<Runnable> dispatcher, Consumer<Snapshot> listener) {
    this(dispatcher, listener, null);
  }

  /** With a world listener the session also decodes the map and entity packets of this session. */
  public GameSession(
      Consumer<Runnable> dispatcher, Consumer<Snapshot> listener, WorldListener world) {
    this.dispatcher = Objects.requireNonNull(dispatcher);
    this.listener = Objects.requireNonNull(listener);
    this.world = world;
    watchdog.scheduleWithFixedDelay(
        () -> {
          synchronized (this) {
            if (!closed
                && stage != Stage.DISCONNECTED
                && stage != Stage.ERROR
                && stage != Stage.SERVERS_READY
                && stage != Stage.ROLES_READY
                && stage != Stage.MAP_RECEIVED
                && stage != Stage.IN_WORLD
                && System.nanoTime() - stateSince > TimeUnit.SECONDS.toNanos(15))
              fail(generation, "Máy chủ chưa hoàn tất bước kết nối trong thời gian cho phép.");
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(stage, status, servers, roles, mapId, mapName);
  }

  public synchronized void loadServers(URI address) {
    ensureOpen();
    if (address == null
        || !("http".equalsIgnoreCase(address.getScheme())
            || "https".equalsIgnoreCase(address.getScheme()))
        || address.getHost() == null
        || address.getUserInfo() != null
        || address.getFragment() != null)
      throw new IllegalArgumentException("Enter an HTTP(S) account endpoint without credentials");
    disconnect();
    endpoint = address;
    key = 1;
    sessionId = 0;
    random = 0;
    final long current = generation;
    change(Stage.LOADING_SERVERS, "Đang tải danh sách máy chủ...");
    io.execute(() -> {
      try {
        Wire.Batch batch = exchange(LoginPackets.serverListRequest(), current);
        List<LoginPackets.ServerLine> found = null;
        for (Packet packet : batch.packets())
          if (packet.opcode() == 1077) found = LoginPackets.servers(packet);
        if (found == null) throw new IOException("Missing server list");
        synchronized (this) {
          if (current != generation) return;
          servers = found;
          change(Stage.SERVERS_READY, "Chọn máy chủ / tuyến rồi đăng nhập.");
        }
      } catch (IOException | RuntimeException ex) {
        fail(current, "Không tải được danh sách máy chủ: lỗi kết nối hoặc dữ liệu chưa hỗ trợ.");
      }
    });
  }

  /**
   * Takes ownership of password contents and wipes the caller's array immediately after cloning.
   */
  public synchronized void login(LoginPackets.ServerLine line, String account, char[] password) {
    ensureOpen();
    Objects.requireNonNull(password);
    char[] secret = password.clone();
    Arrays.fill(password, '\0');
    if ((stage != Stage.SERVERS_READY && stage != Stage.ERROR)
        || !servers.contains(line)
        || account == null
        || account.trim().isEmpty()
        || account.length() > 128
        || secret.length == 0
        || secret.length > 128) {
      Arrays.fill(secret, '\0');
      throw new IllegalStateException("Load servers and enter valid credentials first");
    }
    generation++;
    closeTransport();
    final long current = generation;
    pendingPassword = secret;
    change(Stage.AUTHENTICATING, "Đang đăng nhập...");
    io.execute(() -> {
      try {
        Wire.Batch batch = exchange(LoginPackets.login(account, secret, line.token()), current);
        LoginPackets.Handoff target = null;
        for (Packet packet : batch.packets())
          if (packet.opcode() == 1052) target = LoginPackets.handoff(packet);
        if (target == null) throw new IOException("Missing gameplay handoff");
        Socket next = new Socket();
        synchronized (this) {
          if (current != generation) {
            next.close();
            return;
          }
          socket = next;
          // The original keeps these as TCPBase.usID and Business.ServerLineIndex for the whole
          // session; the create-character request writes both back.
          handoff = target;
          serverLine = line.index();
          change(Stage.CONNECTING_GAME, "Đang kết nối máy chủ game...");
        }
        next.connect(new InetSocketAddress(target.host(), target.port()), 4000);
        next.setSoTimeout(1000);
        next.getOutputStream().write(target.header());
        next.getOutputStream()
            .write(
                Wire.tcpRequest(
                    LoginPackets.connect(target, account, secret, line.index()), key++));
        next.getOutputStream().flush();
        Thread reader = new Thread(() -> receive(next, current), "native-game-inbound");
        reader.setDaemon(true);
        reader.start();
      } catch (IOException | RuntimeException ex) {
        fail(
            current,
            "Đăng nhập thất bại: tài khoản bị từ chối, lỗi kết nối hoặc dữ liệu chưa hỗ trợ.");
      } finally {
        synchronized (this) {
          Arrays.fill(secret, '\0');
          if (pendingPassword == secret) pendingPassword = null;
        }
      }
    });
  }

  /**
   * Sends the creation request Business.NewRoleWnd_EditOK sends. The server answers with a fresh
   * role list, so nothing is added locally: the list this session shows always comes from 1080.
   */
  public synchronized void createRole(String name, int sex, int profession, int slot) {
    ensureOpen();
    if (stage != Stage.ROLES_READY || handoff == null)
      throw new IllegalStateException("Create a character from the character list");
    final byte[] request =
        LoginPackets.createRole(name, sex, profession, slot, handoff, serverLine);
    final long current = generation;
    change(Stage.ROLES_READY, "Đang tạo nhân vật...");
    io.execute(() -> {
      try {
        Socket active;
        synchronized (this) {
          if (current != generation) return;
          active = socket;
        }
        if (active == null) throw new EOFException();
        active.getOutputStream().write(Wire.tcpRequest(request, key++));
        active.getOutputStream().flush();
      } catch (IOException | RuntimeException ex) {
        fail(current, "Chưa tạo được nhân vật: lỗi kết nối.");
      }
    });
  }

  public synchronized void enter(int roleId) {
    ensureOpen();
    if (stage != Stage.ROLES_READY || roles.stream().noneMatch(r -> r.id() == roleId))
      throw new IllegalStateException("Select a role from this session");
    final long current = generation;
    enteredRole = roleId;
    resources.reset();
    change(Stage.ENTERING_WORLD, "Đang vào game...");
    io.execute(() -> {
      try {
        Socket active;
        synchronized (this) {
          if (current != generation) return;
          active = socket;
        }
        if (active == null) throw new EOFException();
        active.getOutputStream().write(Wire.tcpRequest(LoginPackets.enter(roleId), key++));
        active.getOutputStream().flush();
      } catch (IOException | RuntimeException ex) {
        fail(current, "Chưa vào được game: lỗi kết nối.");
      }
    });
  }

  private Wire.Batch exchange(byte[] logical, long current) throws IOException {
    HttpURLConnection connection;
    byte[] body;
    synchronized (this) {
      if (current != generation) throw new IOException("Cancelled");
      connection = (HttpURLConnection) endpoint.toURL().openConnection();
      http = connection;
      body = Wire.httpRequest(logical, sessionId, random, key++);
    }
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(4000);
    connection.setReadTimeout(5000);
    connection.setUseCaches(false);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestProperty("Accept", "application/octet-stream");
    connection.setFixedLengthStreamingMode(body.length);
    try {
      try (OutputStream out = connection.getOutputStream()) {
        out.write(body);
      }
      if (connection.getResponseCode() != 200) throw new IOException("Account HTTP rejected");
      Wire.Batch batch;
      try (InputStream in = connection.getInputStream()) {
        batch = Wire.readHttpResponse(in);
      }
      synchronized (this) {
        if (current != generation) throw new IOException("Cancelled");
        if (sessionId == 0) sessionId = batch.session();
        if (random == 0) random = batch.random();
      }
      return batch;
    } finally {
      Arrays.fill(body, (byte) 0);
      connection.disconnect();
      synchronized (this) {
        if (http == connection) http = null;
      }
    }
  }

  private void receive(Socket active, long current) {
    try {
      InputStream input = active.getInputStream();
      // Cancellation/watchdog closes this socket. Never restart a timed-out partial frame.
      // A user waiting at character selection is not a failed handshake.
      active.setSoTimeout(0);
      while (current == generation) {
        Packet packet = TcpReader.read(input);
        if (packet == null) throw new EOFException();
        synchronized (this) {
          if (current != generation) return;
          if (packet.opcode() == 1080
              && (stage == Stage.CONNECTING_GAME || stage == Stage.ROLES_READY)) {
            // Creating a character answers with the whole list again, as processLoginRoleCmd reads
            // it.
            roles = LoginPackets.roles(packet);
            change(Stage.ROLES_READY, "Chọn nhân vật để vào game.");
          } else if (packet.opcode() == 1110 && inWorld()) {
            WorldPackets.MapIdentity identity = WorldPackets.identity(packet);
            mapId = identity.mapId();
            mapName = identity.name();
            publishMap(identity, current);
            change(
                Stage.MAP_RECEIVED,
                "\u0110\u00e3 nh\u1eadn b\u1ea3n \u0111\u1ed3. \u0110ang d\u1ef1ng th\u1ebf gi\u1edbi.");
          } else if (inWorld() && isWorldPacket(packet.opcode())) {
            consumeWorldPacket(packet, current);
          } else if (System.nanoTime() - stateSince > TimeUnit.SECONDS.toNanos(15)
              && stage != Stage.ROLES_READY
              && stage != Stage.MAP_RECEIVED
              && stage != Stage.IN_WORLD) throw new IOException("Handshake timeout");
        }
      }
    } catch (IOException | RuntimeException ex) {
      fail(current, "Kết nối game đã đóng, quá hạn hoặc dữ liệu chưa hỗ trợ.");
    } finally {
      try {
        active.close();
      } catch (IOException ignored) {
      }
    }
  }

  private boolean inWorld() {
    return stage == Stage.ENTERING_WORLD || stage == Stage.MAP_RECEIVED || stage == Stage.IN_WORLD;
  }

  private static boolean isWorldPacket(int opcode) {
    return opcode == 1407
        || opcode == 1126
        || opcode == 1006
        || opcode == 1082
        || opcode == 1014
        || opcode == 1005;
  }

  /**
   * Decodes one world packet. Resource transfers are only buffered; a map is published when its
   * identity arrives with the documents complete, the order processMapDataCmd expects.
   */
  private void consumeWorldPacket(Packet packet, long current) throws IOException {
    if (packet.opcode() == 1407) {
      resources.accept(packet);
      return;
    }
    if (world == null) return;
    if (packet.opcode() == 1126) {
      final WorldPackets.MapUpdate update = WorldPackets.mapUpdate(packet);
      post(current, () -> world.mapUpdate(update));
      return;
    }
    if (packet.opcode() == 1006 || packet.opcode() == 1082) {
      if (packet.fields().isEmpty() || LoginPackets.number(packet, 0) != 2) return;
      final WorldPackets.UserInfo info = WorldPackets.user(packet);
      post(current, () -> world.user(info));
      if (stage != Stage.IN_WORLD)
        change(Stage.IN_WORLD, "\u0110ang \u1edf trong th\u1ebf gi\u1edbi: " + mapName);
      return;
    }
    if (packet.opcode() == 1014) {
      final WorldPackets.PlayerInfo info = WorldPackets.otherPlayer(packet);
      post(current, () -> world.otherPlayer(info));
      return;
    }
    final WorldPackets.Walk walk = WorldPackets.walk(packet);
    post(current, () -> world.walk(walk));
  }

  /** Publishes a map once its documents are complete; an incomplete transfer fails the session. */
  private void publishMap(WorldPackets.MapIdentity identity, long current) throws IOException {
    if (world == null) return;
    byte[] data = resources.completed(MapResourceAssembly.Kind.MAP_DATA);
    byte[] refs = resources.completed(MapResourceAssembly.Kind.MAP_REF);
    // A role the server only announces a map for sends no 1407 at all. That is an announcement,
    // not a broken transfer: show the identity and build no world. Only a half-arrived set is
    // wrong.
    if (data == null && refs == null && !resources.hasPending()) return;
    if (data == null || refs == null) {
      resources.reset();
      throw new IOException("Map identity arrived without its resources");
    }
    final MapResource resource = MapResource.read(MapResourceAssembly.inflate(data));
    final MapReferences references = MapReferences.read(MapResourceAssembly.inflate(refs));
    final Map<Integer, byte[]> images = downloadedImages();
    resources.reset();
    post(current, () -> world.mapLoaded(identity, resource, references, images));
  }

  /** PNGs this map downloaded, rebuilt from the packed image resource; empty when it sent none. */
  private Map<Integer, byte[]> downloadedImages() throws IOException {
    byte[] index = resources.completed(MapResourceAssembly.Kind.MAP_IMAGES);
    byte[] pack = resources.completed(MapResourceAssembly.Kind.MAP_PNG);
    if (index == null || pack == null) return Collections.emptyMap();
    Map<Integer, byte[]> images = new LinkedHashMap<>();
    for (PackedImages.Entry entry : PackedImages.index(MapResourceAssembly.inflate(index)))
      images.put(entry.imageId(), PackedImages.png(pack, entry.offset()));
    return Collections.unmodifiableMap(images);
  }

  private void post(long current, Runnable action) {
    dispatcher.accept(() -> {
      if (current == generation) action.run();
    });
  }

  /** Sends the walk the original client sends for its own move. Never moves another character. */
  public synchronized void requestWalk(int direction, int x, int y) {
    ensureOpen();
    if (!inWorld() || enteredRole <= 0) throw new IllegalStateException("Enter the world first");
    send(WorldPackets.walkRequest(enteredRole, direction, x, y, mapId));
  }

  /** Asks for a player the server mentioned, as processWalkCmd does for an unknown walker. */
  public synchronized void requestPlayer(int id) {
    ensureOpen();
    if (!inWorld()) throw new IllegalStateException("Enter the world first");
    send(WorldPackets.playerRequest(id));
  }

  private void send(byte[] logical) {
    final long current = generation;
    io.execute(() -> {
      try {
        Socket active;
        byte[] frame;
        synchronized (this) {
          if (current != generation) return;
          active = socket;
          if (active == null) throw new EOFException();
          frame = Wire.tcpRequest(logical, key++);
        }
        active.getOutputStream().write(frame);
        active.getOutputStream().flush();
      } catch (IOException | RuntimeException ex) {
        fail(
            current, "M\u1ea5t k\u1ebft n\u1ed1i khi g\u1eedi l\u1ec7nh trong th\u1ebf gi\u1edbi.");
      }
    });
  }

  private synchronized void fail(long current, String message) {
    if (current != generation) return;
    generation++;
    closeTransport();
    change(Stage.ERROR, message);
  }

  private void change(Stage next, String message) {
    stage = next;
    status = message;
    stateSince = System.nanoTime();
    final long current = generation;
    final Snapshot value = snapshot();
    dispatcher.accept(() -> {
      if (current == generation) listener.accept(value);
    });
  }

  public synchronized void disconnect() {
    generation++;
    closeTransport();
    enteredRole = 0;
    resources.reset();
    servers = Collections.emptyList();
    roles = Collections.emptyList();
    mapId = 0;
    mapName = "";
    change(Stage.DISCONNECTED, "Chưa kết nối");
  }

  private void closeTransport() {
    if (pendingPassword != null) {
      Arrays.fill(pendingPassword, '\0');
      pendingPassword = null;
    }
    Socket old = socket;
    socket = null;
    if (old != null)
      try {
        old.close();
      } catch (IOException ignored) {
      }
    HttpURLConnection request = http;
    http = null;
    if (request != null) cancellation.execute(request::disconnect);
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Session closed");
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      disconnect();
      closed = true;
      io.shutdownNow();
      cancellation.shutdown();
      watchdog.shutdownNow();
    }
  }

  public static final class Snapshot {
    private final Stage stage;
    private final String status, mapName;
    private final List<LoginPackets.ServerLine> servers;
    private final List<LoginPackets.Role> roles;
    private final int mapId;

    Snapshot(
        Stage stage,
        String status,
        List<LoginPackets.ServerLine> servers,
        List<LoginPackets.Role> roles,
        int mapId,
        String mapName) {
      this.stage = stage;
      this.status = status;
      this.servers = servers;
      this.roles = roles;
      this.mapId = mapId;
      this.mapName = mapName;
    }

    public Stage stage() {
      return stage;
    }

    public String status() {
      return status;
    }

    public List<LoginPackets.ServerLine> servers() {
      return servers;
    }

    public List<LoginPackets.Role> roles() {
      return roles;
    }

    public int mapId() {
      return mapId;
    }

    public String mapName() {
      return mapName;
    }
  }
}
