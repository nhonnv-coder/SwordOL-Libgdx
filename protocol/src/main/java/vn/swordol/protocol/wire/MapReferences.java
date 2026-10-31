package vn.swordol.protocol.wire;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The map-ref resource: one tile definition per entry, as ImagePointer.initCurMapRefDataEx reads
 * it. A sixteen bit record count and image count precede signed sixteen bit record offsets relative
 * to the end of that offset array. Each record holds flags, a signed cell offset and a component
 * count; the component width is declared by the flags: two bytes for an animation reference
 * (0x2000, whose component is another record index), three for an object tile (0x4000) and seven
 * otherwise.
 */
public final class MapReferences {
  public static final int ANIMATION = 0x2000, OBJECT = 0x4000, LOW_DECORATION = 0x0800;
  private final int imageCount;
  private final List<Reference> references;

  private MapReferences(int imageCount, List<Reference> references) {
    this.imageCount = imageCount;
    this.references = Collections.unmodifiableList(references);
  }

  public static MapReferences read(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 4) throw new IOException("Reference table is truncated");
    int count = signedShort(bytes, 0), imageCount = signedShort(bytes, 2);
    int base = 4 + 2 * count;
    if (count < 0 || imageCount < 0 || base > bytes.length)
      throw new IOException("Invalid reference count");
    List<Reference> references = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      int offset = base + signedShort(bytes, 4 + index * 2);
      if (offset < base || offset + 5 > bytes.length)
        throw new IOException("Reference offset outside the buffer");
      int flags = signedShort(bytes, offset) & 0xffff;
      int offsetX = bytes[offset + 2], offsetY = bytes[offset + 3], size = bytes[offset + 4] & 255;
      int width = (flags & ANIMATION) != 0 ? 2 : (flags & OBJECT) != 0 ? 3 : 7;
      if (offset + 5 + size * width > bytes.length)
        throw new IOException("Reference components are truncated");
      List<Piece> pieces = new ArrayList<>(size);
      for (int piece = 0; piece < size; piece++) {
        int at = offset + 5 + piece * width;
        if (width == 2) pieces.add(new Piece(signedShort(bytes, at), 0, 0, 0));
        else if (width == 3)
          pieces.add(new Piece(signedShort(bytes, at), 0, 0, bytes[at + 2] & 255));
        else
          pieces.add(
              new Piece(
                  signedShort(bytes, at),
                  signedShort(bytes, at + 2),
                  signedShort(bytes, at + 4),
                  bytes[at + 6] & 255));
      }
      references.add(new Reference(flags, offsetX, offsetY, pieces));
    }
    return new MapReferences(imageCount, references);
  }

  public int size() {
    return references.size();
  }

  public int imageCount() {
    return imageCount;
  }

  public Reference get(int index) {
    if (index < 0 || index >= references.size())
      throw new IndexOutOfBoundsException("Tile reference " + index + " is not defined");
    return references.get(index);
  }

  public boolean defines(int index) {
    return index >= 0 && index < references.size();
  }

  private static int signedShort(byte[] bytes, int at) throws IOException {
    if (at < 0 || at + 2 > bytes.length)
      throw new IOException("Reference field outside the buffer");
    return (short) (((bytes[at] & 255) << 8) | (bytes[at + 1] & 255));
  }

  public static final class Reference {
    private final int flags, offsetX, offsetY;
    private final List<Piece> pieces;

    Reference(int flags, int offsetX, int offsetY, List<Piece> pieces) {
      this.flags = flags;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.pieces = Collections.unmodifiableList(pieces);
    }

    public int flags() {
      return flags;
    }

    public int offsetX() {
      return offsetX;
    }

    public int offsetY() {
      return offsetY;
    }

    public List<Piece> pieces() {
      return pieces;
    }

    /** Components are record indexes, not image ids. */
    public boolean isAnimation() {
      return (flags & ANIMATION) != 0;
    }

    public boolean isObject() {
      return (flags & OBJECT) != 0;
    }

    public boolean isLowDecoration() {
      return (flags & LOW_DECORATION) != 0;
    }
  }

  public static final class Piece {
    private final int value, offsetX, offsetY, transform;

    Piece(int value, int offsetX, int offsetY, int transform) {
      this.value = value;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.transform = transform;
    }

    /** An image id, or a reference index when its record is an animation. */
    public int value() {
      return value;
    }

    public int offsetX() {
      return offsetX;
    }

    public int offsetY() {
      return offsetY;
    }

    public int transform() {
      return transform;
    }
  }
}
