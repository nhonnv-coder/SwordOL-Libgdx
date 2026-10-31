package vn.swordol.protocol.wire;

import static org.junit.Assert.*;
import static vn.swordol.protocol.wire.MapResourceTest.fixture;
import static vn.swordol.protocol.wire.MapResourceTest.reject;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Oracle: map-ref.bin captured for map1101400, parsed identically by the verified Python preview.
 */
public class MapReferencesTest {
  @Test
  public void readsEveryTileReferenceOfTheCapturedMap() throws Exception {
    MapReferences refs = MapReferences.read(fixture("map-ref.bin"));
    assertEquals(236, refs.size());
    assertEquals(685, refs.imageCount());

    MapReferences.Reference first = refs.get(0);
    assertEquals(0xd000, first.flags());
    assertEquals(1, first.offsetX());
    assertEquals(1, first.offsetY());
    assertTrue(first.isObject());
    assertFalse(first.isAnimation());
    assertEquals(1, first.pieces().size());
    assertEquals(126, first.pieces().get(0).value());
    assertEquals(0, first.pieces().get(0).offsetX());
    assertEquals(0, first.pieces().get(0).offsetY());
    assertEquals(0, first.pieces().get(0).transform());

    MapReferences.Reference last = refs.get(235);
    assertEquals(0, last.flags());
    assertFalse(last.isObject());
    assertFalse(last.isLowDecoration());
    List<MapReferences.Piece> pieces = last.pieces();
    assertEquals(2, pieces.size());
    assertEquals(175, pieces.get(0).value());
    assertEquals(-8, pieces.get(0).offsetX());
    assertEquals(-16, pieces.get(0).offsetY());
    assertEquals(574, pieces.get(1).value());
    assertEquals(-17, pieces.get(1).offsetX());
    assertEquals(-31, pieces.get(1).offsetY());
  }

  @Test
  public void keepsTheComponentWidthEachFlagCombinationDeclares() throws Exception {
    MapReferences refs = MapReferences.read(fixture("map-ref.bin"));
    int animation = 0, object = 0, full = 0;
    for (int i = 0; i < refs.size(); i++) {
      MapReferences.Reference reference = refs.get(i);
      if (reference.isAnimation()) animation++;
      else if (reference.isObject()) object++;
      else full++;
    }
    assertEquals(2, animation);
    assertEquals(146, object);
    assertEquals(88, full);
  }

  @Test
  public void rejectsTruncatedTablesAndOffsetsOutsideTheBuffer() throws Exception {
    byte[] valid = fixture("map-ref.bin");
    reject(() -> MapReferences.read(Arrays.copyOf(valid, 3)));
    reject(() -> MapReferences.read(Arrays.copyOf(valid, 64)));
    reject(() -> MapReferences.read(new byte[] {-1, -1, 0, 1}));
    byte[] badOffset = valid.clone();
    badOffset[4] = 0x7f;
    badOffset[5] = (byte) 0xff;
    reject(() -> MapReferences.read(badOffset));
    reject(() -> MapReferences.read(null));
    MapReferences refs = MapReferences.read(valid);
    try {
      refs.get(refs.size());
      fail("Accepted reference beyond the table");
    } catch (IndexOutOfBoundsException expected) {
    }
  }
}
