package vn.swordol.client.render;

/**
 * The original's look rules, kept as plain arithmetic so they can be checked without a window.
 *
 * <p>MapObject's constructor decides which sprite file a look face uses, ISpriteEx.ISpriteImageInit
 * rewrites a player's image table from the look's sex and scan digits, Engine.getIspActionIndex
 * turns an action and a direction into one of the sprite's action ids, and ISpriteEx.paint mirrors
 * the eastern direction instead of storing separate frames for it.
 */
public final class Look {
  /** Direction values MapObject.checkmove assigns: 0 is +y, 1 is +x, 2 is -y, 3 is -x. */
  public static final int SOUTH = 0, EAST = 1, NORTH = 2, WEST = 3;

  /** The original advances a sprite's action frame on a fixed 50 ms tick in nextActionFrame. */
  public static final long FRAME_MILLIS = 50;

  private Look() {}

  /** The sprite file this look face loads, or 0 for looks this slice does not assemble. */
  public static int spriteId(int lookFace) {
    if (lookFace == 2010000 || lookFace == 1110000) {
      return lookFace;
    }
    int look = lookFace / 100000;
    if (look == 1 || look == 2) {
      return profession(lookFace / 10000);
    }
    if (look == 3) {
      return lookFace % 100000 / 100;
    }
    if (look == 4) {
      return lookFace % 100000 / 100 * 10000;
    }
    if (look == 7) {
      return lookFace;
    }
    // Mounts (8) and crops (9) are built by Engine.createMounts/createCrops, not read as a file.
    return 0;
  }

  /** MapObject.changeProfessionToDatID. */
  private static int profession(int head) {
    switch (head) {
      case 13:
      case 14:
      case 24:
        return 2 * 100000;
      case 22:
      case 26:
        return 3 * 100000;
      default:
        return 1 * 100000;
    }
  }

  /**
   * The image ids a sprite actually draws with for this look. A player look (1 or 2) has its table
   * rewritten by ISpriteImageInit; every other look keeps the table the sprite file declares. The
   * returned array is a copy, and a zero slot means the original draws nothing there.
   */
  public static int[] imageTable(int[] declared, int lookFace) {
    int[] table = declared.clone();
    int look = lookFace / 100000;
    if (look != 1 && look != 2) {
      return table;
    }
    int scan = lookFace / 10000 % 10;
    for (int index = table.length - 2; index >= 0; index--) {
      if (index == 9 || index == 10) {
        continue;
      }
      if (index == 1 || index == 8 || index >= 11 || index == 20) {
        table[index] = 0;
        continue;
      }
      int base = table[index] / 10000000;
      if (base < 2) {
        base *= look;
      }
      table[index] = base * 10000000 + table[index] / 100000 % 100 * 100000 + scan * 1000;
    }
    return table;
  }

  /** Engine.getIspActionIndex: the stored frames face west, so east reuses them mirrored. */
  public static int actionId(int actionIndex, int direction) {
    return actionIndex * 100 + (direction == WEST ? EAST : direction);
  }

  public static boolean mirrored(int direction) {
    return direction == EAST;
  }

  /** ISpriteEx.transformMirror. */
  public static int mirrorTransform(int transform) {
    switch (transform) {
      case 0:
        return 2;
      case 2:
        return 0;
      case 5:
        return 4;
      case 4:
        return 5;
      case 3:
        return 1;
      case 1:
        return 3;
      case 6:
        return 7;
      case 7:
        return 6;
      default:
        return 0;
    }
  }

  /** ISpriteEx.coordinateTransform: a transposing transform mirrors around its height. */
  public static int mirrorOffsetX(int x, int width, int height, int transform) {
    if (transform == 4 || transform == 5 || transform == 6 || transform == 7) {
      return -x - height;
    }
    return -x - width;
  }
}
