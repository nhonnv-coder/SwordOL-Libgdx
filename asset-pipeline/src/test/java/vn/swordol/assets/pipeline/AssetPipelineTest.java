package vn.swordol.assets.pipeline;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.ZipFile;
import org.junit.Test;

public class AssetPipelineTest {
  private Path project(String assets) throws IOException {
    Path root = Files.createTempDirectory("asset-test");
    Files.write(root.resolve("a.bin"), new byte[] {1, 2, 3});
    registry(root, "{\"schemaVersion\":1,\"assets\":[" + assets + "]}");
    return root;
  }

  private void registry(Path root, String json) throws IOException {
    Files.write(root.resolve("registry.json"), json.getBytes(StandardCharsets.UTF_8));
  }

  private String asset(String id, String source, String deps) {
    return "{\"id\":\""
        + id
        + "\",\"type\":\"binary\",\"source\":\""
        + source
        + "\",\"dependencies\":["
        + deps
        + "],\"provenance\":{}}";
  }

  private void invalid(Path root) throws Exception {
    try {
      AssetRegistry.load(root);
      fail("invalid registry accepted");
    } catch (IOException expected) {
    }
  }

  @Test
  public void rejectsDuplicateMissingAndCyclicDependencies() throws Exception {
    invalid(project(asset("a", "a.bin", "") + "," + asset("a", "a.bin", "")));
    invalid(project(asset("a", "a.bin", "\"missing\"")));
    invalid(project(asset("a", "a.bin", "\"b\"") + "," + asset("b", "a.bin", "\"a\"")));
  }

  @Test
  public void rejectsEscapesCaseCollisionAndUnsupportedVersions() throws Exception {
    invalid(project(asset("a", "../a.bin", "")));
    invalid(project(asset("a", "a.bin", "") + "," + asset("b", "A.bin", "")));
    Path root = project("");
    registry(root, "{\"schemaVersion\":0,\"assets\":[]}");
    invalid(root);
  }

  @Test
  public void deterministicBuildAndPreservationOnFailure() throws Exception {
    Path root = project(asset("b", "a.bin", "\"a\"") + "," + asset("a", "a.bin", ""));
    assertEquals(Arrays.asList("b"), AssetRegistry.load(root).findUsages("a"));
    AssetRegistry.load(root).validate("a");
    Path out = root.resolve("out.swb");
    AssetBuilder.build(root, out);
    byte[] first = Files.readAllBytes(out);
    AssetBuilder.build(root, out);
    assertArrayEquals(first, Files.readAllBytes(out));
    try (ZipFile zip = new ZipFile(out.toFile())) {
      assertNotNull(zip.getEntry("bundle.json"));
      assertEquals(2, zip.size());
    }
    Files.delete(root.resolve("a.bin"));
    try {
      AssetBuilder.build(root, out);
      fail();
    } catch (IOException expected) {
    }
    assertArrayEquals(first, Files.readAllBytes(out));
  }

  @Test
  public void migrationRequiresExplicitCallAndKeepsBackup() throws Exception {
    Path root = project("");
    String old =
        "{\"schemaVersion\":0,\"assets\":[{\"id\":\"a\",\"type\":\"binary\",\"path\":\"a.bin\",\"dependencies\":[],\"provenance\":{}}]}";
    registry(root, old);
    invalid(root);
    AssetCli.migrate(root);
    assertEquals(
        old,
        new String(
            Files.readAllBytes(root.resolve("registry.json.schema0.bak")), StandardCharsets.UTF_8));
    AssetRegistry.load(root).validate("a");
  }

  @Test
  public void importsExporterManifestWithoutChangingSource() throws Exception {
    Path source = Files.createTempDirectory("export");
    Files.write(source.resolve("member.bin"), new byte[] {1, 2, 3});
    String hash = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";
    String manifest =
        "{\"source\":\"original.jar\",\"source_sha256\":\""
            + hash
            + "\",\"members\":[{\"archive_index\":7,\"source_path\":\"unknown.bin\",\"exported_path\":\"member.bin\",\"size\":3,\"sha256\":\""
            + hash
            + "\"}]}";
    Files.write(source.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
    Path destination = source.resolve("new-project");
    AssetCli.importLegacy(source.resolve("manifest.json"), destination);
    AssetRegistry.load(destination).validate("legacy.7");
    try {
      AssetCli.importLegacy(source.resolve("manifest.json"), destination);
      fail();
    } catch (IOException expected) {
    }
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(source.resolve("member.bin")));
  }
}
