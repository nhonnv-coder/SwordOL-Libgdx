package vn.swordol.classic;

import java.nio.ByteBuffer;

/** One bounded framebuffer, shared between legacy paint callbacks and the GL render thread. */
public final class FrameMailbox {
  public final int width;
  public final int height;
  private final int[] pixels;
  private long sequence;

  public FrameMailbox(int width, int height) {
    if (width <= 0 || height <= 0 || (long) width * height > 4096L * 4096) {
      throw new IllegalArgumentException("Invalid framebuffer size");
    }
    this.width = width;
    this.height = height;
    pixels = new int[width * height];
  }

  public synchronized void publish(int[] argb) {
    if (argb.length != pixels.length) {
      throw new IllegalArgumentException("Unexpected pixel count");
    }
    System.arraycopy(argb, 0, pixels, 0, pixels.length);
    sequence++;
  }

  public synchronized long sequence() {
    return sequence;
  }

  public synchronized long copyRgba(ByteBuffer target) {
    if (target.capacity() < pixels.length * 4L) {
      throw new IllegalArgumentException("Pixel buffer too small");
    }
    target.clear();
    for (int argb : pixels) {
      target.put((byte) (argb >>> 16));
      target.put((byte) (argb >>> 8));
      target.put((byte) argb);
      target.put((byte) (argb >>> 24));
    }
    target.flip();
    return sequence;
  }
}
