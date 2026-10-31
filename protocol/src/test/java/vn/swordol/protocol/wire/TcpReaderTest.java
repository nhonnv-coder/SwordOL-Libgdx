package vn.swordol.protocol.wire;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public class TcpReaderTest {
  @Test
  public void preservesSignedTypesUtf8AndInboundTagFiveWidth() throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(body);
    out.writeShort(1110);
    out.writeByte(1);
    out.writeShort(-2);
    out.writeByte(2);
    out.writeByte(-128);
    out.writeByte(3);
    out.writeShort(32767);
    out.writeByte(4);
    out.writeInt(1101400);
    out.writeByte(5);
    out.writeInt(-123456);
    byte[] name = "Trung nguyên".getBytes("UTF-8");
    out.writeByte(6);
    out.writeShort(name.length);
    out.write(name);
    out.writeByte(7);
    out.writeShort(3);
    out.write(new byte[] {0, -1, 9});
    Packet packet = TcpReader.read(new ByteArrayInputStream(frame(body.toByteArray())));
    assertEquals(1110, packet.opcode());
    assertEquals(7, packet.fields().size());
    assertEquals(-2, packet.fields().get(0).number());
    assertEquals(-128, packet.fields().get(1).number());
    assertEquals(32767, packet.fields().get(2).number());
    assertEquals(1101400, packet.fields().get(3).number());
    assertEquals(5, packet.fields().get(4).tag());
    assertEquals(-123456, packet.fields().get(4).number());
    assertEquals("Trung nguyên", packet.fields().get(5).text());
    byte[] binary = packet.fields().get(6).bytes();
    binary[0] = 99;
    assertArrayEquals(new byte[] {0, -1, 9}, packet.fields().get(6).bytes());
    try {
      packet.fields().clear();
      fail("mutable fields");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void readsExactlyOneFrameAndDistinguishesCleanEofFromTruncation() throws Exception {
    byte[] first = frame(new byte[] {4, 86}); // opcode1110, no fields
    byte[] second = frame(new byte[] {3, -19}); // opcode1005
    ByteArrayOutputStream joined = new ByteArrayOutputStream();
    joined.write(first);
    joined.write(second);
    ByteArrayInputStream input = new ByteArrayInputStream(joined.toByteArray());
    assertEquals(1110, TcpReader.read(input).opcode());
    assertEquals(1005, TcpReader.read(input).opcode());
    assertNull(TcpReader.read(input));
    for (int size = 1; size < first.length; size++) reject(Arrays.copyOf(first, size));
  }

  @Test
  public void refusesInvalidLengthsTagsAndTruncatedTypedValues() throws Exception {
    reject(new byte[] {0, 0});
    reject(new byte[] {0, 3, 0});
    reject(new byte[] {(byte) 128, 0});
    reject(frame(new byte[] {0, 1, 8}));
    reject(frame(new byte[] {0, 1, 4, 0, 0, 0}));
    reject(frame(new byte[] {0, 1, 6, 0, 3, 65}));
    reject(frame(new byte[] {0, 1, 7, -1, -1}));
  }

  private static byte[] frame(byte[] body) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(result);
    output.writeShort(body.length + 2);
    output.write(body);
    return result.toByteArray();
  }

  private static void reject(byte[] bytes) throws Exception {
    try {
      TcpReader.read(new ByteArrayInputStream(bytes));
      fail("Expected malformed response to fail");
    } catch (IOException expected) {
    }
  }
}
