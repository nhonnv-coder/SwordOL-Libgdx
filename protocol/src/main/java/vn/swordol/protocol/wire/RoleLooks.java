package vn.swordol.protocol.wire;

/**
 * The look table the original creation screen is built from, {@code Business.newRoleLookFaceDatID}:
 * ten professions by two sexes, holding the look face each combination wears.
 *
 * <p>A zero entry is not a gap in the data. {@code NewRoleWnd_Flush} deactivates the sex button
 * whose column is zero, and {@code NewRoleWnd_OK} returns without building a packet when the whole
 * row is zero, so a zero is the original refusing that combination. Professions are numbered the
 * way the screen numbers them, 1 to 10, because the request writes {@code profession * 10 + 1}.
 */
public final class RoleLooks {
  /** Sex values the screen derives as {@code button id % 1000 - 15}: 10015 male, 10016 female. */
  public static final int MALE = 0, FEMALE = 1;

  private static final int[][] LOOK_FACE = {
    {110000, 210000},
    {0, 220000},
    {130000, 0},
    {140000, 240000},
    {150000, 0},
    {0, 260000},
    {170000, 270000},
    {180000, 280000},
    {0, 0},
    {0, 0}
  };

  /** How many professions the table holds; the screen's buttons are 1 to this value. */
  public static final int PROFESSIONS = LOOK_FACE.length;

  private RoleLooks() {}

  /** The look face of this combination, or 0 when the original offers none. */
  public static int lookFace(int profession, int sex) {
    if (profession < 1 || profession > PROFESSIONS || (sex != MALE && sex != FEMALE)) {
      return 0;
    }
    return LOOK_FACE[profession - 1][sex];
  }

  public static boolean offers(int profession, int sex) {
    return lookFace(profession, sex) != 0;
  }

  /** False for a profession whose row is zero in both columns, which the original cannot send. */
  public static boolean creatable(int profession) {
    return offers(profession, MALE) || offers(profession, FEMALE);
  }
}
