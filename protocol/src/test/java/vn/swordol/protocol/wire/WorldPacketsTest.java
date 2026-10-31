package vn.swordol.protocol.wire;

import static org.junit.Assert.*;
import static vn.swordol.protocol.wire.MapResourceTest.reject;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Field positions come from
 * CmdProcessor.processMapInfoCmd/processUserInfoCmd/processOtherPlayerCmd/
 * processWalkCmd/processMapPortalCmd and Engine.packetUserWalk. The shapes below are the shapes
 * real captured inbound frames carry; no credential or chat packet is parsed here.
 */
public class WorldPacketsTest {
  @Test
  public void readsTheMapIdentityTheServerAnnounces() throws Exception {
    WorldPackets.MapIdentity identity =
        WorldPackets.identity(
            Wire.logical(
                new WireWriter(1110)
                    .integer(1101400)
                    .integer(45)
                    .integer(0)
                    .text("Trung nguyên1Tuyến")
                    .build()));
    assertEquals(1101400, identity.mapId());
    assertEquals(45, identity.resourceId());
    assertEquals(0, identity.mapType());
    assertEquals("Trung nguyên1Tuyến", identity.name());
    reject(() ->
        WorldPackets.identity(
            Wire.logical(new WireWriter(1110).integer(1).integer(2).integer(3).build())));
    reject(() ->
        WorldPackets.identity(
            Wire.logical(new WireWriter(1005).integer(1).integer(2).integer(3).text("x").build())));
  }

  @Test
  public void readsTheUsersOwnEntityFromTheCapturedUserInfoShape() throws Exception {
    Packet packet = Wire.logical(userInfo(2, 1235221, 220000, "ThienThan", 65, 12));
    WorldPackets.UserInfo user = WorldPackets.user(packet);
    assertEquals(1235221, user.id());
    assertEquals("ThienThan", user.name());
    assertEquals(220000, user.look());
    assertEquals(0, user.hair());
    assertEquals(65, user.x());
    assertEquals(12, user.y());
    assertEquals(1601590941, user.weaponType());
    reject(() -> WorldPackets.user(Wire.logical(userInfo(5, 1, 1, "x", 1, 1))));
    reject(() -> WorldPackets.user(Wire.logical(new WireWriter(1006).signedByte(2).build())));
  }

  @Test
  public void readsAnotherPlayerAndItsPositionFromTheCapturedShape() throws Exception {
    WorldPackets.PlayerInfo other =
        WorldPackets.otherPlayer(Wire.logical(otherPlayer(1174776, 220000, "Vân-Anh", 40, 56)));
    assertEquals(1174776, other.id());
    assertEquals(220000, other.look());
    assertEquals("Vân-Anh", other.name());
    assertEquals(40, other.x());
    assertEquals(56, other.y());
    reject(() -> WorldPackets.otherPlayer(Wire.logical(new WireWriter(1014).integer(1).build())));
  }

  @Test
  public void decodesWalkWithTheOriginalPackedFieldAndRejectsImpossibleCells() throws Exception {
    WorldPackets.Walk walk =
        WorldPackets.walk(
            Wire.logical(
                new WireWriter(1005).integer(1000694).integer(6010014).integer(1101400).build()));
    assertEquals(1000694, walk.id());
    assertEquals(0, walk.action());
    assertEquals(6, walk.direction());
    assertEquals(10, walk.x());
    assertEquals(14, walk.y());
    assertEquals(1101400, walk.mapId());
    WorldPackets.Walk correction =
        WorldPackets.walk(
            Wire.logical(new WireWriter(1005).integer(7).integer(30065012).integer(5).build()));
    assertEquals(3, correction.action());
    assertEquals(0, correction.direction());
    assertEquals(65, correction.x());
    assertEquals(12, correction.y());
    reject(() ->
        WorldPackets.walk(
            Wire.logical(new WireWriter(1005).integer(1).integer(-1).integer(1).build())));
    reject(() ->
        WorldPackets.walk(
            Wire.logical(new WireWriter(1110).integer(1).integer(6010014).integer(1).build())));
    reject(() ->
        WorldPackets.walk(Wire.logical(new WireWriter(1005).integer(1).integer(6010014).build())));
  }

  @Test
  public void readsPortalsCollisionAndTileUpdatesAndRefusesOtherActions() throws Exception {
    WireWriter portals = new WireWriter(1126).signedByte(0).signedByte(2);
    portals.signedByte(2).signedByte(4).signedByte(26).text("Sườn hắc mộc 1Tuyến");
    portals.signedByte(6).signedByte(44).signedByte(71).text("Quân Sơn Tuyệt Đỉnh 1Tuyến");
    WorldPackets.MapUpdate update = WorldPackets.mapUpdate(Wire.logical(portals.build()));
    assertEquals(0, update.action());
    assertEquals(2, update.portals().size());
    assertEquals(2, update.portals().get(0).id());
    assertEquals(4, update.portals().get(0).x());
    assertEquals(26, update.portals().get(0).y());
    assertEquals("Sườn hắc mộc 1Tuyến", update.portals().get(0).name());
    assertEquals(71, update.portals().get(1).y());
    assertTrue(update.cells().isEmpty());

    WorldPackets.MapUpdate cells =
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(2)
                    .shortValue(2)
                    .signedByte(65)
                    .signedByte(47)
                    .signedByte(0)
                    .signedByte(63)
                    .signedByte(49)
                    .signedByte(11)
                    .build()));
    assertEquals(2, cells.action());
    assertEquals(2, cells.cells().size());
    assertEquals(65, cells.cells().get(0).x());
    assertEquals(47, cells.cells().get(0).y());
    assertFalse(cells.cells().get(0).blocked());
    assertFalse(cells.cells().get(0).flightBlocked());
    assertTrue(cells.cells().get(1).blocked());
    assertTrue(cells.cells().get(1).flightBlocked());

    WorldPackets.MapUpdate tile =
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(3)
                    .signedByte(4)
                    .signedByte(7)
                    .signedByte(37)
                    .signedByte(1)
                    .build()));
    assertEquals(3, tile.action());
    assertEquals(4, tile.tile().x());
    assertEquals(7, tile.tile().y());
    assertEquals(37, tile.tile().tile());
    assertTrue(tile.tile().rotated());

    reject(() -> WorldPackets.mapUpdate(Wire.logical(new WireWriter(1126).signedByte(1).build())));
    reject(() ->
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(0)
                    .signedByte(2)
                    .signedByte(1)
                    .signedByte(1)
                    .signedByte(1)
                    .text("one")
                    .build())));
    reject(() ->
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(2)
                    .shortValue(1)
                    .signedByte(1)
                    .signedByte(1)
                    .build())));
  }

  @Test
  public void outboundWalkAndPlayerRequestMatchTheOriginalPacketBytes() throws Exception {
    byte[] walk = WorldPackets.walkRequest(1235221, 6, 65, 12, 1101400);
    byte[] player = WorldPackets.playerRequest(1174776);
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {Paths.get("../docs/files/VoLamHoiTu.jar").toAbsolutePath().toUri().toURL()},
            null)) {
      Class<?> type = loader.loadClass("base.utils.Packet");
      assertArrayEquals(original(type, 1005, 1235221, 6 * 1000000 + 65 * 1000 + 12, 1101400), walk);
      assertArrayEquals(original(type, 1014, 1174776), player);
    }
    reject(() -> WorldPackets.walkRequest(1, 8, 1, 1, 1));
    reject(() -> WorldPackets.walkRequest(1, 1, 1000, 1, 1));
    reject(() -> WorldPackets.walkRequest(1, 1, 1, -1, 1));
    reject(() -> WorldPackets.playerRequest(0));
  }

  private static byte[] original(Class<?> type, int opcode, int... values) throws Exception {
    Object packet = type.getConstructor().newInstance();
    type.getMethod("init", int.class).invoke(packet, opcode);
    for (int value : values) type.getMethod("writeInt", int.class).invoke(packet, value);
    return (byte[]) type.getMethod("toByteArray").invoke(packet);
  }

  /** The 49-field shape captured for1006 action2; only the fields the client reads carry values. */
  static byte[] userInfo(int action, int id, int look, String name, int x, int y) {
    WireWriter writer =
        new WireWriter(1006)
            .signedByte(action)
            .shortValue(0)
            .integer(id)
            .integer(look)
            .text(name)
            .signedByte(104)
            .signedByte(21)
            .signedByte(0);
    for (int index = 8; index < 49; index++) {
      if (index == 34) writer.integer(1601590941);
      else if (index == 35) writer.shortValue(x);
      else if (index == 36) writer.shortValue(y);
      else if (index == 47) writer.shortValue(6);
      else writer.integer(0);
    }
    return writer.build();
  }

  /** The 37-field shape captured for1014. */
  static byte[] otherPlayer(int id, int look, String name, int x, int y) {
    WireWriter writer =
        new WireWriter(1014)
            .integer(id)
            .integer(look)
            .integer(32)
            .integer(1601590901)
            .integer(121390300)
            .shortValue(120)
            .shortValue(x)
            .shortValue(y);
    for (int index = 8; index < 37; index++) {
      if (index == 17) writer.text(name);
      else if (index == 20) writer.signedByte(0);
      else if (index == 35) writer.shortValue(15);
      else writer.integer(0);
    }
    return writer.build();
  }
}
