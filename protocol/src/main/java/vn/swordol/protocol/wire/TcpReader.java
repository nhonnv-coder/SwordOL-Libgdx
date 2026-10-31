package vn.swordol.protocol.wire;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded inbound TCP decoder from TCPHandler.run / CNetPacketData / PacketProcess. Not an outbound
 * FEFE/XOR/CRC envelope decoder and not an HTTP batch decoder. Caller owns the stream. Unknown
 * field tags fail closed; unknown opcodes remain raw packets.
 */
public final class TcpReader {
  private TcpReader() {}

  /** Returns null only at a clean frame boundary EOF. Never closes the input. */
  public static Packet read(InputStream input) throws IOException {
    int high = input.read();
    if (high == -1) return null;
    int low = input.read();
    if (low == -1) throw new EOFException("Truncated response length");
    int length = (high << 8) | low;
    if (length < 4 || length > 32767) throw new IOException("Invalid response length");
    byte[] payload = new byte[length - 2];
    new DataInputStream(input).readFully(payload);
    DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
    short opcode = data.readShort();
    List<Packet.Field> fields = new ArrayList<>();
    while (data.available() > 0) {
      int tag = data.readUnsignedByte();
      Object value;
      switch (tag) {
        case 1:
        case 3:
          value = (int) data.readShort();
          break;
        case 2:
          value = (int) data.readByte();
          break;
        case 4:
        case 5:
          value = data.readInt();
          break;
        case 6:
        case 7:
          int size = data.readUnsignedShort();
          if (size > data.available()) throw new EOFException("Truncated response field");
          byte[] bytes = new byte[size];
          data.readFully(bytes);
          value = tag == 6 ? new String(bytes, StandardCharsets.UTF_8) : bytes;
          break;
        default:
          throw new IOException("Unsupported response field tag: " + tag);
      }
      fields.add(new Packet.Field(tag, value));
    }
    return new Packet(opcode, fields);
  }
}
