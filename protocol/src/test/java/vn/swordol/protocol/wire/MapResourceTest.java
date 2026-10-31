package vn.swordol.protocol.wire;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.Test;

/**
 * Oracle: map-data.bin captured for map1101400 and the independently verified export of the same
 * bytes by tools/analysis/extract_maps.py (docs/analysis + dev/server-maps).
 */
public class MapResourceTest {
  @Test
  public void readsTheCapturedMapDocumentExactlyAsTheVerifiedExport() throws Exception {
    MapResource map = MapResource.read(fixture("map-data.bin"));
    assertEquals(74, map.columns());
    assertEquals(74, map.rows());
    assertEquals(16, map.cellWidth());
    assertEquals(16, map.cellHeight());
    assertEquals(0, map.tile(0, 0));
    assertEquals(37, map.tile(20, 10));
    assertEquals(0, map.tile(73, 73));
    assertEquals(
        "cf91ede0eadef2b8f1c7a54b0abc9994b1e67219d324445caa281dc6a5feb650", sha256(map.tiles()));
    assertEquals(
        "a47e8a4f910c58340868c332c65b7a1cebecbd2cc871a8f8636066da883fb929",
        sha256(map.blockingBits()));
    assertEquals(
        "d38e02a0695b4aefd9130617861af6330115891d4e5bae1c28ada22116c66d5f",
        sha256(map.rotationBits()));
    assertEquals(
        "65c7c873129f3639642bc5ed31683f8944d70555d83bbec2fdd6ceb4f3b36a5b",
        sha256(map.flightBlockingBits()));
    assertEquals(685, map.blockingBits().length);
  }

  @Test
  public void exposesCellBitsLeastSignificantFirstAndCopiesItsArrays() throws Exception {
    MapResource map = MapResource.read(fixture("map-data.bin"));
    byte[] blocking = map.blockingBits();
    for (int y = 0; y < map.rows(); y++) {
      for (int x = 0; x < map.columns(); x++) {
        int cell = y * map.columns() + x;
        boolean expected = (blocking[cell / 8] & (1 << (cell % 8))) != 0;
        assertEquals("cell " + x + "," + y, expected, map.blocked(x, y));
      }
    }
    Arrays.fill(blocking, (byte) 0xff);
    assertEquals(
        "a47e8a4f910c58340868c332c65b7a1cebecbd2cc871a8f8636066da883fb929",
        sha256(map.blockingBits()));
  }

  @Test
  public void rejectsSizesAndDimensionsTheClientCouldNotIndex() throws Exception {
    byte[] valid = fixture("map-data.bin");
    reject(() -> MapResource.read(Arrays.copyOf(valid, valid.length - 1)));
    reject(() -> MapResource.read(Arrays.copyOf(valid, valid.length + 1)));
    reject(() -> MapResource.read(new byte[] {1, 0, 4, 16, 16}));
    reject(() -> MapResource.read(new byte[] {1, (byte) 200, 4, 16, 16}));
    reject(() -> MapResource.read(new byte[4]));
    reject(() -> MapResource.read(null));
    byte[] zeroCell = new byte[] {1, 1, 1, 0, 16, 0, 0, 0, 0};
    reject(() -> MapResource.read(zeroCell));
  }

  @Test
  public void refusesCoordinatesOutsideTheGrid() throws Exception {
    MapResource map = MapResource.read(fixture("map-data.bin"));
    for (int[] cell : new int[][] {{-1, 0}, {0, -1}, {74, 0}, {0, 74}}) {
      try {
        map.tile(cell[0], cell[1]);
        fail("Accepted out-of-grid tile");
      } catch (IndexOutOfBoundsException expected) {
      }
      try {
        map.blocked(cell[0], cell[1]);
        fail("Accepted out-of-grid cell");
      } catch (IndexOutOfBoundsException expected) {
      }
    }
  }

  static byte[] fixture(String name) throws IOException {
    try (InputStream input = MapResourceTest.class.getResourceAsStream("/maps/" + name)) {
      assertNotNull("Missing fixture " + name, input);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read = input.read(buffer); read != -1; read = input.read(buffer))
        bytes.write(buffer, 0, read);
      return bytes.toByteArray();
    }
  }

  static String sha256(byte[] data) throws Exception {
    StringBuilder text = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(data))
      text.append(String.format("%02x", b));
    return text.toString();
  }

  interface Io {
    void run() throws IOException;
  }

  static void reject(Io action) throws Exception {
    try {
      action.run();
      fail("Malformed map resource accepted");
    } catch (IOException | IllegalArgumentException | NullPointerException expected) {
    }
  }
}
