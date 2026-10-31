package vn.swordol.protocol;

import static org.junit.Assert.*;

import java.io.*;
import org.junit.Test;

public class FrameCodecTest {
  @Test
  public void roundTripUtf8() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FrameCodec.write(out, "{\"name\":\"Kiếm\"}");
    assertEquals(
        "{\"name\":\"Kiếm\"}", FrameCodec.read(new ByteArrayInputStream(out.toByteArray())));
  }

  @Test(expected = IOException.class)
  public void oversizedRejected() throws Exception {
    FrameCodec.read(new ByteArrayInputStream(new byte[16385]));
  }

  @Test(expected = IOException.class)
  public void invalidUtf8Rejected() throws Exception {
    FrameCodec.read(new ByteArrayInputStream(new byte[] {(byte) 0xff, 10}));
  }

  @Test(expected = IOException.class)
  public void unterminatedRejected() throws Exception {
    FrameCodec.read(new ByteArrayInputStream(new byte[] {123}));
  }

  @Test
  public void exactLimitAccepted() throws Exception {
    byte[] bytes = new byte[16385];
    java.util.Arrays.fill(bytes, (byte) 97);
    bytes[16384] = 10;
    assertEquals(16384, FrameCodec.read(new ByteArrayInputStream(bytes)).length());
  }

  @Test(expected = IllegalArgumentException.class)
  public void malformedRequestRejected() {
    FrameCodec.decodeRequest("{}");
  }
}
