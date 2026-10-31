package vn.swordol.client;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import vn.swordol.client.application.GameFacade;
import vn.swordol.client.application.GameView;
import vn.swordol.client.render.MapCatalog;
import vn.swordol.client.render.MapRenderer;
import vn.swordol.client.render.TileSheets;
import vn.swordol.client.render.WorldSync;

/** Scene2D presentation of the independent local demo. No original game rules are implied. */
public final class SwordGame extends ApplicationAdapter implements GameView {
  private Stage stage;
  private Skin skin;
  private GameFacade facade;
  private Input actions;
  private Label status, stats;
  private TextField name;
  private MapActor map;
  private JsonObject state;
  private String failed;
  private MapCatalog catalog;
  private WorldSync world;
  private TileSheets sheets;
  private int frames;

  @Override
  public void create() {
    stage = new Stage(new FitViewport(960, 640));
    Gdx.input.setInputProcessor(stage);
    skin = new Skin();
    BitmapFont font = new BitmapFont();
    skin.add("default-font", font);
    Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pix.setColor(Color.WHITE);
    pix.fill();
    skin.add("white", new Texture(pix));
    pix.dispose();
    skin.add("default", new Label.LabelStyle(font, new Color(.86f, .91f, .96f, 1)));
    TextButton.TextButtonStyle bs = new TextButton.TextButtonStyle();
    bs.font = font;
    bs.fontColor = Color.WHITE;
    bs.up = skin.newDrawable("white", new Color(.16f, .29f, .38f, 1));
    bs.down = skin.newDrawable("white", new Color(.24f, .46f, .51f, 1));
    skin.add("default", bs);
    TextField.TextFieldStyle ts = new TextField.TextFieldStyle();
    ts.font = font;
    ts.fontColor = Color.WHITE;
    ts.background = skin.newDrawable("white", new Color(.1f, .15f, .21f, 1));
    ts.cursor = skin.newDrawable("white", Color.CYAN);
    ts.selection = skin.newDrawable("white", Color.DARK_GRAY);
    skin.add("default", ts);
    Table root = new Table();
    root.setFillParent(true);
    root.pad(24);
    stage.addActor(root);
    catalog = openCatalog();
    world = new WorldSync(catalog);
    skin.add("original-logo", loadLogo());
    Table heading = new Table();
    heading.add(new Image(skin.get("original-logo", Texture.class))).size(40).padRight(12);
    heading.add(new Label("SWORDOL  /  LOCAL DEMO", skin));
    root.add(heading).left().expandX();
    root.row();
    root.add(new Label("A reconstructed foundation · demo world and combat rules", skin))
        .left()
        .padTop(8);
    root.row();
    Table login = new Table();
    name = new TextField("Hero", skin);
    name.setMessageText("Demo name");
    name.setMaxLength(24);
    login.add(new Label("Name ", skin));
    login.add(name).width(190).height(36).padRight(10);
    TextButton connect = new TextButton("Connect", skin);
    login.add(connect).width(120).height(36).padRight(10);
    TextButton disconnect = new TextButton("Disconnect", skin);
    login.add(disconnect).width(120).height(36);
    root.add(login).left().padTop(18);
    root.row();
    stats = new Label("Enter a name and connect to your local server.", skin);
    root.add(stats).left().padTop(14).padBottom(12);
    root.row();
    map = new MapActor();
    root.add(map).width(880).height(370).left();
    root.row();
    Table footer = new Table();
    TextButton attack = new TextButton("Attack monster", skin);
    footer.add(attack).width(160).height(38).padRight(20);
    footer.add(
        new Label(
            "Blue: you     Red: monster     Dark cells are blocked     Click / tap tiles to move",
            skin));
    root.add(footer).left().padTop(12);
    root.row();
    status = new Label("Server: localhost:7777", skin);
    status.setWrap(true);
    root.add(status).width(880).left().padTop(12);
    connect.addListener(
        new ChangeListener() {
          public void changed(ChangeEvent e, Actor a) {
            if (actions != null) {
              state = null;
              actions.connect(name.getText());
            }
          }
        });
    disconnect.addListener(
        new ChangeListener() {
          public void changed(ChangeEvent e, Actor a) {
            if (actions != null) {
              state = null;
              actions.disconnect();
            }
          }
        });
    attack.addListener(
        new ChangeListener() {
          public void changed(ChangeEvent e, Actor a) {
            if (actions != null && monster() != null)
              actions.attack(monster().get("id").getAsString());
          }
        });
    facade = GameFacade.open(r -> Gdx.app.postRunnable(r));
    facade.attach(this);
    if (Boolean.getBoolean("swordol.autoConnect")) actions.connect("Hero");
  }

  private MapCatalog openCatalog() {
    try (InputStream input = Gdx.files.classpath("bundles/demo.swb").read()) {
      return MapCatalog.load(input);
    } catch (IOException ex) {
      throw new GdxRuntimeException("Cannot load demo asset bundle: " + ex.getMessage(), ex);
    }
  }

  private Texture loadLogo() {
    try {
      byte[] bytes = catalog.image("brand.logo");
      Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
      try {
        return new Texture(pixels);
      } finally {
        pixels.dispose();
      }
    } catch (IOException ex) {
      throw new GdxRuntimeException("Cannot load the logo asset: " + ex.getMessage(), ex);
    }
  }

  /**
   * Loads the map the server says it is simulating, refusing a version this client did not build.
   * Runs on the render thread, where creating textures is allowed.
   */
  private void syncWorld(JsonObject area) {
    boolean changed = world.sync(area);
    if (!world.ready()) {
      dropSheets();
      return;
    }
    if (!changed && sheets != null) return;
    try {
      TileSheets tiles = TileSheets.load(world.map(), assetId -> catalog.image(assetId));
      dropSheets();
      sheets = tiles;
    } catch (IOException | GdxRuntimeException ex) {
      dropSheets();
      failed = "Cannot draw " + world.map().id() + ": " + message(ex);
    }
  }

  private void dropSheets() {
    if (sheets != null) {
      sheets.dispose();
      sheets = null;
    }
  }

  private static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  /** The world is drawable only when the map and its tile images both loaded. */
  private boolean drawable() {
    return world != null && world.ready() && sheets != null && failed == null;
  }

  private String refusal() {
    if (failed != null) {
      return failed;
    }
    return world == null ? null : world.error();
  }

  @Override
  public void setInput(Input input) {
    actions = input;
  }

  @Override
  public void showState(JsonObject next) {
    state = next;
    failed = null;
    syncWorld(next.has("map") ? next.getAsJsonObject("map") : null);
    JsonObject p = next.getAsJsonObject("player");
    JsonObject m = monster();
    stats.setText(
        p.get("name").getAsString()
            + "     HP "
            + p.get("hp").getAsInt()
            + "     Gold "
            + p.get("gold").getAsInt()
            + "     Map "
            + (drawable() ? world.map().id() : "unavailable")
            + (m == null ? "     No monster here" : "     Monster HP " + m.get("hp").getAsInt()));
  }

  /** The monster of the current map, or null when this map has none. */
  private JsonObject monster() {
    return state != null && state.has("monster") && state.get("monster").isJsonObject()
        ? state.getAsJsonObject("monster")
        : null;
  }

  @Override
  public void showStatus(String text) {
    String refusal = refusal();
    status.setText(refusal == null ? text : refusal);
  }

  @Override
  public void render() {
    ScreenUtils.clear(.035f, .055f, .085f, 1);
    stage.act(Math.min(Gdx.graphics.getDeltaTime(), .1f));
    stage.draw();
    int limit = Integer.getInteger("swordol.smokeFrames", 0);
    if (limit > 0 && ++frames >= limit) {
      String output = System.getProperty("swordol.screenshot");
      if (output != null) {
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        Pixmap screenshot = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        screenshot.getPixels().put(pixels).position(0);
        PixmapIO.writePNG(Gdx.files.absolute(output), screenshot);
        screenshot.dispose();
      }
      Gdx.app.exit();
    }
  }

  @Override
  public void resize(int width, int height) {
    stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    if (facade != null) facade.close();
    dropSheets();
    if (stage != null) stage.dispose();
    if (skin != null) skin.dispose();
  }

  private final class MapActor extends Actor {
    MapActor() {
      addListener(
          new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
              if (!drawable()) {
                showStatus(null);
                return true;
              }
              if (state != null && actions != null) {
                JsonObject m = state.getAsJsonObject("map");
                int w = m.get("width").getAsInt(), h = m.get("height").getAsInt();
                actions.move(
                    Math.min(w - 1, (int) (x / getWidth() * w)),
                    Math.min(h - 1, (int) (y / getHeight() * h)));
              }
              return true;
            }
          });
    }

    @Override
    public void draw(Batch batch, float alpha) {
      Texture white = skin.get("white", Texture.class);
      int w = 20, h = 15;
      if (state != null) {
        w = state.getAsJsonObject("map").get("width").getAsInt();
        h = state.getAsJsonObject("map").get("height").getAsInt();
      }
      float cw = getWidth() / w, ch = getHeight() / h;
      if (drawable() && world.map().width() == w && world.map().height() == h) {
        MapRenderer.draw(batch, world.map(), sheets, getX(), getY(), cw, ch);
        for (int y = 0; y < h; y++)
          for (int x = 0; x < w; x++)
            if (world.map().isBlocked(x, y)) {
              batch.setColor(0, 0, 0, .35f);
              batch.draw(white, getX() + x * cw, getY() + y * ch, cw, ch);
            }
      } else {
        boolean refused = refusal() != null;
        for (int y = 0; y < h; y++)
          for (int x = 0; x < w; x++) {
            if (refused) {
              batch.setColor(
                  (x + y) % 2 == 0
                      ? new Color(.17f, .09f, .10f, 1)
                      : new Color(.13f, .07f, .08f, 1));
            } else {
              batch.setColor(
                  (x + y) % 2 == 0
                      ? new Color(.10f, .19f, .22f, 1)
                      : new Color(.085f, .16f, .19f, 1));
            }
            batch.draw(white, getX() + x * cw, getY() + y * ch, cw - 1, ch - 1);
          }
      }
      if (state != null && drawable()) {
        drawUnit(
            batch, white, state.getAsJsonObject("player"), cw, ch, new Color(.25f, .72f, 1, 1));
        JsonObject monster = monster();
        if (monster != null && monster.get("hp").getAsInt() > 0)
          drawUnit(batch, white, monster, cw, ch, new Color(.95f, .32f, .29f, 1));
      }
      batch.setColor(Color.WHITE);
    }

    private void drawUnit(
        Batch batch, Texture white, JsonObject unit, float cw, float ch, Color color) {
      batch.setColor(color);
      batch.draw(
          white,
          getX() + unit.get("x").getAsInt() * cw + 4,
          getY() + unit.get("y").getAsInt() * ch + 4,
          cw - 8,
          ch - 8);
    }
  }
}
