package vn.swordol.protocol.wire;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Original Packet's typed writer. Intentionally no ambiguous tag5/generic-long writer. */
public final class WireWriter {
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final DataOutputStream out = new DataOutputStream(bytes);

  public WireWriter(int opcode) {
    if (opcode < Short.MIN_VALUE || opcode > Short.MAX_VALUE)
      throw new IllegalArgumentException("opcode");
    write(() -> out.writeShort(opcode));
  }

  public WireWriter integer(int value) {
    write(() -> {
      out.writeByte(4);
      out.writeInt(value);
    });
    return this;
  }

  public WireWriter signedByte(int value) {
    if (value < -128 || value > 127) throw new IllegalArgumentException("byte range");
    write(() -> {
      out.writeByte(2);
      out.writeByte(value);
    });
    return this;
  }

  public WireWriter shortValue(int value) {
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE)
      throw new IllegalArgumentException("short range");
    write(() -> {
      out.writeByte(3);
      out.writeShort(value);
    });
    return this;
  }

  public WireWriter text(String value) {
    byte[] data = java.util.Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
    if (data.length > 32760) throw new IllegalArgumentException("text too large");
    write(() -> {
      out.writeByte(6);
      out.writeShort(data.length);
      out.write(data);
    });
    return this;
  }

  /**
   * Tag7 binary field. The original client never writes one; this mirrors the tag TcpReader reads
   * so the local gateway can send the resource transfers the real server sends.
   */
  public WireWriter binary(byte[] value) {
    byte[] data = java.util.Objects.requireNonNull(value).clone();
    if (data.length > 32760) throw new IllegalArgumentException("binary field too large");
    write(() -> {
      out.writeByte(7);
      out.writeShort(data.length);
      out.write(data);
    });
    return this;
  }

  public byte[] build() {
    return bytes.toByteArray();
  }

  private interface Io {
    void run() throws IOException;
  }

  private void write(Io action) {
    try {
      action.run();
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
    if (bytes.size() > 32765) throw new IllegalArgumentException("packet too large");
  }
}
