package vn.swordol.client.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import vn.swordol.assets.runtime.MapData;

public class WorldSyncTest {
  private MapCatalog catalog() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/bundles/demo.swb")) {
      assertNotNull("Build the demo asset bundle before running the client", input);
      return MapCatalog.load(input);
    }
  }

  private JsonObject area(String id, String version) {
    JsonObject area = new JsonObject();
    if (id != null) {
      area.addProperty("id", id);
    }
    if (version != null) {
      area.addProperty("version", version);
    }
    area.addProperty("width", 20);
    area.addProperty("height", 15);
    return area;
  }

  @Test
  public void loadsTheMapTheServerNamesAndReportsItReady() throws Exception {
    WorldSync sync = new WorldSync(catalog());
    assertFalse(sync.ready());
    assertTrue(sync.sync(area("world.demo-field", null)));
    assertTrue(sync.ready());
    assertNull(sync.error());
    assertEquals(20, sync.map().width());
    assertEquals("playing", sync.status("playing"));
  }

  @Test
  public void reloadsOnlyWhenTheServerChangesMap() throws Exception {
    WorldSync sync = new WorldSync(catalog());
    assertTrue(sync.sync(area("world.demo-field", null)));
    MapData first = sync.map();
    assertFalse(sync.sync(area("world.demo-field", first.version())));
    assertEquals(first, sync.map());
    assertTrue(sync.sync(area("world.town", null)));
    assertEquals(10, sync.map().width());
  }

  @Test
  public void keepsRefusingUntilTheServerAgreesOnTheMapVersion() throws Exception {
    WorldSync sync = new WorldSync(catalog());
    String wrong = "0000000000000000000000000000000000000000000000000000000000000000";
    assertTrue(sync.sync(area("world.demo-field", wrong)));
    assertFalse(sync.ready());
    assertNull(sync.map());
    assertNotNull(sync.error());
    assertTrue(sync.error(), sync.error().contains(wrong));
    // A later server status must not bury the refusal, and the player must not keep playing.
    assertEquals(sync.error(), sync.status("Connected. Click a tile to move."));
    assertFalse(sync.canInteract());
    assertTrue(sync.sync(area("world.demo-field", null)));
    assertTrue(sync.ready());
    assertTrue(sync.canInteract());
    assertEquals("Connected.", sync.status("Connected."));
  }

  @Test
  public void refusesAVersionChangeEvenWhenMapIdDidNotChange() throws Exception {
    WorldSync sync = new WorldSync(catalog());
    assertTrue(sync.sync(area("world.demo-field", null)));
    assertTrue(sync.ready());
    assertTrue(sync.sync(area("world.demo-field", "different-version")));
    assertFalse(sync.ready());
    assertFalse(sync.canInteract());
    assertNull(sync.map());
    assertTrue(sync.error().contains("different-version"));
    assertTrue(sync.sync(area("world.demo-field", null)));
    assertTrue(sync.ready());
  }

  @Test
  public void refusesAMapTheClientDoesNotShipAndAnAreaWithoutAnId() throws Exception {
    WorldSync sync = new WorldSync(catalog());
    assertTrue(sync.sync(area("world.nowhere", null)));
    assertFalse(sync.ready());
    assertTrue(sync.error(), sync.error().contains("world.nowhere"));
    assertTrue(sync.sync(area(null, null)));
    assertFalse(sync.ready());
    assertNotNull(sync.error());
  }
}
