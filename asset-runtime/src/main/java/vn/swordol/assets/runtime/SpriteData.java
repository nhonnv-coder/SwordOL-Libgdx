package vn.swordol.assets.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An original {@code .dat} sprite as tools/analysis/decode_sprites.py writes it: a table of image
 * ids, modules that cut a rectangle out of one of those images, frames that place modules at signed
 * offsets with a MIDP {@code drawRegion} transform, and actions that list the frames they play.
 *
 * <p>Immutable and bounded. Like {@link MapData} this reader repeats the producer's rules instead
 * of trusting them, because bundle content is untrusted and the runtime must work without the
 * tools. Per-frame timing is not part of the document: the original's action entries carry a third
 * byte the shipped client never reads, so a player of these frames has to choose its own rate.
 */
public final class SpriteData {
  private static final int MAX_IMAGES = 256;
  private static final int MAX_MODULES = 4096;
  private static final int MAX_FRAMES = 4096;
  private static final int MAX_PLACEMENTS = 256;
  private static final int MAX_ACTIONS = 512;
  private static final int MAX_ACTION_FRAMES = 1024;
  private static final int MAX_COORDINATE = 32767;

  private final int spriteId;
  private final int[] images;
  private final List<Module> modules;
  private final List<List<Placement>> frames;
  private final Map<Integer, List<Integer>> actions;

  private SpriteData(
      int spriteId,
      int[] images,
      List<Module> modules,
      List<List<Placement>> frames,
      Map<Integer, List<Integer>> actions) {
    this.spriteId = spriteId;
    this.images = images;
    this.modules = Collections.unmodifiableList(modules);
    this.frames = Collections.unmodifiableList(frames);
    this.actions = Collections.unmodifiableMap(actions);
  }

  public static SpriteData read(byte[] bytes) throws IOException {
    JsonObject document = RuntimeJson.object(RuntimeJson.parse(bytes, "sprite"), "sprite");
    int spriteId = (int) bounded(document, "sprite_id", 0, Integer.MAX_VALUE);
    JsonArray imageTable = RuntimeJson.array(field(document, "images"), "sprite images");
    if (imageTable.size() > MAX_IMAGES) {
      throw new IOException("Sprite declares too many images");
    }
    int[] images = new int[imageTable.size()];
    for (int index = 0; index < images.length; index++) {
      images[index] = (int) integer(imageTable.get(index), "image id", 0, Integer.MAX_VALUE);
    }

    JsonArray moduleTable = RuntimeJson.array(field(document, "modules"), "sprite modules");
    if (moduleTable.size() > MAX_MODULES) {
      throw new IOException("Sprite declares too many modules");
    }
    List<Module> modules = new ArrayList<>(moduleTable.size());
    for (JsonElement element : moduleTable) {
      JsonObject module = RuntimeJson.object(element, "sprite module");
      int image = (int) bounded(module, "image", 0, images.length - 1);
      int width = (int) bounded(module, "width", 1, MAX_COORDINATE);
      int height = (int) bounded(module, "height", 1, MAX_COORDINATE);
      int x = (int) bounded(module, "x", 0, MAX_COORDINATE);
      int y = (int) bounded(module, "y", 0, MAX_COORDINATE);
      modules.add(new Module(image, x, y, width, height));
    }

    JsonArray frameTable = RuntimeJson.array(field(document, "frames"), "sprite frames");
    if (frameTable.size() > MAX_FRAMES) {
      throw new IOException("Sprite declares too many frames");
    }
    List<List<Placement>> frames = new ArrayList<>(frameTable.size());
    for (JsonElement element : frameTable) {
      JsonArray placements = RuntimeJson.array(element, "sprite frame");
      if (placements.size() > MAX_PLACEMENTS) {
        throw new IOException("Sprite frame places too many modules");
      }
      List<Placement> frame = new ArrayList<>(placements.size());
      for (JsonElement item : placements) {
        JsonObject placement = RuntimeJson.object(item, "sprite placement");
        frame.add(
            new Placement(
                (int) bounded(placement, "module", 0, modules.size() - 1),
                (int) bounded(placement, "x", -MAX_COORDINATE, MAX_COORDINATE),
                (int) bounded(placement, "y", -MAX_COORDINATE, MAX_COORDINATE),
                (int) bounded(placement, "transform", 0, 7)));
      }
      frames.add(Collections.unmodifiableList(frame));
    }

    JsonArray actionTable = RuntimeJson.array(field(document, "actions"), "sprite actions");
    if (actionTable.size() > MAX_ACTIONS) {
      throw new IOException("Sprite declares too many actions");
    }
    Map<Integer, List<Integer>> actions = new LinkedHashMap<>();
    for (JsonElement element : actionTable) {
      JsonObject action = RuntimeJson.object(element, "sprite action");
      int id = (int) bounded(action, "id", 0, Integer.MAX_VALUE);
      JsonArray entries = RuntimeJson.array(field(action, "frames"), "sprite action frames");
      if (entries.size() > MAX_ACTION_FRAMES) {
        throw new IOException("Sprite action lists too many frames");
      }
      List<Integer> played = new ArrayList<>(entries.size());
      for (JsonElement item : entries) {
        JsonObject entry = RuntimeJson.object(item, "sprite action frame");
        played.add((int) bounded(entry, "frame", 0, frames.size() - 1));
      }
      if (actions.put(id, Collections.unmodifiableList(played)) != null) {
        throw new IOException("Sprite repeats action " + id);
      }
    }
    // Every sprite the original ships has at least one of each, so an empty table is a broken file.
    if (images.length == 0 || modules.isEmpty() || frames.isEmpty() || actions.isEmpty()) {
      throw new IOException("Sprite has an empty image, module, frame or action table");
    }
    return new SpriteData(spriteId, images, modules, frames, actions);
  }

  public int spriteId() {
    return spriteId;
  }

  public int imageCount() {
    return images.length;
  }

  /** The legacy image id of this slot; 0 means the original leaves the slot empty. */
  public int imageId(int index) {
    return images[index];
  }

  public int moduleCount() {
    return modules.size();
  }

  public Module module(int index) {
    return modules.get(index);
  }

  public int frameCount() {
    return frames.size();
  }

  public List<Placement> placements(int frame) {
    return frames.get(frame);
  }

  public List<Integer> actionIds() {
    return Collections.unmodifiableList(new ArrayList<>(actions.keySet()));
  }

  public boolean hasAction(int id) {
    return actions.containsKey(id);
  }

  /** The frame indexes this action plays, in order. */
  public List<Integer> action(int id) {
    List<Integer> played = actions.get(id);
    if (played == null) {
      throw new IndexOutOfBoundsException("Sprite has no action " + id);
    }
    return played;
  }

  public int actionFrameCount(int id) {
    return action(id).size();
  }

  /** The frame index this action shows at this step. */
  public int actionFrame(int id, int step) {
    return action(id).get(step);
  }

  private static JsonElement field(JsonObject document, String name) throws IOException {
    JsonElement value = document.get(name);
    if (value == null) {
      throw new IOException("Sprite document is missing " + name);
    }
    return value;
  }

  private static long bounded(JsonObject document, String name, long low, long high)
      throws IOException {
    return integer(field(document, name), name, low, high);
  }

  private static long integer(JsonElement value, String name, long low, long high)
      throws IOException {
    long number = RuntimeJson.signed(value, name);
    if (number < low || number > high) {
      throw new IOException("Sprite field " + name + " is outside its range: " + number);
    }
    return number;
  }

  /** A rectangle of one image in the sprite's image table. */
  public static final class Module {
    private final int imageIndex, x, y, width, height;

    Module(int imageIndex, int x, int y, int width, int height) {
      this.imageIndex = imageIndex;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    public int imageIndex() {
      return imageIndex;
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
  }

  /** One module placed in a frame, with the stored MIDP drawRegion transform. */
  public static final class Placement {
    private final int moduleIndex, x, y, transform;

    Placement(int moduleIndex, int x, int y, int transform) {
      this.moduleIndex = moduleIndex;
      this.x = x;
      this.y = y;
      this.transform = transform;
    }

    public int moduleIndex() {
      return moduleIndex;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int transform() {
      return transform;
    }
  }
}
