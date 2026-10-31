package vn.swordol.client.render;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import vn.swordol.assets.runtime.SpriteData;

/**
 * The original's own look rules: MapObject's constructor picks the sprite file,
 * ISpriteEx.ISpriteImageInit rewrites the image table for a player look, Engine.getIspActionIndex
 * turns an action and a direction into an action id, and ISpriteEx.paint mirrors the eastern
 * direction with coordinateTransform and transformMirror.
 */
public class LookTest {
  @Test
  public void picksTheSpriteFileEachLookRangeNames() {
    // look2: profession22 maps to dat3, so a player look reaches sprite300000.
    assertEquals(300000, Look.spriteId(220000));
    assertEquals(200000, Look.spriteId(240000));
    assertEquals(100000, Look.spriteId(110000));
    // look3 npc: the file id is the middle digits of the look.
    assertEquals(150, Look.spriteId(315000));
    // look4 monster: those digits times ten thousand.
    assertEquals(1000000, Look.spriteId(410000));
    assertEquals(710000, Look.spriteId(710000));
    assertEquals(2010000, Look.spriteId(2010000));
    assertEquals(1110000, Look.spriteId(1110000));
    // Mounts and crops are assembled elsewhere in the original, so this does not guess a file.
    assertEquals(0, Look.spriteId(810000));
    assertEquals(0, Look.spriteId(910000));
    assertEquals(0, Look.spriteId(0));
  }

  @Test
  public void rewritesAPlayerImageTableTheWayTheOriginalDoes() {
    int[] table = new int[21];
    for (int index = 0; index < table.length; index++) {
      table[index] = 20202000 + index;
    }
    int[] rewritten = Look.imageTable(table, 220000);
    assertNotSame(table, rewritten);
    assertEquals(20202000, table[0]);

    // Sex is the look's leading digit and scan the last digit of its head, both2 here.
    assertEquals(2 * 10000000 + 2 * 100000 + 2 * 1000, rewritten[0]);
    assertEquals(0, rewritten[1]);
    assertEquals(0, rewritten[8]);
    assertEquals(20202009, rewritten[9]);
    assertEquals(20202010, rewritten[10]);
    for (int index = 11; index < 20; index++) {
      assertEquals("slot " + index, 0, rewritten[index]);
    }
    // The original's loop stops one short, so the last slot keeps whatever the file declared.
    assertEquals(20202020, rewritten[20]);
  }

  @Test
  public void doublesTheBaseOfALowSlotAndLeavesOtherLooksAlone() {
    int[] table = {10102000, 20202000, 30302000};
    int[] rewritten = Look.imageTable(table, 220000);
    assertEquals(2 * 10000000 + 101 % 100 * 100000 + 2000, rewritten[0]);
    int[] npc = {5, 6, 7};
    assertArrayEquals(npc, Look.imageTable(npc, 315000));
  }

  @Test
  public void mapsActionAndDirectionOntoTheStoredActionIds() {
    assertEquals(0, Look.actionId(0, Look.SOUTH));
    assertEquals(2, Look.actionId(0, Look.NORTH));
    assertEquals(100, Look.actionId(1, Look.SOUTH));
    assertEquals(102, Look.actionId(1, Look.NORTH));
    // West is what the sprite stores; east is the same frames mirrored.
    assertEquals(101, Look.actionId(1, Look.WEST));
    assertEquals(101, Look.actionId(1, Look.EAST));
    assertFalse(Look.mirrored(Look.WEST));
    assertTrue(Look.mirrored(Look.EAST));
    assertFalse(Look.mirrored(Look.SOUTH));
  }

  @Test
  public void buildsTheDrawsOfOneStoredFrame() throws Exception {
    SpriteData sprite = sprite();
    int[] table = {2990000};
    SpritePlan.Frame frame = SpritePlan.build(sprite, table, 1, 0, false);
    assertFalse(frame.draws().isEmpty());
    SpritePlan.Draw first = frame.draws().get(0);
    SpriteData.Module module =
        sprite.module(sprite.placements(sprite.actionFrame(1, 0)).get(0).moduleIndex());
    assertEquals(2990000, first.imageId());
    assertEquals(module.x(), first.sourceX());
    assertEquals(module.y(), first.sourceY());
    assertEquals(module.width(), first.width());
    assertEquals(module.height(), first.height());
    assertEquals(-36, first.x());
    assertEquals(-16, first.y());
    assertEquals(0, first.transform());
  }

  @Test
  public void mirrorsEveryPlacementAndItsTransformWhenFacingEast() throws Exception {
    SpriteData sprite = sprite();
    int[] table = {2990000};
    List<SpritePlan.Draw> plain = SpritePlan.build(sprite, table, 1, 0, false).draws();
    List<SpritePlan.Draw> mirrored = SpritePlan.build(sprite, table, 1, 0, true).draws();
    assertEquals(plain.size(), mirrored.size());
    for (int index = 0; index < plain.size(); index++) {
      SpritePlan.Draw before = plain.get(index);
      SpritePlan.Draw after = mirrored.get(index);
      assertEquals(before.y(), after.y());
      int rotated = before.transform() >= 4 ? before.height() : before.width();
      assertEquals(-before.x() - rotated, after.x());
      assertEquals(Look.mirrorTransform(before.transform()), after.transform());
    }
  }

  @Test
  public void skipsSlotsTheImageTableEmptiesAndRefusesAMissingAction() throws Exception {
    SpriteData sprite = sprite();
    assertTrue(SpritePlan.build(sprite, new int[] {0}, 1, 0, false).draws().isEmpty());
    try {
      SpritePlan.build(sprite, new int[] {2990000}, 999, 0, false);
      fail("accepted an action the sprite does not have");
    } catch (IndexOutOfBoundsException expected) {
      // the caller has to fall back the way Engine.getIspActionIndex does
    }
    // Steps wrap, so a player can keep ticking without checking the action length itself.
    assertEquals(
        SpritePlan.build(sprite, new int[] {2990000}, 1, 0, false).draws().size(),
        SpritePlan.build(sprite, new int[] {2990000}, 1, sprite.actionFrameCount(1), false)
            .draws()
            .size());
  }

  private static SpriteData sprite() throws IOException {
    try (InputStream input = LookTest.class.getResourceAsStream("/sprites/2010000.sprite.json")) {
      assertNotNull(input);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
        bytes.write(buffer, 0, read);
      }
      return SpriteData.read(bytes.toByteArray());
    }
  }
}
