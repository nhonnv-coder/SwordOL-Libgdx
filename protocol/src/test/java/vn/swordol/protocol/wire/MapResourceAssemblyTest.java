package vn.swordol.protocol.wire;

import static org.junit.Assert.*;
import static vn.swordol.protocol.wire.MapResourceTest.fixture;
import static vn.swordol.protocol.wire.MapResourceTest.reject;
import static vn.swordol.protocol.wire.MapResourceTest.sha256;

import java.util.Arrays;
import org.junit.Test;

/**
 * Evidence: CmdProcessor.processMapDataCmd resource actions, MapEx.dataArrrayCopy accumulating
 * chunks, updateArrayBoosImages replacing the image index per archive, and MapEx.loadZipData.
 */
public class MapResourceAssemblyTest {
  @Test
  public void assemblesChunkedMapDataAndInflatesItToTheCapturedDocument() throws Exception {
    byte[] zip = fixture("map-data.zip");
    MapResourceAssembly assembly = new MapResourceAssembly();
    assembly.accept(resource(15, Arrays.copyOfRange(zip, 0, 1000)));
    assertNull(assembly.completed(MapResourceAssembly.Kind.MAP_DATA));
    assertTrue(assembly.hasPending());
    assembly.accept(resource(15, Arrays.copyOfRange(zip, 1000, 1700)));
    assembly.accept(resource(16, Arrays.copyOfRange(zip, 1700, zip.length)));
    assertFalse(assembly.hasPending());
    byte[] completed = assembly.completed(MapResourceAssembly.Kind.MAP_DATA);
    assertArrayEquals(zip, completed);
    byte[] inflated = MapResourceAssembly.inflate(completed);
    assertEquals(4887, inflated.length);
    MapResource map = MapResource.read(inflated);
    assertEquals(71, map.columns());
    assertEquals(50, map.rows());
  }

  @Test
  public void keepsPackedImageBytesUnzippedAndReplacesTheIndexPerArchive() throws Exception {
    byte[] pack = fixture("map-png.bin");
    byte[] index = fixture("map-images.bin");
    MapResourceAssembly assembly = new MapResourceAssembly();
    assembly.accept(resource(9, Arrays.copyOfRange(pack, 0, 500)));
    assembly.accept(resource(10, Arrays.copyOfRange(pack, 500, pack.length)));
    assertArrayEquals(pack, assembly.completed(MapResourceAssembly.Kind.MAP_PNG));
    byte[] first = zipped(index);
    byte[] second = zipped(Arrays.copyOf(index, 9));
    assembly.accept(resource(13, first));
    assertArrayEquals(first, assembly.completed(MapResourceAssembly.Kind.MAP_IMAGES));
    assembly.accept(resource(14, second));
    assertArrayEquals(second, assembly.completed(MapResourceAssembly.Kind.MAP_IMAGES));
    assertFalse(assembly.hasPending());
  }

  @Test
  public void rejectsMalformedResourcePacketsAndOversizedTransfers() throws Exception {
    MapResourceAssembly assembly = new MapResourceAssembly();
    reject(() ->
        assembly.accept(
            Wire.logical(
                new WireWriter(1407).signedByte(15).shortValue(3).binary(new byte[2]).build())));
    reject(() ->
        assembly.accept(
            Wire.logical(new WireWriter(1407).signedByte(15).binary(new byte[2]).build())));
    reject(() ->
        assembly.accept(
            Wire.logical(
                new WireWriter(1110).signedByte(15).shortValue(2).binary(new byte[2]).build())));
    assembly.accept(Wire.logical(new WireWriter(1407).signedByte(1).integer(0).build()));
    assertFalse(assembly.hasPending());
    byte[] chunk = new byte[16384];
    for (int sent = 0; sent < 16 * 1024 * 1024; sent += chunk.length)
      assembly.accept(resource(11, chunk));
    reject(() -> assembly.accept(resource(11, chunk)));
    assembly.reset();
    assertFalse(assembly.hasPending());
    assertNull(assembly.completed(MapResourceAssembly.Kind.MAP_REF));
  }

  @Test
  public void refusesZipMembersThatDoNotMatchTheirStoredSizeOrCrc() throws Exception {
    byte[] zip = fixture("map-data.zip");
    assertEquals(
        "a4e879ec2f9313d9b5e7e2619a140f2dbb46963f43a87615068ed60ebd289e18",
        sha256(MapResourceAssembly.inflate(fixture("map-data.zip"))));
    byte[] badCrc = zip.clone();
    badCrc[14] ^= 0x7f;
    reject(() -> MapResourceAssembly.inflate(badCrc));
    byte[] badSize = zip.clone();
    badSize[22] ^= 0x7f;
    reject(() -> MapResourceAssembly.inflate(badSize));
    reject(() -> MapResourceAssembly.inflate(Arrays.copyOf(zip, 29)));
    reject(() -> MapResourceAssembly.inflate(Arrays.copyOf(zip, 200)));
    byte[] notZip = zip.clone();
    notZip[0] = 'Q';
    reject(() -> MapResourceAssembly.inflate(notZip));
  }

  private static Packet resource(int action, byte[] payload) throws java.io.IOException {
    return Wire.logical(
        new WireWriter(1407).signedByte(action).shortValue(payload.length).binary(payload).build());
  }

  /**
   * Builds the same single-member archive shape the server sends, so the test needs no fixture per
   * case.
   */
  private static byte[] zipped(byte[] content) throws Exception {
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(content);
    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("data");
    entry.setMethod(java.util.zip.ZipEntry.STORED);
    entry.setSize(content.length);
    entry.setCompressedSize(content.length);
    entry.setCrc(crc.getValue());
    entry.setTime(0L);
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
      zip.putNextEntry(entry);
      zip.write(content);
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }
}
