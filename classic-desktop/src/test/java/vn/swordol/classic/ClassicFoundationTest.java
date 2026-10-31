package vn.swordol.classic;

import static org.junit.Assert.*;

import com.badlogic.gdx.Input.Keys;
import java.awt.event.KeyEvent;
import java.nio.ByteBuffer;
import org.junit.Test;

public class ClassicFoundationTest {
  @Test
  public void convertsChannelsAndDoesNotRetainMutableProducerPixels() {
    FrameMailbox frames = new FrameMailbox(2, 1);
    int[] producer = {0x80402010, 0xff112233};
    frames.publish(producer);
    producer[0] = 0;
    ByteBuffer pixels = ByteBuffer.allocate(8);
    assertEquals(1, frames.copyRgba(pixels));
    assertArrayEquals(
        new byte[] {0x40, 0x20, 0x10, (byte) 0x80, 0x11, 0x22, 0x33, (byte) 0xff}, pixels.array());
    assertEquals(0, pixels.position());
    assertEquals(8, pixels.limit());
  }

  @Test
  public void viewportMapsTopLeftCoordinatesAndRejectsBars() {
    ViewportBounds bounds = ViewportBounds.fit(1000, 800, 640, 480, 40, false);
    assertArrayEquals(new int[] {0, 0}, bounds.point(0, 5, false));
    assertArrayEquals(new int[] {639, 479}, bounds.point(999, 754, false));
    assertNull(bounds.point(500, 799, false));
    assertNull(bounds.point(-1, 200, false));
    assertArrayEquals(new int[] {639, 479}, bounds.point(2000, 900, true));
    ViewportBounds integer = ViewportBounds.fit(1000, 800, 640, 480, 40, true);
    assertEquals(1f, integer.scale, 0);
  }

  @Test
  public void mapsDesktopNavigationWithoutTreatingFunctionKeysAsText() {
    assertEquals(KeyEvent.VK_UP, DesktopKeys.awtCode(Keys.UP));
    assertEquals(KeyEvent.VK_F1, DesktopKeys.awtCode(Keys.F1));
    assertEquals(KeyEvent.VK_BACK_SPACE, DesktopKeys.awtCode(Keys.BACKSPACE));
    assertEquals(KeyEvent.VK_NUMPAD5, DesktopKeys.awtCode(Keys.NUMPAD_5));
    assertEquals(KeyEvent.VK_UNDEFINED, DesktopKeys.awtCode(Keys.A));
  }
}
