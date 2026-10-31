package vn.swordol.client.render;

import static org.junit.Assert.*;
import static vn.swordol.client.render.MapPlanTest.fixture;

import org.junit.Test;
import vn.swordol.protocol.wire.MapResource;

/** MapEx.setMapView centres the view on the entity and clamps it inside the map world. */
public class MapViewTest {
  @Test
  public void centresOnTheEntityAndStopsAtTheMapEdges() throws Exception {
    WorldMap map = new WorldMap(MapResource.read(fixture("map-data.bin")));
    int width = map.widthInPixels();
    assertEquals(74 * 16, width);
    assertEquals(0, MapView.offset(0, 400, width));
    assertEquals(0, MapView.offset(100, 400, width));
    assertEquals(400, MapView.offset(600, 400, width));
    assertEquals(width - 800, MapView.offset(width, 800, width));
    assertEquals(0, MapView.offset(500, width + 200, width));
  }

  @Test
  public void convertsCellsAndTheOriginalDownwardPixelsToGlCoordinates() throws Exception {
    WorldMap map = new WorldMap(MapResource.read(fixture("map-data.bin")));
    assertEquals(0, MapView.cellX(0, map));
    assertEquals(16 * 5, MapView.cellX(5, map));
    // Cell row0 is the top row in the original, so it is the highest row in GL space.
    assertEquals(map.heightInPixels() - 16, MapView.cellY(0, map));
    assertEquals(0, MapView.cellY(map.rows() - 1, map));
    assertEquals(map.heightInPixels() - 32 - 24, MapView.imageY(32, 24, map));
  }
}
