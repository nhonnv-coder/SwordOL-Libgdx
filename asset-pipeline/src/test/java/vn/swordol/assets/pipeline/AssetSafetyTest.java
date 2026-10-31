package vn.swordol.assets.pipeline;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class AssetSafetyTest {
  @Test
  public void repeatedAssetRecordsShareOneDecodedBlobBudget() throws Exception {
    StringBuilder entries = new StringBuilder();
    for (int index = 0; index < 5; index++) {
      if (index != 0) {
        entries.append(',');
      }
      entries.append(asset().replace("\"a\"", "\"a" + index + "\""));
    }
    Path root = project(entries.toString());
    try (java.io.RandomAccessFile file =
        new java.io.RandomAccessFile(root.resolve("a.bin").toFile(), "rw")) {
      file.setLength(16 * 1024 * 1024);
    }
    AssetBuilder.build(root, root.resolve("bundle.swb"));
    assertTrue(Files.size(root.resolve("bundle.swb")) < 17 * 1024 * 1024);
  }

  private Path project(String assets) throws IOException {
    Path root = Files.createTempDirectory("asset-safety");
    Files.write(root.resolve("a.bin"), new byte[] {1, 2, 3});
    Files.write(
        root.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":[" + assets + "]}").getBytes(StandardCharsets.UTF_8));
    return root;
  }

  private String asset() {
    return "{\"id\":\"a\",\"type\":\"binary\",\"source\":\"a.bin\",\"dependencies\":[],\"provenance\":{}}";
  }

  @Test
  public void sourceAndRegistryCannotBeBuildDestinations() throws Exception {
    Path root = project(asset());
    byte[] registry = Files.readAllBytes(root.resolve("registry.json"));
    for (String target : new String[] {"a.bin", "registry.json"}) {
      try {
        AssetBuilder.build(root, root.resolve(target));
        fail();
      } catch (IOException expected) {
      }
    }
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(root.resolve("a.bin")));
    assertArrayEquals(registry, Files.readAllBytes(root.resolve("registry.json")));
  }

  @Test
  public void invalidMigrationDoesNotCreateBackup() throws Exception {
    Path root = project("");
    byte[] old =
        "{\"schemaVersion\":0,\"assets\":[{\"path\":\"../escape\"}]}"
            .getBytes(StandardCharsets.UTF_8);
    Files.write(root.resolve("registry.json"), old);
    try {
      AssetCli.migrate(root);
      fail();
    } catch (IOException expected) {
    }
    assertArrayEquals(old, Files.readAllBytes(root.resolve("registry.json")));
    assertFalse(Files.exists(root.resolve("registry.json.schema0.bak")));
  }

  @Test
  public void duplicateJsonKeysAndWrongFieldTypesAreRejected() throws Exception {
    for (String json :
        new String[] {
          "{\"schemaVersion\":0,\"schemaVersion\":1,\"assets\":[]}",
          "{\"schemaVersion\":\"1\",\"assets\":[]}",
          "{\"schemaVersion\":1,\"assets\":[{\"id\":1}]}",
          "{\"schemaVersion\":1,\"assets\":[]} false"
        }) {
      Path root = project("");
      Files.write(root.resolve("registry.json"), json.getBytes(StandardCharsets.UTF_8));
      try {
        AssetRegistry.load(root);
        fail(json);
      } catch (IOException expected) {
      }
    }
  }

  @Test
  public void oversizeSourceIsRejectedBeforeOutputPublication() throws Exception {
    Path root = project(asset());
    try (java.io.RandomAccessFile file =
        new java.io.RandomAccessFile(root.resolve("a.bin").toFile(), "rw")) {
      file.setLength(16 * 1024 * 1024 + 1);
    }
    Path output = root.resolve("bundle.swb");
    Files.write(output, new byte[] {9});
    try {
      AssetBuilder.build(root, output);
      fail();
    } catch (IOException expected) {
    }
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(output));
  }

  @Test
  public void failedImportRemovesStagingAndDoesNotPublishProject() throws Exception {
    Path root = project("");
    String manifest =
        "{\"source\":\"a.jar\",\"source_sha256\":\""
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "\",\"members\":[{\"archive_index\":1,\"source_path\":\"a.bin\","
            + "\"exported_path\":\"a.bin\",\"size\":3,\"sha256\":\""
            + "0000000000000000000000000000000000000000000000000000000000000000\"}]}";
    Files.write(root.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
    Path destination = root.resolve("imported");
    try {
      AssetCli.importLegacy(root.resolve("manifest.json"), destination);
      fail();
    } catch (IOException expected) {
    }
    assertFalse(Files.exists(destination));
    try (java.nio.file.DirectoryStream<Path> entries =
        Files.newDirectoryStream(root, ".asset-import-*")) {
      assertFalse(entries.iterator().hasNext());
    }
  }
}
