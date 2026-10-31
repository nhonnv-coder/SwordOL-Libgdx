package vn.swordol.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import vn.swordol.client.modules.world.WorldState;
import vn.swordol.client.modules.world.WorldView;
import vn.swordol.client.render.ImageSet;
import vn.swordol.client.render.MapView;
import vn.swordol.client.render.SpriteBody;
import vn.swordol.client.render.TileImages;
import vn.swordol.client.render.WorldMap;
import vn.swordol.client.render.WorldRenderer;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * Draws the map the server sent and the entities on it, and turns arrow keys into the walk the
 * original client sends. The character is a placeholder marker: assembling the original character
 * sprite from look, hair and weapon is a later stage, and drawing a stand-in is honest about that.
 * Everything here runs on the render thread; it never touches a socket.
 */
public final class NativeWorldScreen implements WorldView, Disposable {
  private final BitmapFont font;
  private final Texture marker;
  private final SpriteBatch batch = new SpriteBatch();
  private final OrthographicCamera camera = new OrthographicCamera();
  private final String imageLocation;
  private TileImages tiles;
  private WorldRenderer renderer;
  private ImageSet images;
  private SpriteBody body;
  private WorldState world;
  private Input input;
  private String notice = "";
  private int bodyLook, lastX = -1, lastY = -1;
  private long walkingUntil, lastFrame;

  public NativeWorldScreen(BitmapFont font, Texture marker, String imageLocation) {
    this.font = font;
    this.marker = marker;
    this.imageLocation = imageLocation;
  }

  @Override
  public void setWorldInput(Input input) {
    this.input = input;
  }

  @Override
  public void showMap(
      WorldPackets.MapIdentity identity,
      MapResource resource,
      MapReferences references,
      Map<Integer, byte[]> images) {
    release();
    WorldMap map = new WorldMap(resource);
    try {
      this.images = ImageSet.open(images, imageLocation);
      tiles = TileImages.load(map, references, this.images);
    } catch (IOException ex) {
      notice = "Không nạp được ảnh tile của bản đồ.";
      return;
    }
    renderer = new WorldRenderer(map, references, tiles);
    notice = describe(identity, map);
  }

  @Override
  public void showWorld(WorldState state) {
    world = state;
  }

  @Override
  public void mapChanged() {
    if (renderer != null) renderer.refresh();
  }

  @Override
  public void clearWorld() {
    world = null;
    notice = "";
    release();
  }

  /** Loads the body of the look the server sent, once, and keeps the marker when it has none. */
  private void ensureBody() {
    if (world.userLook() == 0 || world.userLook() == bodyLook) {
      return;
    }
    bodyLook = world.userLook();
    if (body != null) {
      body.dispose();
      body = null;
    }
    if (images == null) {
      return;
    }
    try {
      body = SpriteBody.load(bodyLook, images);
    } catch (IOException | RuntimeException ex) {
      body = null;
    }
  }

  /** The original walks while its position changes and stands again shortly after it stops. */
  private int actionIndex() {
    long now = System.currentTimeMillis();
    if (world.userX() != lastX || world.userY() != lastY) {
      if (lastX >= 0 && body != null && walkingUntil < now) {
        body.resetAnimation();
      }
      lastX = world.userX();
      lastY = world.userY();
      walkingUntil = now + 300;
    }
    return walkingUntil > now ? 1 : 0;
  }

  public boolean isShowing() {
    return renderer != null && world != null && world.hasMap();
  }

  /**
   * Draws one frame and sends at most one step per key press, as the original does per key event.
   */
  public void render() {
    if (!isShowing()) return;
    readKeys();
    WorldMap map = renderer.map();
    int viewWidth = Gdx.graphics.getWidth(), viewHeight = Gdx.graphics.getHeight();
    camera.setToOrtho(false, viewWidth, viewHeight);
    batch.setProjectionMatrix(camera.combined);
    int offsetX = MapView.offset(world.userX() * map.cellWidth(), viewWidth, map.widthInPixels());
    int offsetDown =
        MapView.offset(world.userY() * map.cellHeight(), viewHeight, map.heightInPixels());
    float originX = -offsetX;
    float originY = Math.min(0, -(map.heightInPixels() - offsetDown - viewHeight));
    ensureBody();
    int action = actionIndex();
    long now = System.currentTimeMillis();
    if (body != null) {
      body.tick(lastFrame == 0 ? 0 : now - lastFrame, action == 1);
    }
    lastFrame = now;
    batch.begin();
    renderer.draw(batch, originX, originY, viewWidth, viewHeight);
    for (WorldState.Player player : world.otherPlayers())
      drawEntity(
          map,
          originX,
          originY,
          player.x(),
          player.y(),
          player.name(),
          new Color(.45f, .75f, 1f, .85f));
    if (body != null) {
      batch.setColor(Color.WHITE);
      body.draw(
          batch,
          action,
          world.userDirection(),
          originX + MapView.cellX(world.userX(), map),
          originY + MapView.cellY(world.userY(), map) + map.cellHeight());
      drawName(
          map,
          originX,
          originY,
          world.userX(),
          world.userY(),
          world.userName(),
          new Color(1f, .9f, .55f, 1f));
    } else {
      drawEntity(
          map,
          originX,
          originY,
          world.userX(),
          world.userY(),
          world.userName(),
          new Color(1f, .85f, .35f, .9f));
    }
    batch.setColor(Color.WHITE);
    font.setColor(Color.WHITE);
    font.draw(
        batch,
        notice + "  |  Ô " + world.userX() + "," + world.userY() + "  |  Mũi tên để đi, Esc để rời",
        16,
        viewHeight - 16);
    batch.end();
  }

  private void readKeys() {
    if (input == null) return;
    if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)
        || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.D)) input.step(WorldState.EAST);
    else if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)
        || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.A)) input.step(WorldState.WEST);
    else if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)
        || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.W)) input.step(WorldState.NORTH);
    else if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)
        || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.S)) input.step(WorldState.SOUTH);
    else if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) input.leave();
  }

  private void drawEntity(
      WorldMap map, float originX, float originY, int cellX, int cellY, String name, Color color) {
    if (!map.holds(cellX, cellY)) return;
    float x = originX + MapView.cellX(cellX, map);
    float y = originY + MapView.cellY(cellY, map);
    batch.setColor(color);
    batch.draw(marker, x + 2, y + 2, map.cellWidth() - 4, map.cellHeight() * 1.6f);
    batch.setColor(Color.WHITE);
    drawName(map, originX, originY, cellX, cellY, name, color);
  }

  private void drawName(
      WorldMap map, float originX, float originY, int cellX, int cellY, String name, Color color) {
    if (name == null || name.isEmpty() || !map.holds(cellX, cellY)) {
      return;
    }
    float x = originX + MapView.cellX(cellX, map);
    float y = originY + MapView.cellY(cellY, map);
    font.setColor(color);
    font.draw(
        batch,
        Normalizer.normalize(name, Normalizer.Form.NFC),
        x - 12,
        y + map.cellHeight() * 1.6f + 18);
    font.setColor(Color.WHITE);
  }

  private String describe(WorldPackets.MapIdentity identity, WorldMap map) {
    StringBuilder text =
        new StringBuilder(Normalizer.normalize(identity.name(), Normalizer.Form.NFC));
    text.append(" (")
        .append(identity.mapId())
        .append(") ")
        .append(map.columns())
        .append('x')
        .append(map.rows());
    List<Integer> missing = renderer == null ? null : renderer.missingImages();
    if (missing != null && !missing.isEmpty())
      text.append("  thiếu ").append(missing.size()).append(" ảnh");
    return text.toString();
  }

  private void release() {
    renderer = null;
    if (body != null) {
      body.dispose();
      body = null;
    }
    bodyLook = 0;
    lastX = -1;
    lastY = -1;
    images = null;
    if (tiles != null) tiles.dispose();
    tiles = null;
  }

  @Override
  public void dispose() {
    release();
    batch.dispose();
  }
}
