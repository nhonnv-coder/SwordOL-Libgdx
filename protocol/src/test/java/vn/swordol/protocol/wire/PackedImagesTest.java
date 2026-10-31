package vn.swordol.protocol.wire;

import static org.junit.Assert.*;
import static vn.swordol.protocol.wire.MapResourceTest.fixture;
import static vn.swordol.protocol.wire.MapResourceTest.reject;
import static vn.swordol.protocol.wire.MapResourceTest.sha256;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Oracle: the six images map1105100 downloaded, already rebuilt and CRC-verified by
 * tools/analysis/decode_images.py. The client's own RGB565 arithmetic is reproduced, not corrected.
 */
public class PackedImagesTest {
  @Test
  public void readsTheDownloadedImageIndexTheServerSent() throws Exception {
    List<PackedImages.Entry> index = PackedImages.index(fixture("map-images.bin"));
    assertEquals(6, index.size());
    assertEquals(689, index.get(0).imageId());
    assertEquals(0, index.get(0).packIndex());
    assertEquals(7533, index.get(0).offset());
    assertEquals(690, index.get(5).imageId());
    assertEquals(0, index.get(5).offset());
  }

  @Test
  public void rebuildsEachDownloadedPngByteForByte() throws Exception {
    byte[] pack = fixture("map-png.bin");
    String[][] expected = {
      {"685", "980894f783fb45f071deaa0daca35d08da0fcaf9dbc912dbb49ab17c4a726e1e"},
      {"686", "991a75ced32549ccb3ea93257015ceafccfa779809646cfba58e255ae8ae5302"},
      {"687", "300088530e8aa60e9bf0d961ac8730c9595707eeb4d905154f48e43d71a59415"},
      {"688", "c8c81499bb9146d9610373c6ea453f3767cd02d2c8d9a259fa5092835585943a"},
      {"689", "0a853b418a944b7622547f1d0eb70d6d50e04255ea94c612be2768f3e904e6a2"},
      {"690", "42c6368bb72349c74ed33dd98742e49895263fab5fd1a04f35f689b0f3e87217"}
    };
    for (PackedImages.Entry entry : PackedImages.index(fixture("map-images.bin"))) {
      String digest = sha256(PackedImages.png(pack, entry.offset()));
      boolean found = false;
      for (String[] row : expected) {
        if (Integer.parseInt(row[0]) == entry.imageId()) {
          assertEquals("image " + entry.imageId(), row[1], digest);
          found = true;
        }
      }
      assertTrue("unexpected image " + entry.imageId(), found);
    }
  }

  @Test
  public void rejectsRecordsAndIndexesOutsideTheirBuffer() throws Exception {
    byte[] pack = fixture("map-png.bin");
    byte[] index = fixture("map-images.bin");
    reject(() -> PackedImages.index(Arrays.copyOf(index, 1)));
    reject(() -> PackedImages.index(Arrays.copyOf(index, 10)));
    reject(() -> PackedImages.index(new byte[] {0, 5, 0, 0, 0, 1, 0}));
    reject(() -> PackedImages.index(null));
    reject(() -> PackedImages.png(pack, -1));
    reject(() -> PackedImages.png(pack, pack.length - 4));
    reject(() -> PackedImages.png(Arrays.copyOf(pack, 60), 0));
    byte[] corrupt = pack.clone();
    corrupt[14] ^= 0x5a;
    reject(() -> PackedImages.png(corrupt, 0));
  }
}
