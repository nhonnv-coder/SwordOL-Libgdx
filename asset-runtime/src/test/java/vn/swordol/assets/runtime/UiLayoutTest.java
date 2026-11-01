package vn.swordol.assets.runtime;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * The decoded layout of one original window, as tools/analysis/decode_ui.py writes it. The reader
 * repeats the producer's rules rather than trusting them, the way the other bundle readers do.
 */
public class UiLayoutTest {
  /** Window 114, the registration screen, with the controls it actually ships. */
  private static final String REGISTER =
      "{\"window_id\":114,\"screen_id\":114000,\"screen_class\":1,"
          + "\"x\":0,\"y\":0,\"width\":640,\"height\":360,\"focus_id\":7,"
          + "\"soft_left_id\":-1,\"soft_right_id\":-1,\"controls\":["
          + "{\"id\":114001,\"type\":\"button\",\"x\":170,\"y\":54,\"width\":50,\"height\":40,"
          + "\"text\":\"Tài khoản:\",\"text_color\":16777215,\"image_id\":0,\"image_index\":0},"
          + "{\"id\":114007,\"type\":\"edit\",\"x\":226,\"y\":54,\"width\":240,\"height\":40,"
          + "\"text\":\"\",\"text_color\":16777215,\"char_limit\":13,\"input_flags\":8},"
          + "{\"id\":114005,\"type\":\"text\",\"x\":170,\"y\":196,\"width\":290,\"height\":30,"
          + "\"text\":\"Chú ý\",\"text_color\":0}]}";

  @Test
  public void readsTheWindowAndItsControlsInOrder() throws Exception {
    UiLayout layout = UiLayout.read(REGISTER.getBytes(StandardCharsets.UTF_8));
    assertEquals(114, layout.windowId());
    assertEquals(640, layout.width());
    assertEquals(360, layout.height());
    assertEquals(3, layout.controls().size());

    UiLayout.Control label = layout.controls().get(0);
    assertEquals(114001, label.id());
    assertEquals(UiLayout.Kind.BUTTON, label.kind());
    assertEquals(170, label.x());
    assertEquals(54, label.y());
    assertEquals(50, label.width());
    assertEquals(40, label.height());
    assertEquals("Tài khoản:", label.text());
    assertEquals(0, label.imageId());
  }

  @Test
  public void findsAControlByTheIdTheOriginalUses() throws Exception {
    UiLayout layout = UiLayout.read(REGISTER.getBytes(StandardCharsets.UTF_8));
    UiLayout.Control box = layout.control(114007);
    assertNotNull(box);
    assertEquals(UiLayout.Kind.EDIT, box.kind());
    assertEquals(13, box.charLimit());
    assertEquals(226, box.x());
    assertNull("an id this window does not hold", layout.control(999999));
  }

  @Test
  public void keepsTheTextColourAsTheLayoutStoresIt() throws Exception {
    UiLayout layout = UiLayout.read(REGISTER.getBytes(StandardCharsets.UTF_8));
    assertEquals(0xFFFFFF, layout.control(114001).textColor());
    assertEquals(0, layout.control(114005).textColor());
  }

  @Test
  public void refusesADocumentThatIsNotOneWindow() {
    assertRejected("[]");
    assertRejected("{\"window_id\":114}");
    // A control of a class this build cannot place must not be silently dropped.
    assertRejected(
        "{\"window_id\":1,\"width\":640,\"height\":360,\"controls\":"
            + "[{\"id\":1,\"type\":\"hologram\",\"x\":0,\"y\":0,\"width\":1,\"height\":1}]}");
    // A box far larger than any surface the client draws on.
    assertRejected(
        "{\"window_id\":1,\"width\":640,\"height\":360,\"controls\":"
            + "[{\"id\":1,\"type\":\"text\",\"x\":0,\"y\":0,\"width\":99999,\"height\":1,"
            + "\"text\":\"\",\"text_color\":0}]}");
  }

  @Test
  public void keepsAControlTheWindowDrawsPastItsOwnEdge() throws Exception {
    // Window 10 puts buttons 10009 and 10010 at y=335 with height 31 on a 360 tall window. The
    // original clips them; refusing the file here would lose the other 41 controls with them.
    UiLayout layout =
        UiLayout.read(
            ("{\"window_id\":10,\"width\":640,\"height\":360,\"controls\":"
                    + "[{\"id\":10009,\"type\":\"button\",\"x\":10,\"y\":335,"
                    + "\"width\":87,\"height\":31,\"text\":\"\",\"text_color\":0,"
                    + "\"image_id\":0,\"image_index\":0}]}")
                .getBytes(StandardCharsets.UTF_8));
    assertEquals(366, layout.control(10009).y() + layout.control(10009).height());
  }

  private static void assertRejected(String document) {
    try {
      UiLayout.read(document.getBytes(StandardCharsets.UTF_8));
      fail("accepted a layout it cannot place: " + document);
    } catch (IOException expected) {
    }
  }
}
