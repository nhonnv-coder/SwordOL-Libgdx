package vn.swordol.client.ui.legacy;

import static org.junit.Assert.*;

import org.junit.Test;

public class LegacyImageCellTest {
  @Test
  public void takesTheCellWidthOutOfTheImageIdTheWayTheOriginalDoes() {
    // 4554343 % 10000 / 100 = 43: the login window's strip is cut into 43 pixel cells.
    assertEquals(43, LegacyImageCell.cellWidth(4554343, 860));
    // 6240000 gives zero, so the whole image is one cell.
    assertEquals(120, LegacyImageCell.cellWidth(6240000, 120));
  }

  @Test
  public void stepsAlongTheStripByTheIndex() {
    assertEquals(0, LegacyImageCell.sourceX(0, 43));
    assertEquals(43, LegacyImageCell.sourceX(1, 43));
    assertEquals(129, LegacyImageCell.sourceX(3, 43));
  }

  @Test
  public void refusesACellTheStripDoesNotReach() {
    assertTrue(LegacyImageCell.holds(0, 43, 86));
    assertTrue(LegacyImageCell.holds(1, 43, 86));
    // A third 43 pixel cell would need 129 pixels.
    assertFalse(LegacyImageCell.holds(2, 43, 86));
    assertFalse(LegacyImageCell.holds(-1, 43, 86));
    assertFalse(LegacyImageCell.holds(0, 0, 86));
  }
}
