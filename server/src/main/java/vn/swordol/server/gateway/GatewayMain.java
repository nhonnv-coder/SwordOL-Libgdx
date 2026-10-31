package vn.swordol.server.gateway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import vn.swordol.protocol.wire.MapResource;

/**
 * Explicit local developer server entry, loopback only; no legacy account credentials are imported.
 */
public final class GatewayMain {
  private GatewayMain() {}

  public static void main(String[] args) throws Exception {
    String password = System.getenv("SWORDOL_LOCAL_PASSWORD");
    String name = System.getenv("SWORDOL_LOCAL_ACCOUNT");
    if (password == null || password.isEmpty() || name == null || name.isEmpty())
      throw new IllegalArgumentException(
          "Set SWORDOL_LOCAL_ACCOUNT and SWORDOL_LOCAL_PASSWORD for this local server.");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    char[] initial = password.toCharArray();
    byte[] expected;
    try {
      expected = hash(initial, salt);
    } finally {
      Arrays.fill(initial, '\0');
    }
    GatewayServer.World world = loadWorld(System.getenv("SWORDOL_LOCAL_MAP"));
    GatewayServer.Role role =
        world == null
            ? new GatewayServer.Role(42, "Kiếm Khách", 0, 1101400, "Trung nguyên")
            : new GatewayServer.Role(42, "Kiếm Khách", 0, world);
    GatewayServer.Account account =
        new GatewayServer.Account(7, name, Collections.singletonList(role));
    GatewayServer server =
        new GatewayServer(
            (user, secret) -> {
              if (!name.equals(user)) return null;
              byte[] candidate = hash(secret, salt);
              try {
                return MessageDigest.isEqual(expected, candidate) ? account : null;
              } finally {
                Arrays.fill(candidate, (byte) 0);
              }
            },
            7780,
            7781);
    CountDownLatch done = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    server.close();
                  } catch (java.io.IOException ignored) {
                  }
                  Arrays.fill(expected, (byte) 0);
                  done.countDown();
                },
                "legacy-local-shutdown"));
    server.start();
    System.out.println("Local binary login server: " + server.accountEndpoint());
    System.out.println(
        world == null
            ? "Authentication / role selection / map announcement only. Set SWORDOL_LOCAL_MAP to a captured map folder to serve a real map."
            : "Serving the captured map in "
                + System.getenv("SWORDOL_LOCAL_MAP")
                + ". Walks are checked against that map; no persistent world or inventory.");
    done.await();
  }

  /**
   * Reads a map folder exported by tools/analysis/extract_maps.py (map-data.bin and map-ref.bin).
   * Returns null when no folder is configured, so the launcher keeps its announcement-only mode.
   */
  private static GatewayServer.World loadWorld(String folder) throws IOException {
    if (folder == null || folder.trim().isEmpty()) return null;
    Path directory = Paths.get(folder.trim());
    byte[] data = Files.readAllBytes(directory.resolve("map-data.bin"));
    byte[] references = Files.readAllBytes(directory.resolve("map-ref.bin"));
    MapResource map = MapResource.read(data);
    int[] spawn = spawn(map, System.getenv("SWORDOL_LOCAL_SPAWN"));
    System.out.println(
        "Local map " + map.columns() + "x" + map.rows() + " spawn " + spawn[0] + "," + spawn[1]);
    return new GatewayServer.World(
        1101400,
        0,
        "B\u1ea3n \u0111\u1ed3 c\u1ee5c b\u1ed9",
        data,
        references,
        spawn[0],
        spawn[1],
        Collections.<GatewayServer.Portal>emptyList());
  }

  /** Uses the requested cell when the map allows standing there, otherwise the first that does. */
  private static int[] spawn(MapResource map, String requested) {
    if (requested != null && requested.matches("\\d{1,3},\\d{1,3}")) {
      String[] parts = requested.split(",");
      int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]);
      if (walkable(map, x, y)) return new int[] {x, y};
    }
    for (int y = 1; y < map.rows(); y++)
      for (int x = 1; x < map.columns(); x++) if (walkable(map, x, y)) return new int[] {x, y};
    throw new IllegalArgumentException("This map has no walkable cell");
  }

  private static boolean walkable(MapResource map, int x, int y) {
    return x >= 1 && y >= 1 && x < map.columns() && y < map.rows() && !map.blocked(x, y);
  }

  private static byte[] hash(char[] password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, 120000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("Password verifier unavailable", ex);
    } finally {
      spec.clearPassword();
    }
  }
}
