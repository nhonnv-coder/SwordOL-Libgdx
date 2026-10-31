package vn.swordol.protocol.wire;

import static org.junit.Assert.*;

import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;

public class WireTest {
  @Test
  public void writerMatchesOriginalPacketByteForByte() throws Exception {
    byte[] actual =
        new WireWriter(1051)
            .text("local-fixture")
            .text("not-a-real-secret")
            .text("line")
            .integer(0)
            .signedByte(1)
            .signedByte(0)
            .text("JT_N5800F")
            .integer(250615)
            .signedByte(0)
            .build();
    try (URLClassLoader loader =
        new URLClassLoader(
            new java.net.URL[] {
              Paths.get("../docs/files/VoLamHoiTu.jar").toAbsolutePath().toUri().toURL()
            },
            null)) {
      Class<?> type = loader.loadClass("base.utils.Packet");
      Object old = type.getConstructor().newInstance();
      type.getMethod("init", int.class).invoke(old, 1051);
      for (Object[] call :
          new Object[][] {
            {"writeString", "local-fixture"},
            {"writeString", "not-a-real-secret"},
            {"writeString", "line"},
            {"writeInt", 0},
            {"writeByte", 1},
            {"writeByte", 0},
            {"writeString", "JT_N5800F"},
            {"writeInt", 250615},
            {"writeByte", 0}
          }) {
        type.getMethod((String) call[0], call[1] instanceof String ? String.class : int.class)
            .invoke(old, call[1]);
      }
      assertArrayEquals((byte[]) type.getMethod("toByteArray").invoke(old), actual);
    }
  }

  @Test
  public void tcpOutboundRoundTripsAndRejectsBadCrcAndTruncation() throws Exception {
    byte[] logical =
        new WireWriter(1081).integer(42).signedByte(0).integer(250615).text("JT_N5800F").build();
    byte[] wire = Wire.tcpRequest(logical, 255);
    assertEquals(0xfe, wire[0] & 255);
    assertEquals(0xaa, wire[wire.length - 1] & 255);
    assertArrayEquals(logical, Wire.readTcpRequest(new ByteArrayInputStream(wire)));
    wire[7] ^= 1;
    reject(() -> Wire.readTcpRequest(new ByteArrayInputStream(wire)));
    for (int size : new int[] {1, 4, wire.length - 1}) {
      byte[] partial = Arrays.copyOf(wire, size);
      reject(() -> Wire.readTcpRequest(new ByteArrayInputStream(partial)));
    }
  }

  @Test
  public void accountBatchKeepsSessionAndPacketBoundaries() throws Exception {
    byte[] logical =
        new WireWriter(1077).integer(250615).text("JT_N5800F").signedByte(0).text("").build();
    byte[] request = Wire.httpRequest(logical, 7, 9, 1);
    Wire.Batch decoded = Wire.readHttpRequest(new ByteArrayInputStream(request));
    assertEquals(7, decoded.session());
    assertEquals(9, decoded.random());
    assertEquals(1077, decoded.packets().get(0).opcode());
    byte[] response = Wire.httpResponse(Arrays.asList(logical, logical), 12, 13);
    Wire.Batch received = Wire.readHttpResponse(new ByteArrayInputStream(response));
    assertEquals(12, received.session());
    assertEquals(2, received.packets().size());
    byte[] broken = response.clone();
    broken[10] = 3;
    reject(() -> Wire.readHttpResponse(new ByteArrayInputStream(broken)));
  }

  private interface Io {
    void run() throws IOException;
  }

  private static void reject(Io action) throws Exception {
    try {
      action.run();
      fail("Malformed wire accepted");
    } catch (IOException expected) {
    }
  }
}
