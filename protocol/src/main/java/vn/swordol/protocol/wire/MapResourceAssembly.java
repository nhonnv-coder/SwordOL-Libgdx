package vn.swordol.protocol.wire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Collects the1407 map resource transfers of one map, exactly as CmdProcessor.processMapDataCmd
 * drives MapEx: odd actions append a chunk, the matching even action completes it, and the image
 * index (actions13 and14) is replaced per archive instead of appended, as updateArrayBoosImages
 * does. Packed image bytes stay compressed as the client keeps them; the other kinds are single
 * member archives the caller inflates. Bounded and fail closed; never mutates its input.
 */
public final class MapResourceAssembly {
  public enum Kind {
    MAP_PNG,
    MAP_REF,
    MAP_IMAGES,
    MAP_DATA
  }

  public static final int MAX_RESOURCE = 16 * 1024 * 1024;
  private final Map<Kind, ByteArrayOutputStream> pending = new EnumMap<>(Kind.class);
  private final Map<Kind, byte[]> completed = new EnumMap<>(Kind.class);

  /**
   * Accepts one inbound1407 packet; resource actions of other kinds are ignored, malformed ones
   * rejected.
   */
  public void accept(Packet packet) throws IOException {
    if (packet == null || packet.opcode() != 1407)
      throw new IOException("Not a map resource packet");
    int action = LoginPackets.number(packet, 0);
    Kind kind = kindOf(action);
    if (kind == null) return;
    if (packet.fields().size() != 3) throw new IOException("Invalid resource packet fields");
    byte[] chunk = packet.fields().get(2).bytes();
    if (LoginPackets.number(packet, 1) != chunk.length)
      throw new IOException("Resource chunk length mismatch");
    if (action == 13 || action == 14) {
      inflate(chunk);
      completed.put(kind, chunk);
      return;
    }
    ByteArrayOutputStream buffer = pending.get(kind);
    if (buffer == null) pending.put(kind, buffer = new ByteArrayOutputStream());
    if (buffer.size() + chunk.length > MAX_RESOURCE)
      throw new IOException("Oversized map resource");
    buffer.write(chunk, 0, chunk.length);
    if (action % 2 == 0) completed.put(kind, pending.remove(kind).toByteArray());
  }

  /** The completed transfer for this kind, or null while it is missing or still arriving. */
  public byte[] completed(Kind kind) {
    byte[] bytes = completed.get(kind);
    return bytes == null ? null : bytes.clone();
  }

  public boolean hasPending() {
    return !pending.isEmpty();
  }

  public void reset() {
    pending.clear();
    completed.clear();
  }

  /**
   * Inflates the single stored or deflated member of a resource archive, checking its size and CRC.
   */
  public static byte[] inflate(byte[] zip) throws IOException {
    if (zip == null
        || zip.length < 30
        || zip[0] != 'P'
        || zip[1] != 'K'
        || zip[2] != 3
        || zip[3] != 4) throw new IOException("Missing ZIP local header");
    int flags = little16(zip, 6), method = little16(zip, 8);
    long storedCrc = little32(zip, 14), packed = little32(zip, 18), size = little32(zip, 22);
    int start = 30 + little16(zip, 26) + little16(zip, 28);
    if ((flags & 9) != 0
        || size > MAX_RESOURCE
        || packed > MAX_RESOURCE
        || start + packed > zip.length)
      throw new IOException("Unsupported or truncated ZIP resource");
    byte[] raw;
    if (method == 0) {
      raw = java.util.Arrays.copyOfRange(zip, start, start + (int) packed);
    } else if (method == 8) {
      Inflater inflater = new Inflater(true);
      try {
        inflater.setInput(zip, start, (int) packed);
        raw = new byte[(int) size];
        int produced = 0;
        while (produced < raw.length && !inflater.finished()) {
          int step = inflater.inflate(raw, produced, raw.length - produced);
          if (step == 0 && (inflater.needsInput() || inflater.needsDictionary()))
            throw new IOException("Truncated ZIP stream");
          produced += step;
        }
        if (produced != raw.length || inflater.inflate(new byte[1]) != 0)
          throw new IOException("Invalid or oversized ZIP stream");
      } catch (DataFormatException ex) {
        throw new IOException("Invalid ZIP compression", ex);
      } finally {
        inflater.end();
      }
    } else {
      throw new IOException("Unsupported ZIP compression method");
    }
    CRC32 crc = new CRC32();
    crc.update(raw);
    if (raw.length != size || crc.getValue() != storedCrc)
      throw new IOException("ZIP size/CRC mismatch");
    return raw;
  }

  private static Kind kindOf(int action) {
    switch (action) {
      case 9:
      case 10:
        return Kind.MAP_PNG;
      case 11:
      case 12:
        return Kind.MAP_REF;
      case 13:
      case 14:
        return Kind.MAP_IMAGES;
      case 15:
      case 16:
        return Kind.MAP_DATA;
      default:
        return null;
    }
  }

  private static int little16(byte[] bytes, int at) {
    return (bytes[at] & 255) | ((bytes[at + 1] & 255) << 8);
  }

  private static long little32(byte[] bytes, int at) {
    return (little16(bytes, at) | ((long) little16(bytes, at + 2) << 16)) & 0xffffffffL;
  }
}
