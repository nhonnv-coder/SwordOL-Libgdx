package vn.swordol.classic;

import static org.junit.Assert.*;

import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Test;

public class ClassicRuntimeTest {
  @Test
  public void bootsPaintsAndForwardsKeysAndPointerWithoutAService() throws Exception {
    Path folder = Files.createTempDirectory("classic-probe");
    Path jar = folder.resolve("probe.jar");
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.putValue("Manifest-Version", "1.0");
    attributes.putValue("MIDlet-Name", "BridgeProbe");
    attributes.putValue("MIDlet-Version", "1.0");
    attributes.putValue("MIDlet-Vendor", "SwordOL");
    attributes.putValue("MIDlet-1", "Probe,,vn.swordol.classic.ProbeMidlet");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      for (String name : new String[] {"ProbeMidlet", "ProbeMidlet$ProbeCanvas"}) {
        String resource = "vn/swordol/classic/" + name + ".class";
        output.putNextEntry(new JarEntry(resource));
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
          byte[] buffer = new byte[4096];
          int length;
          while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
          }
        }
        output.closeEntry();
      }
    }
    ClassicRuntime runtime = new ClassicRuntime(64, 48);
    Class<?> fixture = null;
    try {
      runtime.start(jar);
      long deadline = System.currentTimeMillis() + 5000;
      while (runtime.frames().sequence() == 0 && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertTrue("No framebuffer produced", runtime.frames().sequence() > 0);
      ByteBuffer pixels = ByteBuffer.allocate(64 * 48 * 4);
      runtime.frames().copyRgba(pixels);
      assertEquals(0x11, pixels.get(0) & 255);
      assertEquals(0x22, pixels.get(1) & 255);
      assertEquals(0x33, pixels.get(2) & 255);
      fixture = runtime.currentMidlet().getClass();
      runtime.button(KeyEvent.VK_UP, '\0', true);
      runtime.button(KeyEvent.VK_UP, '\0', false);
      runtime.pointer(0, 12, 17);
      deadline = System.currentTimeMillis() + 3000;
      while (fixture.getField("pointerX").getInt(null) != 12
          && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertEquals(1, fixture.getField("lastAction").getInt(null));
      assertEquals(12, fixture.getField("pointerX").getInt(null));
      assertEquals(17, fixture.getField("pointerY").getInt(null));
      fixture.getMethod("showTextBox").invoke(null);
      Thread.sleep(100);
      runtime.type('\u0110');
      runtime.type('a');
      assertEquals("\u0110a", fixture.getMethod("textValue").invoke(null));
      final Class<?> probe = fixture;
      fixture.getField("holdDestroy").setBoolean(null, true);
      Thread unblock =
          new Thread(() -> {
            try {
              Thread.sleep(6000);
              probe.getField("holdDestroy").setBoolean(null, false);
            } catch (Exception error) {
              throw new AssertionError(error);
            }
          });
      unblock.setDaemon(true);
      unblock.start();
      long start = System.currentTimeMillis();
      runtime.close();
      assertTrue(
          "Shutdown must not wait indefinitely for legacy network workers",
          System.currentTimeMillis() - start < 4500);
    } finally {
      if (fixture != null) {
        fixture.getField("holdDestroy").setBoolean(null, false);
      }
      runtime.close();
    }
    assertNotNull(fixture);
    assertTrue(fixture.getField("destroyed").getBoolean(null));
  }
}
