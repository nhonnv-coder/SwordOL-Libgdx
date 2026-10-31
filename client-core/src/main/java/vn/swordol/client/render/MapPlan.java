package vn.swordol.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import vn.swordol.protocol.wire.MapReferences;

/**
 * Turns a legacy map and its tile reference table into the ordered image draws the original paints.
 * MapEx paints the immediate0x4000 object tiles, then its0x0800 low-decoration buffer queue, then
 * the remaining static entities, and Entity.compareMapPos sorts each queue by row then column.
 * Positions are left in the original's downward pixel space, exactly as the verified Python preview
 * produces them; flipping to the GL coordinate system belongs to the renderer that consumes this.
 * Content errors (an undefined tile, a missing image, a reference cycle) are reported, not thrown.
 */
public final class MapPlan {
  private static final int MAX_REFERENCE_DEPTH = 32;

  public interface Images {
    /** Width in pixels, or a negative value when this image is not available. */
    int width(int imageId);

    int height(int imageId);
  }

  private MapPlan() {}

  public static Plan build(WorldMap map, MapReferences references, Images images) {
    Objects.requireNonNull(map, "map");
    Objects.requireNonNull(references, "references");
    Objects.requireNonNull(images, "images");
    List<Draw> draws = new ArrayList<>();
    Set<Integer> missing = new TreeSet<>();
    Set<String> warnings = new TreeSet<>();
    List<List<int[]>> queues = new ArrayList<>();
    for (int layer = 0; layer < 3; layer++) queues.add(new ArrayList<int[]>());
    for (int y = 0; y < map.rows(); y++) {
      for (int x = 0; x < map.columns(); x++) {
        int index = map.tile(x, y);
        if (index == WorldMap.EMPTY_TILE) continue;
        if (!references.defines(index)) {
          warnings.add("Tile không có định nghĩa: " + index);
          continue;
        }
        int flags = references.get(index).flags();
        boolean object =
            (flags & MapReferences.OBJECT) != 0 && (flags & MapReferences.ANIMATION) == 0;
        int px = x * map.cellWidth() + (object ? 0 : map.cellWidth() / 2);
        int py = y * map.cellHeight() + (object ? 0 : map.cellHeight());
        int layer = object ? 0 : (flags & MapReferences.LOW_DECORATION) != 0 ? 1 : 2;
        queues.get(layer).add(new int[] {index, px, py, map.rotated(x, y) ? 1 : 0});
      }
    }
    for (List<int[]> queue : queues)
      for (int[] cell : queue)
        emit(
            references,
            images,
            draws,
            missing,
            warnings,
            cell[0],
            cell[1],
            cell[2],
            cell[3],
            new ArrayList<Integer>());
    return new Plan(draws, new ArrayList<>(missing), new ArrayList<>(warnings));
  }

  private static void emit(
      MapReferences references,
      Images images,
      List<Draw> draws,
      Set<Integer> missing,
      Set<String> warnings,
      int index,
      int x,
      int y,
      int turn,
      List<Integer> trail) {
    if (trail.contains(index) || trail.size() > MAX_REFERENCE_DEPTH) {
      warnings.add("Chu kỳ tham chiếu animation: " + index);
      return;
    }
    if (!references.defines(index)) {
      warnings.add("Tile không có định nghĩa: " + index);
      return;
    }
    MapReferences.Reference reference = references.get(index);
    if (reference.isAnimation()) {
      // The original shows one animation frame at a time; a static plan keeps the first.
      if (!reference.pieces().isEmpty()) {
        List<Integer> walked = new ArrayList<>(trail);
        walked.add(index);
        emit(
            references,
            images,
            draws,
            missing,
            warnings,
            reference.pieces().get(0).value(),
            x,
            y,
            turn,
            walked);
      }
      return;
    }
    for (MapReferences.Piece piece : reference.pieces()) {
      int width = images.width(piece.value()), height = images.height(piece.value());
      if (width < 0 || height < 0) {
        missing.add(piece.value());
        continue;
      }
      int offsetX = piece.offsetX(), offsetY = piece.offsetY();
      int transform = piece.transform() ^ turn;
      if ((reference.flags() & MapReferences.OBJECT) == 0) {
        if ((transform & 32) != 0) offsetY = -offsetY - height;
        if ((transform & 1) != 0) offsetX = -offsetX - width;
      }
      draws.add(new Draw(piece.value(), x + offsetX, y + offsetY, flipCode(transform)));
    }
  }

  /**
   * The stored bit1/bit32 flips map onto the renderer's flip-vertical/flip-horizontal/diagonal
   * code.
   */
  private static int flipCode(int transform) {
    if (transform == 1) return 2;
    if (transform == 32) return 1;
    if (transform == 33) return 3;
    return transform;
  }

  public static final class Plan {
    private final List<Draw> draws;
    private final List<Integer> missingImages;
    private final List<String> warnings;

    Plan(List<Draw> draws, List<Integer> missingImages, List<String> warnings) {
      this.draws = Collections.unmodifiableList(draws);
      this.missingImages = Collections.unmodifiableList(missingImages);
      this.warnings = Collections.unmodifiableList(warnings);
    }

    public List<Draw> draws() {
      return draws;
    }

    public List<Integer> missingImages() {
      return missingImages;
    }

    public List<String> warnings() {
      return warnings;
    }
  }

  /**
   * One image blit: top-left corner in the original downward pixel space plus its transform code.
   */
  public static final class Draw {
    public static final int FLIP_VERTICAL = 1, FLIP_HORIZONTAL = 2, DIAGONAL = 4;
    private final int imageId, x, y, transform;

    Draw(int imageId, int x, int y, int transform) {
      this.imageId = imageId;
      this.x = x;
      this.y = y;
      this.transform = transform;
    }

    public int imageId() {
      return imageId;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int transform() {
      return transform;
    }

    public boolean flipVertical() {
      return (transform & FLIP_VERTICAL) != 0;
    }

    public boolean flipHorizontal() {
      return (transform & FLIP_HORIZONTAL) != 0;
    }

    public boolean diagonal() {
      return (transform & DIAGONAL) != 0;
    }
  }
}
