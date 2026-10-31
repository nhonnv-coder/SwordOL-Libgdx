package vn.swordol.client.modules.world;

import java.util.Map;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * Presentation port of the world screen, so the flow can be driven without an OpenGL context.
 * Everything here is called on the render thread. {@code showWorld} hands over the live state the
 * view may read while drawing; it is mutated only on that same thread.
 */
public interface WorldView {
  interface Input {
    /** One cell in a WorldState direction; refused silently when the original would refuse it. */
    void step(int direction);

    /** Asks the server for a player the world mentioned but does not know yet. */
    void requestPlayer(int id);

    void leave();
  }

  void setWorldInput(Input input);

  void showMap(
      WorldPackets.MapIdentity identity,
      MapResource resource,
      MapReferences references,
      Map<Integer, byte[]> images);

  void showWorld(WorldState state);

  /** The server changed tiles or collision, so any cached drawing of the grid is stale. */
  void mapChanged();

  void clearWorld();
}
