package vn.swordol.client.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import vn.swordol.assets.runtime.MapData;

public class MapCatalogTest {
  private MapCatalog catalog() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/bundles/demo.swb")) {
      assertNotNull("Build the demo asset bundle before running the client", input);
      return MapCatalog.load(input);
    }
  }

  @Test
  public void resolvesAMapTheServerNames() throws Exception {
    MapCatalog catalog = catalog();
    MapData map = catalog.require("world.demo-field", null);
    assertEquals(20, map.width());
    assertEquals(15, map.height());
    assertTrue(map.layerCount() >= 1);
    assertEquals(map, catalog.require("world.demo-field", map.version()));
  }

  @Test
  public void refusesAMapVersionTheServerDoesNotShare() throws Exception {
    MapCatalog catalog = catalog();
    String serverVersion = "0000000000000000000000000000000000000000000000000000000000000000";
    try {
      catalog.require("world.demo-field", serverVersion);
      fail("version mismatch accepted");
    } catch (IOException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains(serverVersion));
      assertTrue(
          expected.getMessage(),
          expected.getMessage().contains(catalog.require("world.demo-field", null).version()));
    }
  }

  @Test
  public void refusesAnUnknownMapId() throws Exception {
    try {
      catalog().require("world.nowhere", null);
      fail("unknown map accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void providesTilesetImageBytes() throws Exception {
    MapCatalog catalog = catalog();
    MapData map = catalog.require("world.demo-field", null);
    assertEquals(1, map.tilesets().size());
    byte[] image = catalog.image(map.tilesets().get(0).asset());
    assertArrayEquals(
        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, java.util.Arrays.copyOf(image, 8));
    try {
      catalog.image("world.demo-field");
      fail("a map was served as an image");
    } catch (IOException expected) {
    }
  }
}
