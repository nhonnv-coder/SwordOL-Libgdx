package vn.swordol.assets.pipeline;

import static org.junit.Assert.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class DeterministicTimezoneTest {
  @Test
  public void buildBytesDoNotDependOnDefaultCalendarLocale() throws Exception {
    Path root = Files.createTempDirectory("locale-build");
    Files.write(root.resolve("a"), new byte[] {42});
    Files.write(
        root.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":["
                + "{\"id\":\"a\",\"type\":\"binary\",\"source\":\"a\",\"dependencies\":[],\"provenance\":{}}]}")
            .getBytes(StandardCharsets.UTF_8));
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimezone = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      Locale.setDefault(Locale.US);
      AssetBuilder.build(root, root.resolve("one"));
      Locale.setDefault(new Locale("th", "TH"));
      AssetBuilder.build(root, root.resolve("two"));
      assertArrayEquals(
          Files.readAllBytes(root.resolve("one")), Files.readAllBytes(root.resolve("two")));
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimezone);
    }
  }

  @Test
  public void buildBytesDoNotDependOnLocalTimezone() throws Exception {
    Path root = Files.createTempDirectory("timezone-build");
    Files.write(root.resolve("a"), new byte[] {42});
    Files.write(
        root.resolve("registry.json"),
        ("{\"schemaVersion\":1,\"assets\":["
                + "{\"id\":\"a\",\"type\":\"binary\",\"source\":\"a\",\"dependencies\":[],\"provenance\":{}}]}")
            .getBytes(StandardCharsets.UTF_8));
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      AssetBuilder.build(root, root.resolve("one"));
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Saigon"));
      AssetBuilder.build(root, root.resolve("two"));
      assertArrayEquals(
          Files.readAllBytes(root.resolve("one")), Files.readAllBytes(root.resolve("two")));
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
