package vn.swordol.client.ui.legacy;

import static org.junit.Assert.*;

import org.junit.Test;

public class LegacyPlacementTest {
  @Test
  public void flipsTheAxisTheLayoutMeasuresDownwards() {
    // Window 114: the account box sits at y=54 and is 40 tall on a 360 tall window.
    assertEquals(360 - 54 - 40, LegacyPlacement.stageY(360, 54, 40));
    // A control at the very top ends flush with the top of the stage.
    assertEquals(360 - 40, LegacyPlacement.stageY(360, 0, 40));
    // One that fills the window starts at the bottom.
    assertEquals(0, LegacyPlacement.stageY(360, 0, 360));
  }

  @Test
  public void keepsTheOrderTheWindowShows() {
    // The box drawn higher in the original must end up higher on the stage.
    assertTrue(LegacyPlacement.stageY(360, 54, 40) > LegacyPlacement.stageY(360, 100, 40));
  }
}
