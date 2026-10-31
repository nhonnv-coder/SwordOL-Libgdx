package vn.swordol.client.modules.world;

import java.util.Map;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * World packets the session has already decoded, delivered on the dispatcher the session was given
 * (the render thread on desktop). Every value handed over is an immutable parse result or a copy,
 * so the inbound thread keeps no shared mutable state; the receiver applies them to its own
 * WorldState.
 */
public interface WorldListener {
  /**
   * A map whose resources arrived complete. {@code images} holds the PNGs this map downloaded,
   * keyed by legacy image id, and is empty for a map that only uses the original client's images.
   */
  void mapLoaded(
      WorldPackets.MapIdentity identity,
      MapResource resource,
      MapReferences references,
      Map<Integer, byte[]> images);

  void mapUpdate(WorldPackets.MapUpdate update);

  void user(WorldPackets.UserInfo info);

  void otherPlayer(WorldPackets.PlayerInfo info);

  void walk(WorldPackets.Walk walk);
}
