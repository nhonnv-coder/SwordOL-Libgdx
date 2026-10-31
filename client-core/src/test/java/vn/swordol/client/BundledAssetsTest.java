package vn.swordol.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.security.MessageDigest;
import org.junit.Test;
import vn.swordol.assets.runtime.AssetBundle;

public class BundledAssetsTest {
  @Test
  public void demoBundleLoadsOriginalLogoByStableId() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/bundles/demo.swb")) {
      assertNotNull("Build the demo asset bundle before packaging the client", input);
      AssetBundle bundle = AssetBundle.load(input);
      assertEquals("image", bundle.type("brand.logo"));
      byte[] logo = bundle.read("brand.logo");
      assertArrayEquals(
          new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, java.util.Arrays.copyOf(logo, 8));
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(logo);
      StringBuilder hex = new StringBuilder();
      for (byte value : digest) {
        hex.append(String.format("%02x", value & 255));
      }
      assertEquals(
          "e836588490271c4d5b6efa3b2ce8162f210b9ad0dddd9f0b2d8e5501316f0d1c", hex.toString());
    }
  }
}
