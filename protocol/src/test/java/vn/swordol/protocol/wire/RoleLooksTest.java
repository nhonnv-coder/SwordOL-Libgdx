package vn.swordol.protocol.wire;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * The creation screen's own look table, {@code Business.newRoleLookFaceDatID}. Ten professions by
 * two sexes; a zero means the original deactivates that sex button, and a row that is zero in both
 * columns is a profession {@code NewRoleWnd_OK} refuses to send at all.
 */
public class RoleLooksTest {
  @Test
  public void givesTheLookFaceTheOriginalTableNames() {
    // Row 1 holds both sexes: prefix 1 is male, prefix 2 is female.
    assertEquals(110000, RoleLooks.lookFace(1, RoleLooks.MALE));
    assertEquals(210000, RoleLooks.lookFace(1, RoleLooks.FEMALE));
    assertEquals(220000, RoleLooks.lookFace(2, RoleLooks.FEMALE));
    assertEquals(130000, RoleLooks.lookFace(3, RoleLooks.MALE));
    assertEquals(280000, RoleLooks.lookFace(8, RoleLooks.FEMALE));
  }

  @Test
  public void reportsTheSexEachProfessionActuallyOffers() {
    assertTrue(RoleLooks.offers(1, RoleLooks.MALE));
    assertTrue(RoleLooks.offers(1, RoleLooks.FEMALE));
    // Profession 2 is {0, 220000}: the original forces the female button.
    assertFalse(RoleLooks.offers(2, RoleLooks.MALE));
    assertTrue(RoleLooks.offers(2, RoleLooks.FEMALE));
    // Profession 3 is {130000, 0}: the other way round.
    assertTrue(RoleLooks.offers(3, RoleLooks.MALE));
    assertFalse(RoleLooks.offers(3, RoleLooks.FEMALE));
    assertEquals(0, RoleLooks.lookFace(2, RoleLooks.MALE));
  }

  @Test
  public void refusesTheProfessionsTheOriginalNeverSends() {
    // Rows 9 and 10 are {0, 0}; NewRoleWnd_OK returns without building a packet for them.
    assertFalse(RoleLooks.creatable(9));
    assertFalse(RoleLooks.creatable(10));
    for (int profession = 1; profession <= 8; profession++) {
      assertTrue("profession " + profession, RoleLooks.creatable(profession));
    }
  }

  @Test
  public void refusesIndexesOutsideTheTable() {
    assertFalse(RoleLooks.creatable(0));
    assertFalse(RoleLooks.creatable(11));
    assertFalse(RoleLooks.offers(1, 2));
    assertEquals(0, RoleLooks.lookFace(0, RoleLooks.MALE));
    assertEquals(0, RoleLooks.lookFace(11, RoleLooks.MALE));
  }
}
