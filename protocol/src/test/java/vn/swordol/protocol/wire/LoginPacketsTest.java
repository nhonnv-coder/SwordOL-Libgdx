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

  @Test
  public void writesTheRegistrationRequestTheAccountScreenSends() throws Exception {
    Packet packet = Wire.logical(LoginPackets.register("account01", "password01", "answer0001"));
    assertEquals(1055, packet.opcode());
    assertEquals(8, packet.fields().size());
    // Business.initRegisterPacket, in its own order.
    assertEquals(0, LoginPackets.number(packet, 0));
    assertEquals("account01", LoginPackets.text(packet, 1));
    assertEquals("password01", LoginPackets.text(packet, 2));
    assertEquals("answer0001", LoginPackets.text(packet, 3));
    assertEquals("", LoginPackets.text(packet, 4));
    // getFenBaoID reads PackageID.o and getBaoUserID its own file; this build ships neither.
    assertEquals(0, LoginPackets.number(packet, 5));
    assertEquals(0, LoginPackets.number(packet, 6));
    assertEquals(9, LoginPackets.number(packet, 7));
  }

  @Test
  public void refusesRegistrationFieldsShorterThanTheScreenAccepts() {
    // CustomScreen rejects any of the three boxes under six characters with other_str[583].
    assertRegisterRejected("short", "password01", "answer0001");
    assertRegisterRejected("account01", "pw", "answer0001");
    assertRegisterRejected("account01", "password01", "a");
    assertRegisterRejected(null, "password01", "answer0001");
  }

  private static void assertRegisterRejected(String account, String password, String third) {
    try {
      LoginPackets.register(account, password, third);
      fail("accepted a registration the screen refuses");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void writesTheCreateRoleRequestFieldForFieldLikeTheOriginal() throws Exception {
    LoginPackets.Handoff handoff = handoff();
    Packet packet =
        Wire.logical(LoginPackets.createRole("Kiem Khach", RoleLooks.FEMALE, 2, 1, handoff, 3));
    assertEquals(1001, packet.opcode());
    assertEquals(12, packet.fields().size());
    // Business.NewRoleWnd_EditOK, in its own order.
    assertEquals("Kiem Khach", LoginPackets.text(packet, 0));
    assertEquals(RoleLooks.FEMALE, LoginPackets.number(packet, 1));
    assertEquals(2 * 10 + 1, LoginPackets.number(packet, 2));
    assertEquals(0, LoginPackets.number(packet, 3));
    // TCPBase.usID, which processLoginBackCmd sets to the account id of the 1052 response.
    assertEquals(handoff.account(), LoginPackets.number(packet, 4));
    assertEquals(0, LoginPackets.number(packet, 5));
    assertEquals("", LoginPackets.text(packet, 6));
    assertEquals(1, LoginPackets.number(packet, 7));
    assertEquals(0, LoginPackets.number(packet, 8));
    assertEquals(3, LoginPackets.number(packet, 9));
    assertEquals(0, LoginPackets.number(packet, 10));
    assertEquals(0, LoginPackets.number(packet, 11));
  }

  @Test
  public void refusesCreateRoleArgumentsTheCreationScreenCannotProduce() {
    LoginPackets.Handoff handoff = handoff();
    // Profession 9 is {0, 0} in the look table; the original never sends it.
    assertCreateRoleRejected("A", RoleLooks.MALE, 9, 0, handoff, 0);
    // Profession 2 offers no male look.
    assertCreateRoleRejected("A", RoleLooks.MALE, 2, 0, handoff, 0);
    assertCreateRoleRejected("A", 2, 1, 0, handoff, 0);
    // The creation screen holds three slots and limits the name to ten characters.
    assertCreateRoleRejected("A", RoleLooks.MALE, 1, 3, handoff, 0);
    assertCreateRoleRejected("", RoleLooks.MALE, 1, 0, handoff, 0);
    assertCreateRoleRejected("   ", RoleLooks.MALE, 1, 0, handoff, 0);
    assertCreateRoleRejected("12345678901", RoleLooks.MALE, 1, 0, handoff, 0);
  }

  private static void assertCreateRoleRejected(
      String name, int sex, int profession, int slot, LoginPackets.Handoff handoff, int line) {
    try {
      LoginPackets.createRole(name, sex, profession, slot, handoff, line);
      fail("accepted a request the creation screen cannot produce");
    } catch (IllegalArgumentException expected) {
    }
  }

  private static LoginPackets.Handoff handoff() {
    try {
      return LoginPackets.handoff(
          Wire.logical(
              new WireWriter(1052)
                  .integer(77)
                  .integer(88)
                  .integer(1)
                  .text("127.0.0.1")
                  .integer(0)
                  .integer(0)
                  .integer(0)
                  .integer(7781)
                  .integer(0)
                  .integer(0)
                  .integer(0)
                  .integer(0)
                  .build()));
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
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
