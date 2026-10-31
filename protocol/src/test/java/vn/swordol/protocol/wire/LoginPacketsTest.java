package vn.swordol.protocol.wire;

import static org.junit.Assert.*;

import java.io.IOException;
import org.junit.Test;

public class LoginPacketsTest {
  @Test
  public void serverRowsExpandIntoIndependentChannelIndices() throws Exception {
    Packet packet =
        Wire.logical(
            new WireWriter(1077)
                .integer(0)
                .integer(0)
                .signedByte(2)
                .text("One")
                .text("one-token")
                .signedByte(1)
                .integer(2)
                .text("Two")
                .text("two-token")
                .signedByte(1)
                .integer(1)
                .build());
    java.util.List<LoginPackets.ServerLine> lines = LoginPackets.servers(packet);
    assertEquals(3, lines.size());
    assertEquals(0, lines.get(0).index());
    assertEquals(1, lines.get(1).index());
    assertEquals(0, lines.get(2).index());
    assertEquals("two-token", lines.get(2).token());
  }

  @Test
  public void mapsRoleSlotsAndRejectsDuplicatesAndTruncation() throws Exception {
    WireWriter out = new WireWriter(1080).signedByte(1);
    appendRole(out, 42, "Local role", 2);
    LoginPackets.Role role = LoginPackets.roles(Wire.logical(out.build())).get(0);
    assertEquals(42, role.id());
    assertEquals(2, role.slot());
    assertEquals("Local role", role.name());
    WireWriter duplicate = new WireWriter(1080).signedByte(2);
    appendRole(duplicate, 42, "A", 0);
    appendRole(duplicate, 43, "B", 0);
    try {
      LoginPackets.roles(Wire.logical(duplicate.build()));
      fail();
    } catch (IOException expected) {
    }
    try {
      LoginPackets.roles(Wire.logical(new WireWriter(1080).signedByte(1).build()));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void gameHandoffUsesSocketPortThreeWhenPresent() throws Exception {
    Packet packet =
        Wire.logical(
            new WireWriter(1052)
                .integer(1)
                .integer(2)
                .integer(80)
                .text("127.0.0.1")
                .signedByte(66)
                .signedByte(49)
                .signedByte(1)
                .integer(9000)
                .text("127.0.0.2")
                .integer(9001)
                .text("")
                .signedByte(0)
                .build());
    LoginPackets.Handoff handoff = LoginPackets.handoff(packet);
    assertEquals(9001, handoff.port());
    assertEquals("127.0.0.1", handoff.host());
    assertArrayEquals(new byte[] {66, 49}, handoff.header());
  }

  private static void appendRole(WireWriter out, int id, String name, int slot) {
    out.integer(id)
        .integer(1)
        .integer(100000)
        .signedByte(0)
        .shortValue(10)
        .text(name)
        .signedByte(slot);
    for (int i = 7; i < 30; i++) out.integer(0);
  }
}
