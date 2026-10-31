package vn.swordol.protocol.wire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Rebuilds the indexed PNG images the server downloads with a map, from the map-images index and
 * the map-png pack, as NewCreatePng does. The palette keeps the client's own RGB565 arithmetic,
 * including its red channel truncation, because the CRC stored beside each image is the oracle:
 * "fixing" the colour maths would break every stored CRC and produce pixels the original game never
 * displayed.
 */
public final class PackedImages {
  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
  private static final byte[] END = {
    0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82
  };
  private static final int RECORD_HEAD = 22;

  private PackedImages() {}

  /** Parses the sixteen bit length header and seven byte records of an image index. */
  public static List<Entry> index(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 2)
      throw new IOException("Image index is shorter than its header");
    int declared = (short) (((bytes[0] & 255) << 8) | (bytes[1] & 255));
    if (declared < 0 || 2 + declared > bytes.length)
      throw new IOException("Image index declares more bytes than it holds");
    if (declared % 7 != 0) throw new IOException("Image index is not a whole number of records");
    List<Entry> table = new ArrayList<>(declared / 7);
    for (int at = 2; at < 2 + declared; at += 7) {
      long id =
          ((long) (bytes[at] & 255) << 24)
              | ((bytes[at + 1] & 255) << 16)
              | ((bytes[at + 2] & 255) << 8)
              | (bytes[at + 3] & 255);
      int pack = bytes[at + 4] & 255;
      int offset = (short) (((bytes[at + 5] & 255) << 8) | (bytes[at + 6] & 255));
      if (offset < 0) throw new IOException("Image record has a negative pack offset");
      if (id > Integer.MAX_VALUE) throw new IOException("Image id outside the client's range");
      table.add(new Entry((int) id, pack, offset));
    }
    return Collections.unmodifiableList(table);
  }

  /**
   * Rebuilds the PNG stored at this offset of a pack, refusing any image whose stored CRC
   * disagrees.
   */
  public static byte[] png(byte[] pack, int offset) throws IOException {
    if (pack == null || offset < 0 || offset + RECORD_HEAD > pack.length)
      throw new IOException("Image record runs past the pack");
    int width = unsigned16(pack, offset), height = unsigned16(pack, offset + 2);
    int depth = pack[offset + 4] & 255, transparentIndex = pack[offset + 5];
    long paletteCrc = unsigned32(pack, offset + 6), transparentCrc = unsigned32(pack, offset + 10);
    long headerCrc = unsigned32(pack, offset + 14);
    int paletteLength = unsigned16(pack, offset + 18), total = unsigned16(pack, offset + 20);
    if (width == 0 || height == 0) throw new IOException("Image record declares an empty image");
    if (depth != 1 && depth != 2 && depth != 4 && depth != 8)
      throw new IOException("Unsupported indexed bit depth");
    int body = offset + RECORD_HEAD;
    if (body + total > pack.length) throw new IOException("Image record runs past the pack");
    if (paletteLength == 0 || paletteLength > total)
      throw new IOException("Image palette does not fit its data");
    int imageLength = total - paletteLength;
    if (imageLength <= 4) throw new IOException("Image record holds no compressed data");
    int colors = paletteLength / 2;
    if (transparentIndex != -1 && (transparentIndex < 0 || transparentIndex >= colors))
      throw new IOException("Transparent index outside the palette");
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(SIGNATURE, 0, SIGNATURE.length);
    byte[] header = new byte[13];
    write32(header, 0, width);
    write32(header, 4, height);
    header[8] = (byte) depth;
    header[9] = 3;
    chunk(png, "IHDR", header, headerCrc);
    chunk(png, "PLTE", palette(pack, body, paletteLength), paletteCrc);
    if (transparentIndex != -1) {
      byte[] alpha = new byte[colors];
      for (int index = 0; index < colors; index++)
        alpha[index] = (byte) (index == transparentIndex ? 0 : 255);
      chunk(png, "tRNS", alpha, transparentCrc);
    }
    int tail = body + total - imageLength;
    byte[] data = java.util.Arrays.copyOfRange(pack, tail, body + total - 4);
    chunk(png, "IDAT", data, unsigned32(pack, body + total - 4));
    png.write(END, 0, END.length);
    return png.toByteArray();
  }

  private static byte[] palette(byte[] pack, int at, int length) throws IOException {
    if (length % 2 != 0)
      throw new IOException("Palette is not a whole number of sixteen bit colours");
    byte[] plte = new byte[length / 2 * 3];
    for (int index = 0; index < length; index += 2) {
      int value = (short) (((pack[at + index] & 255) << 8) | (pack[at + index + 1] & 255));
      plte[index / 2 * 3] = (byte) divide((value >> 11) * 255, 31);
      plte[index / 2 * 3 + 1] = (byte) divide(((value >> 5) & 0x3f) * 255, 63);
      plte[index / 2 * 3 + 2] = (byte) divide((value & 0x1f) * 255, 31);
    }
    return plte;
  }

  /** The client divides with Java semantics, so a negative stored colour truncates toward zero. */
  private static int divide(int value, int divisor) {
    return value / divisor;
  }

  private static void chunk(ByteArrayOutputStream png, String kind, byte[] data, long storedCrc)
      throws IOException {
    byte[] name = kind.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    if (crc.getValue() != storedCrc)
      throw new IOException("Stored CRC disagrees for chunk " + kind);
    byte[] length = new byte[4];
    write32(length, 0, data.length);
    png.write(length, 0, 4);
    png.write(name, 0, name.length);
    png.write(data, 0, data.length);
    byte[] trailer = new byte[4];
    write32(trailer, 0, (int) storedCrc);
    png.write(trailer, 0, 4);
  }

  private static void write32(byte[] bytes, int at, int value) {
    bytes[at] = (byte) (value >>> 24);
    bytes[at + 1] = (byte) (value >>> 16);
    bytes[at + 2] = (byte) (value >>> 8);
    bytes[at + 3] = (byte) value;
  }

  private static int unsigned16(byte[] bytes, int at) {
    return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255);
  }

  private static long unsigned32(byte[] bytes, int at) {
    return (((long) (bytes[at] & 255) << 24)
            | ((bytes[at + 1] & 255) << 16)
            | ((bytes[at + 2] & 255) << 8)
            | (bytes[at + 3] & 255))
        & 0xffffffffL;
  }

  public static final class Entry {
    private final int imageId, packIndex, offset;

    Entry(int imageId, int packIndex, int offset) {
      this.imageId = imageId;
      this.packIndex = packIndex;
      this.offset = offset;
    }

    public int imageId() {
      return imageId;
    }

    public int packIndex() {
      return packIndex;
    }

    public int offset() {
      return offset;
    }
  }
}
