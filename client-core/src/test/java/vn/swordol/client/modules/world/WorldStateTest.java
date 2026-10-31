package vn.swordol.client.modules.world;

import static org.junit.Assert.*;
import static vn.swordol.client.render.MapPlanTest.fixture;

import org.junit.Test;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.Wire;
import vn.swordol.protocol.wire.WireWriter;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * Rules from the original: MapObject.checkmove refuses blocked cells and row/column zero,
 * processWalkCmd applies action3 as a correction and drops other players more than24 cells away,
 * and Engine.packetUserWalk sends the position the client has just moved to.
 */
public class WorldStateTest {
  @Test
  public void placesTheUserWhereTheServerPutsIt() throws Exception {
    WorldState world = loaded();
    world.user(WorldPackets.user(Wire.logical(userInfo(1235221, 20, 30))));
    assertEquals(1235221, world.userId());
    assertEquals(20, world.userX());
    assertEquals(30, world.userY());
    assertEquals("ThienThan", world.userName());
    assertEquals(1101400, world.mapId());
    assertEquals("Trung nguyên1Tuyến", world.mapName());
    assertTrue(world.hasMap());
  }

  @Test
  public void refusesStepsIntoBlockedCellsAndOffTheGrid() throws Exception {
    WorldState world = loaded();
    world.user(WorldPackets.user(Wire.logical(userInfo(7, 20, 30))));
    world.map().applyCell(21, 30, true, true);
    assertFalse(world.step(WorldState.EAST));
    assertEquals(20, world.userX());
    assertNull(world.takeWalkRequest());
    world.map().applyCell(21, 30, false, false);
    assertTrue(world.step(WorldState.EAST));
    assertEquals(21, world.userX());
    assertEquals(30, world.userY());
    byte[] request = world.takeWalkRequest();
    assertArrayEquals(WorldPackets.walkRequest(7, WorldState.EAST, 21, 30, 1101400), request);
    assertNull("one step sends one walk", world.takeWalkRequest());

    world.user(WorldPackets.user(Wire.logical(userInfo(7, 1, 1))));
    assertFalse(world.step(WorldState.WEST));
    assertFalse(world.step(WorldState.NORTH));
    assertEquals(1, world.userX());
    assertEquals(1, world.userY());
  }

  @Test
  public void takesTheServerCorrectionOverTheLocalPosition() throws Exception {
    WorldState world = loaded();
    world.user(WorldPackets.user(Wire.logical(userInfo(7, 20, 30))));
    world.walk(WorldPackets.walk(Wire.logical(walk(7, 3, 0, 40, 41, 1101400))));
    assertEquals(40, world.userX());
    assertEquals(41, world.userY());
    // The original ignores its own requested move coming back; only a correction moves the user.
    world.walk(WorldPackets.walk(Wire.logical(walk(7, 0, 1, 10, 10, 1101400))));
    assertEquals(40, world.userX());
    assertEquals(41, world.userY());
  }

  @Test
  public void keepsOtherPlayersOnlyInsideTheOriginalWindow() throws Exception {
    WorldState world = loaded();
    world.user(WorldPackets.user(Wire.logical(userInfo(7, 40, 40))));
    world.otherPlayer(WorldPackets.otherPlayer(Wire.logical(otherPlayer(99, 42, 44))));
    assertEquals(1, world.otherPlayers().size());
    assertEquals(42, world.otherPlayers().get(0).x());

    world.walk(WorldPackets.walk(Wire.logical(walk(99, 0, 2, 43, 44, 1101400))));
    assertEquals(43, world.otherPlayers().get(0).x());
    world.walk(WorldPackets.walk(Wire.logical(walk(99, 0, 2, 90, 44, 1101400))));
    assertTrue("beyond24 cells the original deletes the player", world.otherPlayers().isEmpty());

    world.walk(WorldPackets.walk(Wire.logical(walk(123, 0, 2, 41, 41, 1101400))));
    assertArrayEquals(WorldPackets.playerRequest(123), world.takePlayerRequest());
    assertNull(world.takePlayerRequest());
    world.walk(WorldPackets.walk(Wire.logical(walk(124, 0, 2, 95, 95, 1101400))));
    assertNull("an unknown player outside the window is not requested", world.takePlayerRequest());
  }

  @Test
  public void appliesPortalsCellAndTileUpdatesToTheLoadedMap() throws Exception {
    WorldState world = loaded();
    WireWriter portals =
        new WireWriter(1126)
            .signedByte(0)
            .signedByte(1)
            .signedByte(2)
            .signedByte(4)
            .signedByte(26)
            .text("Sườn hắc mộc 1Tuyến");
    world.mapUpdate(WorldPackets.mapUpdate(Wire.logical(portals.build())));
    assertEquals(1, world.portals().size());
    assertEquals(4, world.portals().get(0).x());
    assertEquals("Sườn hắc mộc 1Tuyến", world.portals().get(0).name());

    world.mapUpdate(
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(2)
                    .shortValue(1)
                    .signedByte(30)
                    .signedByte(31)
                    .signedByte(11)
                    .build())));
    assertFalse(world.map().walkable(30, 31));
    world.mapUpdate(
        WorldPackets.mapUpdate(
            Wire.logical(
                new WireWriter(1126)
                    .signedByte(3)
                    .signedByte(30)
                    .signedByte(31)
                    .signedByte(42)
                    .signedByte(1)
                    .build())));
    assertEquals(42, world.map().tile(30, 31));
    assertTrue(world.map().rotated(30, 31));
  }

  @Test
  public void keepsTheUserTheServerSendsBeforeTheMapAndClearsEverythingOnLeave() throws Exception {
    // Captured sessions send1006 action2 before the1407 map resources, so the user comes first.
    WorldState world = new WorldState();
    world.user(WorldPackets.user(Wire.logical(userInfo(7, 20, 30))));
    assertEquals(7, world.userId());
    assertFalse(world.hasMap());
    assertFalse("no map means no local movement", world.step(WorldState.EAST));
    assertNull(world.takeWalkRequest());

    world.map(
        WorldPackets.identity(
            Wire.logical(
                new WireWriter(1110)
                    .integer(1101400)
                    .integer(45)
                    .integer(0)
                    .text("Trung nguyên1Tuyến")
                    .build())),
        MapResource.read(fixture("map-data.bin")));
    assertEquals("the map load keeps the user entity", 7, world.userId());
    assertEquals(20, world.userX());
    assertTrue(world.step(WorldState.EAST));

    world.otherPlayer(WorldPackets.otherPlayer(Wire.logical(otherPlayer(99, 21, 31))));
    world.leave();
    assertFalse(world.hasMap());
    assertEquals(0, world.userId());
    assertTrue(world.otherPlayers().isEmpty());
    assertEquals(0, world.mapId());
  }

  @Test
  public void acceptsTheMapFieldTheServerActuallySendsAndRefusesAForeignMap() throws Exception {
    WorldState world = loaded();
    world.user(WorldPackets.user(Wire.logical(userInfo(7, 20, 30))));
    // Captured1005 frames carry0 in the map field, so0 means "this map".
    world.walk(WorldPackets.walk(Wire.logical(walk(7, 3, 0, 40, 41, 0))));
    assertEquals(40, world.userX());
    world.walk(WorldPackets.walk(Wire.logical(walk(7, 3, 0, 50, 51, 999))));
    assertEquals(40, world.userX());
    assertEquals(41, world.userY());
  }

  private static WorldState loaded() throws Exception {
    WorldState world = new WorldState();
    world.map(
        WorldPackets.identity(
            Wire.logical(
                new WireWriter(1110)
                    .integer(1101400)
                    .integer(45)
                    .integer(0)
                    .text("Trung nguyên1Tuyến")
                    .build())),
        MapResource.read(fixture("map-data.bin")));
    return world;
  }

  private static byte[] userInfo(int id, int x, int y) {
    WireWriter writer =
        new WireWriter(1006)
            .signedByte(2)
            .shortValue(0)
            .integer(id)
            .integer(220000)
            .text("ThienThan")
            .signedByte(104)
            .signedByte(21)
            .signedByte(0);
    for (int index = 8; index < 49; index++) {
      if (index == 35) writer.shortValue(x);
      else if (index == 36) writer.shortValue(y);
      else if (index == 47) writer.shortValue(6);
      else writer.integer(0);
    }
    return writer.build();
  }

  private static byte[] otherPlayer(int id, int x, int y) {
    WireWriter writer =
        new WireWriter(1014)
            .integer(id)
            .integer(220000)
            .integer(0)
            .integer(0)
            .integer(0)
            .shortValue(120)
            .shortValue(x)
            .shortValue(y);
    for (int index = 8; index < 37; index++) {
      if (index == 17) writer.text("Khách" + id);
      else if (index == 35) writer.shortValue(15);
      else writer.integer(0);
    }
    return writer.build();
  }

  private static byte[] walk(int id, int action, int direction, int x, int y, int mapId) {
    return new WireWriter(1005)
        .integer(id)
        .integer(action * 10000000 + direction * 1000000 + x * 1000 + y)
        .integer(mapId)
        .build();
  }
}
