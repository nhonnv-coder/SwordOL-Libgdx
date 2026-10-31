package vn.swordol.assets.runtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class AssetBundleTest {
  // SHA-256 of the ASCII bytes "abc", independently known test vector.
  private static final String HASH =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
  private static final String PATH = "files/" + HASH;

  @Test
  public void loadsOutOfOrderSharedBlobsWithoutClosingBorrowedStream() throws Exception {
    String assets = asset("first", "[]") + "," + asset("second", "[\"first\"]");
    final boolean[] closed = {false};
    ByteArrayInputStream input =
        new ByteArrayInputStream(
            zip(manifest(assets), PATH, "abc".getBytes(StandardCharsets.UTF_8))) {
          @Override
          public void close() {
            closed[0] = true;
          }
        };
    AssetBundle bundle = AssetBundle.load(input);
    assertFalse(closed[0]);
    assertEquals(Arrays.asList("first", "second"), bundle.ids());
    assertEquals("binary", bundle.type("first"));
    byte[] bytes = bundle.read("first");
    bytes[0] = 0;
    assertArrayEquals(new byte[] {97, 98, 99}, bundle.read("first"));
    try {
      bundle.ids().add("third");
      fail("mutable ids");
    } catch (UnsupportedOperationException expected) {
    }
    assertNull(bundle.read("absent"));
    assertNull(bundle.type("absent"));
  }

  @Test
  public void rejectsTamperedHashAndSize() throws Exception {
    reject(manifest(asset("a", "[]")), PATH, new byte[] {97, 98, 100});
    reject(
        manifest(asset("a", "[]").replace("\"size\":3", "\"size\":4")),
        PATH,
        new byte[] {97, 98, 99});
  }

  @Test
  public void rejectsExponentEncodedIntegerFields() throws Exception {
    rejectManifest(manifest(asset("a", "[]")).replace("schemaVersion\":1", "schemaVersion\":1e0"));
    rejectManifest(manifest(asset("a", "[]")).replace("\"size\":3", "\"size\":3e0"));
  }

  @Test
  public void acceptsTenThousandAssetChainAndRejectsExcessAssets() throws Exception {
    StringBuilder assets = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      if (i > 0) assets.append(',');
      assets.append(asset("a" + i, i == 0 ? "[]" : "[\"a" + (i - 1) + "\"]"));
    }
    AssetBundle bundle =
        AssetBundle.load(
            new ByteArrayInputStream(
                zip(manifest(assets.toString()), PATH, new byte[] {97, 98, 99})));
    assertEquals(10000, bundle.ids().size());
    assertArrayEquals(new byte[] {97, 98, 99}, bundle.read("a9999"));
    assets.append(',').append(asset("extra", "[]"));
    rejectManifest(manifest(assets.toString()));
  }

  @Test
  public void rejectsTotalDecodedLimitAcrossIndividuallyValidBlobs() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      byte[] chunk = new byte[8192];
      for (int i = 0; i < 4; i++) {
        StringBuilder name = new StringBuilder("files/");
        for (int j = 0; j < 64; j++) name.append((char) ('a' + i));
        zip.putNextEntry(new ZipEntry(name.toString()));
        for (int j = 0; j < 2048; j++) zip.write(chunk);
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("bundle.json"));
      zip.write(manifest("").getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    try {
      AssetBundle.load(new ByteArrayInputStream(output.toByteArray()));
      fail("accepted >64MiB decoded bundle");
    } catch (IOException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("64 MiB"));
    }
  }

  @Test
  public void preservesStreamOwnershipOnValidationFailure() throws Exception {
    final boolean[] closed = {false};
    ByteArrayInputStream input =
        new ByteArrayInputStream(zip("{}", null, null)) {
          @Override
          public void close() {
            closed[0] = true;
          }
        };
    try {
      AssetBundle.load(input);
      fail("accepted missing fields");
    } catch (IOException expected) {
    }
    assertFalse(closed[0]);
  }

  @Test
  public void rejectsDuplicateIdsMissingAndCyclicDependencies() throws Exception {
    rejectManifest(manifest(asset("a", "[]") + "," + asset("a", "[]")));
    rejectManifest(manifest(asset("a", "[\"missing\"]")));
    rejectManifest(manifest(asset("a", "[\"b\"]") + "," + asset("b", "[\"a\"]")));
  }

  @Test
  public void rejectsUnsafeUnreferencedAndMissingEntries() throws Exception {
    reject(manifest(asset("a", "[]")), "../" + PATH, new byte[] {97, 98, 99});
    reject(manifest(""), PATH, new byte[] {97, 98, 99});
    reject(manifest(asset("a", "[]").replace(PATH, "files/../bad")), PATH, new byte[] {97, 98, 99});
    reject(manifest(asset("a", "[]")), null, null);
    reject("", PATH, new byte[] {97, 98, 99});
  }

  @Test
  public void rejectsWrongVersionAndRequiredFieldTypes() throws Exception {
    String valid = manifest(asset("a", "[]"));
    for (String bad :
        new String[] {
          valid.replace("schemaVersion\":1", "schemaVersion\":2"),
          valid.replace("schemaVersion\":1", "schemaVersion\":\"1\""),
          valid.replace("\"size\":3", "\"size\":3.0"),
          valid.replace("\"binary\"", "\"unknown\""),
          valid.replace("\"dependencies\":[]", "\"dependencies\":{}"),
          valid.replace("\"provenance\":{}", "\"provenance\":[]"),
          valid.replace("\"id\":\"a\"", "\"id\":3"),
          valid.replace("\"id\":\"a\"", "\"id\":\"\""),
          valid.replace("\"size\":3", "\"size\":-1"),
          valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")
        }) {
      rejectManifest(bad);
    }
  }

  @Test
  public void rejectsManifestAndBlobActualDecodedLimits() throws Exception {
    byte[] huge = new byte[16 * 1024 * 1024 + 1];
    reject(manifest(asset("a", "[]")), PATH, huge);
    char[] spaces = new char[4 * 1024 * 1024];
    Arrays.fill(spaces, ' ');
    rejectManifest(manifest(asset("a", "[]")) + new String(spaces));
  }

  @Test
  public void rejectsDeepJsonWithoutStackOverflow() throws Exception {
    StringBuilder nested = new StringBuilder();
    for (int i = 0; i < 1000; i++) nested.append('[');
    for (int i = 0; i < 1000; i++) nested.append(']');
    rejectManifest(
        manifest(asset("a", "[]"))
            .replace("\"provenance\":{}", "\"provenance\":{\"nested\":" + nested + "}"));
  }

  @Test
  public void rejectsDuplicateZipEntries() throws Exception {
    byte[] data = zip(manifest(""), "bundle.jsox", new byte[0]);
    byte[] old = "bundle.jsox".getBytes(StandardCharsets.UTF_8);
    byte[] replacement = "bundle.json".getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i <= data.length - old.length; i++) {
      boolean match = true;
      for (int j = 0; j < old.length; j++) if (data[i + j] != old[j]) match = false;
      if (match) System.arraycopy(replacement, 0, data, i, replacement.length);
    }
    expectRejected(data);
  }

  private static String asset(String id, String dependencies) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"binary\",\"path\":\""
        + PATH
        + "\",\"sha256\":\""
        + HASH
        + "\",\"size\":3,\"dependencies\":"
        + dependencies
        + ",\"provenance\":{}}";
  }

  private static String manifest(String assets) {
    return "{\"schemaVersion\":1,\"assets\":[" + assets + "]}";
  }

  private static byte[] zip(String manifest, String blobPath, byte[] bytes) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
      if (blobPath != null) {
        zip.putNextEntry(new ZipEntry(blobPath));
        zip.write(bytes);
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("bundle.json"));
      zip.write(manifest.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return buffer.toByteArray();
  }

  private static void rejectManifest(String value) throws Exception {
    reject(value, PATH, new byte[] {97, 98, 99});
  }

  private static void reject(String manifest, String path, byte[] bytes) throws Exception {
    expectRejected(zip(manifest, path, bytes));
  }

  private static void expectRejected(byte[] bytes) throws Exception {
    try {
      AssetBundle.load(new ByteArrayInputStream(bytes));
      fail("accepted invalid bundle");
    } catch (IOException expected) {
      assertNotNull(expected.getMessage());
    }
  }
}
