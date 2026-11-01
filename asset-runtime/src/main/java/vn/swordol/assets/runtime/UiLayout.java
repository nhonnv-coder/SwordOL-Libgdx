package vn.swordol.assets.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One original window as tools/analysis/decode_ui.py writes it: the screen box and the controls
 * {@code CtrlManager.newScreen} would place inside it, each keeping the id the client addresses it
 * by. A window is a fixed 640x360 design surface; coordinates are the original's own, measured from
 * the top left with y growing downwards.
 *
 * <p>Immutable and bounded, like the other bundle readers: the rules the producer followed are
 * checked again here, because bundle content is untrusted and the runtime must work without the
 * tools. Nothing is renamed to a meaning the client does not establish, and a control of a class
 * this reader cannot place is refused rather than dropped, since dropping one silently would move a
 * window's focus order onto a control that is no longer there.
 */
public final class UiLayout {
  /**
   * The control classes CtrlManager.newScreen knows: 2 button, 4 list, 7 edit, 11 text, 12 grid.
   */
  public enum Kind {
    BUTTON,
    LIST,
    EDIT,
    TEXT,
    GRID;

    static Kind of(String name) throws IOException {
      for (Kind kind : values()) if (kind.name().equalsIgnoreCase(name)) return kind;
      throw new IOException("Unknown control class: " + name);
    }
  }

  private static final int MAX_CONTROLS = 512;
  private static final int MAX_EXTENT = 4096;

  private final int windowId, screenId, x, y, width, height, focusId;
  private final List<Control> controls;
  private final Map<Integer, Control> byId;

  private UiLayout(
      int windowId,
      int screenId,
      int x,
      int y,
      int width,
      int height,
      int focusId,
      List<Control> controls) {
    this.windowId = windowId;
    this.screenId = screenId;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.focusId = focusId;
    this.controls = Collections.unmodifiableList(controls);
    Map<Integer, Control> index = new LinkedHashMap<>();
    for (Control control : controls) index.put(control.id, control);
    this.byId = Collections.unmodifiableMap(index);
  }

  public static UiLayout read(byte[] bytes) throws IOException {
    JsonObject root = RuntimeJson.object(RuntimeJson.parse(bytes, "ui layout"), "ui layout");
    int width = (int) RuntimeJson.integer(member(root, "width"), "window width");
    int height = (int) RuntimeJson.integer(member(root, "height"), "window height");
    if (width <= 0 || height <= 0 || width > MAX_EXTENT || height > MAX_EXTENT)
      throw new IOException("Window box out of range");
    List<JsonElement> raw = RuntimeJson.array(member(root, "controls"), "controls").asList();
    if (raw.size() > MAX_CONTROLS) throw new IOException("Window declares too many controls");
    List<Control> controls = new ArrayList<>();
    for (JsonElement element : raw) controls.add(control(RuntimeJson.object(element, "control")));
    return new UiLayout(
        (int) RuntimeJson.integer(member(root, "window_id"), "window id"),
        (int) optional(root, "screen_id"),
        (int) optional(root, "x"),
        (int) optional(root, "y"),
        width,
        height,
        (int) optional(root, "focus_id"),
        controls);
  }

  private static Control control(JsonObject source) throws IOException {
    Kind kind = Kind.of(RuntimeJson.string(member(source, "type"), "control class"));
    int x = (int) RuntimeJson.signed(member(source, "x"), "control x");
    int y = (int) RuntimeJson.signed(member(source, "y"), "control y");
    int w = (int) RuntimeJson.signed(member(source, "width"), "control width");
    int h = (int) RuntimeJson.signed(member(source, "height"), "control height");
    // Window 10 places buttons 10009 and 10010 six pixels past its own bottom edge, and the
    // original clips them instead of rejecting the file, so overflow is data rather than damage.
    if (w < 0 || h < 0 || w > MAX_EXTENT || h > MAX_EXTENT || x < -MAX_EXTENT || y < -MAX_EXTENT)
      throw new IOException("Control box out of range");
    JsonElement text = source.get("text");
    JsonElement colour = source.get("text_color");
    JsonElement image = source.get("image_id");
    JsonElement imageIndex = source.get("image_index");
    JsonElement limit = source.get("char_limit");
    return new Control(
        (int) RuntimeJson.integer(member(source, "id"), "control id"),
        kind,
        x,
        y,
        w,
        h,
        text == null || text.isJsonNull() ? "" : RuntimeJson.string(text, "control text"),
        colour == null ? 0 : (int) RuntimeJson.integer(colour, "control text colour"),
        image == null ? 0 : (int) RuntimeJson.integer(image, "control image"),
        imageIndex == null ? 0 : (int) RuntimeJson.integer(imageIndex, "control image index"),
        limit == null ? 0 : (int) RuntimeJson.integer(limit, "control char limit"));
  }

  private static JsonElement member(JsonObject source, String name) throws IOException {
    JsonElement value = source.get(name);
    if (value == null || value.isJsonNull()) throw new IOException("Layout is missing " + name);
    return value;
  }

  private static long optional(JsonObject source, String name) throws IOException {
    JsonElement value = source.get(name);
    return value == null || value.isJsonNull() ? 0 : RuntimeJson.signed(value, name);
  }

  public int windowId() {
    return windowId;
  }

  public int screenId() {
    return screenId;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  /** The control the window starts focused on, as the layout records it. */
  public int focusId() {
    return focusId;
  }

  public List<Control> controls() {
    return controls;
  }

  /** The control with this original id, or null when the window holds none. */
  public Control control(int id) {
    return byId.get(id);
  }

  /** One placed control, in the original's own coordinate space. */
  public static final class Control {
    private final int id, x, y, width, height, textColor, imageId, imageIndex, charLimit;
    private final Kind kind;
    private final String text;

    Control(
        int id,
        Kind kind,
        int x,
        int y,
        int width,
        int height,
        String text,
        int textColor,
        int imageId,
        int imageIndex,
        int charLimit) {
      this.id = id;
      this.kind = kind;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.text = text;
      this.textColor = textColor;
      this.imageId = imageId;
      this.imageIndex = imageIndex;
      this.charLimit = charLimit;
    }

    public int id() {
      return id;
    }

    public Kind kind() {
      return kind;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int width() {
      return width;
    }

    public int height() {
      return height;
    }

    public String text() {
      return text;
    }

    /** Packed 0xRRGGBB as the layout stores it. */
    public int textColor() {
      return textColor;
    }

    /** The legacy image this control draws, or 0 when it draws none. */
    public int imageId() {
      return imageId;
    }

    /** Which cell of that image the control draws; ImagePointer steps along a strip by this. */
    public int imageIndex() {
      return imageIndex;
    }

    public int charLimit() {
      return charLimit;
    }
  }
}
