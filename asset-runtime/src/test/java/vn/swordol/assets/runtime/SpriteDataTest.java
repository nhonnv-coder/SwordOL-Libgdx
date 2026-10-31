package vn.swordol.assets.runtime;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/**
 * Reads the sprite documents tools/analysis/decode_sprites.py produces from the original {@code
 * .dat} files: an image table, modules that cut rectangles out of those images, frames that place
 * modules, and actions that list frames.
 */
public class SpriteDataTest {
  @Test
  public void readsADecodedSpriteOfTheOriginalClient() throws Exception {
    SpriteData sprite = SpriteData.read(fixture("2010000.sprite.json"));
    assertEquals(2010000, sprite.spriteId());
    assertEquals(1, sprite.imageCount());
    assertEquals(2990000, sprite.imageId(0));
    assertEquals(13, sprite.moduleCount());
    assertEquals(5, sprite.frameCount());

    SpriteData.Module module = sprite.module(0);
    assertEquals(0, module.imageIndex());
    assertEquals(46, module.x());
    assertEquals(55, module.y());
    assertEquals(70, module.width());
    assertEquals(33, module.height());

    assertEquals(java.util.Collections.singletonList(1), sprite.actionIds());
    assertTrue(sprite.hasAction(1));
    assertFalse(sprite.hasAction(0));
    assertEquals(13, sprite.actionFrameCount(1));
    List<SpriteData.Placement> frame = sprite.placements(sprite.actionFrame(1, 0));
    assertFalse(frame.isEmpty());
    assertEquals(0, frame.get(0).moduleIndex());
    assertEquals(-36, frame.get(0).x());
    assertEquals(-16, frame.get(0).y());
    assertEquals(0, frame.get(0).transform());
    for (int index = 0; index < sprite.frameCount(); index++) {
      for (SpriteData.Placement placement : sprite.placements(index)) {
        assertTrue(placement.moduleIndex() >= 0 && placement.moduleIndex() < sprite.moduleCount());
        assertTrue(placement.transform() >= 0 && placement.transform() <= 7);
      }
    }
  }

  @Test
  public void refusesDocumentsThatWouldIndexOutsideTheirTables() throws Exception {
    reject("{}");
    reject("{\"sprite_id\":1,\"images\":[],\"modules\":[],\"frames\":[],\"actions\":[]}");
    reject(
        document("[1]", "[{\"image\":5,\"x\":0,\"y\":0,\"width\":1,\"height\":1}]", "[[]]", "[]"));
    reject(document("[1]", module(), "[[{\"module\":9,\"x\":0,\"y\":0,\"transform\":0}]]", "[]"));
    reject(
        document("[1]", module(), frame(), "[{\"id\":0,\"frames\":[{\"frame\":9,\"flag\":0}]}]"));
    reject(
        document("[1]", module(), frame(), "[{\"id\":-1,\"frames\":[{\"frame\":0,\"flag\":0}]}]"));
    reject(document("[1]", module(), "[[{\"module\":0,\"x\":0,\"y\":0,\"transform\":9}]]", "[]"));
    reject(
        document("[1]", module(), frame(), "[{\"id\":0,\"frames\":[]},{\"id\":0,\"frames\":[]}]"));
  }

  @Test
  public void acceptsAMinimalSpriteAndCopiesItsTables() throws Exception {
    SpriteData sprite =
        SpriteData.read(
            document(
                    "[1,2]",
                    module(),
                    frame(),
                    "[{\"id\":7,\"frames\":[{\"frame\":0,\"flag\":3}]}]")
                .getBytes(StandardCharsets.UTF_8));
    assertEquals(2, sprite.imageCount());
    assertEquals(1, sprite.actionIds().size());
    assertEquals(Integer.valueOf(7), sprite.actionIds().get(0));
    assertEquals(0, sprite.actionFrame(7, 0));
    assertEquals(1, sprite.actionFrameCount(7));
    try {
      sprite.actionFrame(7, 1);
      fail("accepted a frame beyond the action");
    } catch (IndexOutOfBoundsException expected) {
      // the caller must not walk past the action's own frames
    }
  }

  private static String module() {
    return "[{\"image\":0,\"x\":1,\"y\":2,\"width\":3,\"height\":4}]";
  }

  private static String frame() {
    return "[[{\"module\":0,\"x\":-1,\"y\":-2,\"transform\":2}]]";
  }

  private static String document(String images, String modules, String frames, String actions) {
    return "{\"sprite_id\":1,\"placement_size\":4,\"images\":"
        + images
        + ",\"modules\":"
        + modules
        + ",\"frames\":"
        + frames
        + ",\"actions\":"
        + actions
        + "}";
  }

  private static void reject(String json) {
    try {
      SpriteData.read(json.getBytes(StandardCharsets.UTF_8));
      fail("accepted an invalid sprite document");
    } catch (IOException expected) {
      // the runtime treats bundle content as untrusted
    }
  }

  private static byte[] fixture(String name) throws IOException {
    try (java.io.InputStream input = SpriteDataTest.class.getResourceAsStream("/sprites/" + name)) {
      assertNotNull("missing fixture " + name, input);
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
        bytes.write(buffer, 0, read);
      }
      return bytes.toByteArray();
    }
  }
}
