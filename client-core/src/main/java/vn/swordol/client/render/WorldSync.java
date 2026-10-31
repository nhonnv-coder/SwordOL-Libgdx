package vn.swordol.client.render;

import com.google.gson.JsonObject;
import java.io.IOException;
import vn.swordol.assets.runtime.MapData;

/**
 * Keeps the client's drawn world in step with the map the server says it is simulating.
 *
 * <p>When the two disagree the refusal is held until a map the client can trust arrives: the
 * message survives later server status text, and {@link #canInteract()} stays false. Drawing a
 * plain grid and accepting moves would tell the player there are no walls where the server has
 * them.
 */
public final class WorldSync {
  private final MapCatalog catalog;
  private MapData map;
  private String mapId;
  private String error;

  public WorldSync(MapCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Applies the {@code map} object of a server state.
   *
   * @return true when the drawn world or the refusal changed
   */
  public boolean sync(JsonObject area) {
    String id =
        area != null && area.has("id") && area.get("id").isJsonPrimitive()
            ? area.get("id").getAsString()
            : null;
    String version =
        area != null && area.has("version") && area.get("version").isJsonPrimitive()
            ? area.get("version").getAsString()
            : null;
    if (id == null) {
      return fail("The server did not name the map it is running.");
    }
    if (id.equals(mapId)
        && map != null
        && error == null
        && (version == null || version.equals(map.version()))) {
      return false;
    }
    try {
      MapData loaded = catalog.require(id, version);
      map = loaded;
      mapId = id;
      error = null;
      return true;
    } catch (IOException refused) {
      return fail(refused.getMessage());
    }
  }

  private boolean fail(String message) {
    boolean changed = error == null || !error.equals(message) || map != null;
    map = null;
    mapId = null;
    error = message;
    return changed;
  }

  /** The map to draw, or null while the client and the server disagree. */
  public MapData map() {
    return map;
  }

  /** Why the world is not being drawn, or null when it is. */
  public String error() {
    return error;
  }

  public boolean ready() {
    return map != null && error == null;
  }

  /** False while the world is refused: moves would be aimed at a world the player cannot see. */
  public boolean canInteract() {
    return ready();
  }

  /** The refusal if there is one, otherwise the server's own status text. */
  public String status(String serverStatus) {
    return error != null ? error : serverStatus;
  }
}
