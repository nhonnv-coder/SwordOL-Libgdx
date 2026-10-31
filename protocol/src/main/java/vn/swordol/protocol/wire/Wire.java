package vn.swordol.protocol.wire;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Direction-specific legacy framing. Pure byte processing; owns no socket or credentials. */
public final class Wire {
  private Wire() {}

  public static byte[] tcpRequest(byte[] logical, int key) throws IOException {
    checkPacket(logical);
    ByteArrayOutputStream covered = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(covered);
    data.writeByte(key);
    data.writeShort(logical.length + 2);
    for (byte b : logical) data.writeByte(b ^ key);
    return envelope(covered.toByteArray());
  }

  public static byte[] readTcpRequest(InputStream input) throws IOException {
    DataInputStream in = new DataInputStream(input);
    int first = in.read();
    if (first == -1) return null;
    if (first != 254 || in.readUnsignedByte() != 254) throw new IOException("Invalid TCP marker");
    int key = in.readUnsignedByte();
    int length = in.readUnsignedShort();
    if (length < 4 || length > 32767) throw new IOException("Invalid TCP request length");
    byte[] encoded = new byte[length - 2];
    in.readFully(encoded);
    ByteArrayOutputStream covered = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(covered);
    data.writeByte(key);
    data.writeShort(length);
    data.write(encoded);
    verifyTail(in, covered.toByteArray());
    for (int i = 0; i < encoded.length; i++) encoded[i] ^= (byte) key;
    return encoded;
  }

  public static byte[] tcpResponse(byte[] logical) throws IOException {
    checkPacket(logical);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeShort(logical.length + 2);
    out.write(logical);
    return bytes.toByteArray();
  }

  public static Packet logical(byte[] bytes) throws IOException {
    return TcpReader.read(new ByteArrayInputStream(tcpResponse(bytes)));
  }

  public static byte[] httpRequest(byte[] logical, int session, int random, int key)
      throws IOException {
    checkPacket(logical);
    // Original readySend only batches queued packets while combined packet bytes <1000.
    if (logical.length + 2 >= 1000)
      throw new IOException("Account packet exceeds original batch limit");
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(body);
    data.writeInt(session);
    data.writeInt(random);
    data.writeByte(1);
    data.write(tcpResponse(logical));
    byte[] encoded = body.toByteArray();
    for (int i = 0; i < encoded.length; i++) encoded[i] ^= (byte) key;
    ByteArrayOutputStream covered = new ByteArrayOutputStream();
    data = new DataOutputStream(covered);
    data.writeByte(key);
    data.writeShort(encoded.length);
    data.write(encoded);
    return envelope(covered.toByteArray());
  }

  public static Batch readHttpRequest(InputStream input) throws IOException {
    DataInputStream in = new DataInputStream(input);
    if (in.readUnsignedShort() != 0xfefe) throw new IOException("Invalid HTTP request marker");
    int key = in.readUnsignedByte(), length = in.readUnsignedShort();
    if (length < 13 || length > 1008) throw new IOException("Invalid account request size");
    byte[] encoded = new byte[length];
    in.readFully(encoded);
    ByteArrayOutputStream covered = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(covered);
    out.writeByte(key);
    out.writeShort(length);
    out.write(encoded);
    verifyTail(in, covered.toByteArray());
    if (in.read() != -1) throw new IOException("Trailing account request data");
    for (int i = 0; i < encoded.length; i++) encoded[i] ^= (byte) key;
    return readBatch(new DataInputStream(new ByteArrayInputStream(encoded)));
  }

  public static byte[] httpResponse(List<byte[]> logical, int session, int random)
      throws IOException {
    if (logical.size() > 127) throw new IOException("Too many packets");
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(body);
    out.writeInt(session);
    out.writeInt(random);
    out.writeByte(logical.size());
    for (byte[] packet : logical) out.write(tcpResponse(packet));
    if (body.size() > 32767) throw new IOException("Account response too large");
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    out = new DataOutputStream(result);
    out.writeShort(body.size());
    body.writeTo(out);
    return result.toByteArray();
  }

  public static Batch readHttpResponse(InputStream input) throws IOException {
    DataInputStream in = new DataInputStream(input);
    int length = in.readUnsignedShort();
    if (length < 9 || length > 32767) throw new IOException("Invalid account response size");
    byte[] body = new byte[length];
    in.readFully(body);
    if (in.read() != -1) throw new IOException("Trailing account response data");
    return readBatch(new DataInputStream(new ByteArrayInputStream(body)));
  }

  private static Batch readBatch(DataInputStream in) throws IOException {
    int session = in.readInt(), random = in.readInt(), count = in.readUnsignedByte();
    if (count > 127) throw new IOException("Invalid batch count");
    List<Packet> packets = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Packet p = TcpReader.read(in);
      if (p == null) throw new EOFException("Missing batch packet");
      packets.add(p);
    }
    if (in.read() != -1) throw new IOException("Trailing batch data");
    return new Batch(session, random, packets);
  }

  private static byte[] envelope(byte[] covered) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeShort(0xfefe);
    out.write(covered);
    out.writeInt(crc(covered));
    out.writeShort(0xaaaa);
    return bytes.toByteArray();
  }

  private static int crc(byte[] bytes) {
    CRC32 crc = new CRC32();
    crc.update(bytes);
    return (int) ~crc.getValue();
  }

  private static void verifyTail(DataInputStream in, byte[] covered) throws IOException {
    if (in.readInt() != crc(covered)) throw new IOException("Invalid legacy CRC");
    if (in.readUnsignedShort() != 0xaaaa) throw new IOException("Invalid legacy tail");
  }

  private static void checkPacket(byte[] logical) throws IOException {
    if (logical == null || logical.length < 2 || logical.length > 32765)
      throw new IOException("Invalid logical packet size");
  }

  public static final class Batch {
    private final int session, random;
    private final List<Packet> packets;

    Batch(int session, int random, List<Packet> packets) {
      this.session = session;
      this.random = random;
      this.packets = Collections.unmodifiableList(new ArrayList<>(packets));
    }

    public int session() {
      return session;
    }

    public int random() {
      return random;
    }

    public List<Packet> packets() {
      return packets;
    }
  }
}
