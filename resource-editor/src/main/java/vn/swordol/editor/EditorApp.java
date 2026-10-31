package vn.swordol.editor;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import vn.swordol.assets.pipeline.MapDocument;
import vn.swordol.assets.runtime.MapData;
import vn.swordol.client.render.MapRenderer;
import vn.swordol.client.render.TileSheets;

/**
 * The map editor window: project browser, canvas and inspector. The window is a shell over {@link
 * EditorController}; it owns no editing rules of its own. The preview is produced by parsing the
 * document with the runtime reader and drawing it with the client renderer, so the canvas cannot
 * drift from what the game shows.
 */
public final class EditorApp extends ApplicationAdapter {
  private final Path projectRoot;
  private final String openAtStart;
  private Stage stage;
  private Skin skin;
  private EditorController controller;
  private TileSheets sheets;
  private MapData preview;
  private Label status;
  private Label summary;
  private Table maps;
  private Table layers;
  private Table palette;
  private TextField newMapId;
  private TextField newMapWidth;
  private TextField newMapHeight;
  private TextField layerName;
  private TextField spawnFields;
  private TextField portalFields;
  private int frames;

  public EditorApp(Path projectRoot, String openAtStart) {
    this.projectRoot = projectRoot;
    this.openAtStart = openAtStart;
  }

  @Override
  public void create() {
    stage = new Stage(new ScreenViewport());
    Gdx.input.setInputProcessor(stage);
    skin = buildSkin();
    Table root = new Table();
    root.setFillParent(true);
    root.pad(12);
    stage.addActor(root);
    root.add(toolbar()).colspan(3).left().expandX().fillX();
    root.row().padTop(10);
    ScrollPane browser = new ScrollPane(mapBrowser(), skin);
    browser.setFadeScrollBars(false);
    root.add(browser).width(230).top().fillY();
    root.add(new MapCanvas()).expand().fill().pad(0, 12, 0, 12);
    ScrollPane inspector = new ScrollPane(inspector(), skin);
    inspector.setFadeScrollBars(false);
    root.add(inspector).width(320).top().fillY();
    root.row().padTop(10);
    status = new Label("", skin);
    status.setWrap(true);
    root.add(status).colspan(3).left().fillX();
    try {
      controller = new EditorController(EditorProject.open(projectRoot));
      report("Project " + projectRoot + " opened with " + controller.maps().size() + " maps.");
      String first = openAtStart != null ? openAtStart : null;
      if (first == null && !controller.maps().isEmpty()) {
        first = controller.maps().get(0);
      }
      if (first != null) {
        open(first);
      }
    } catch (IOException | RuntimeException failure) {
      report("Cannot open project: " + message(failure));
    }
    refreshLists();
  }

  private Skin buildSkin() {
    Skin built = new Skin();
    BitmapFont font = new BitmapFont();
    built.add("default-font", font);
    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixel.setColor(Color.WHITE);
    pixel.fill();
    built.add("white", new Texture(pixel));
    pixel.dispose();
    built.add("default", new Label.LabelStyle(font, new Color(.88f, .92f, .96f, 1)));
    TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
    button.font = font;
    button.fontColor = Color.WHITE;
    button.up = built.newDrawable("white", new Color(.17f, .27f, .36f, 1));
    button.down = built.newDrawable("white", new Color(.26f, .46f, .55f, 1));
    button.checked = built.newDrawable("white", new Color(.33f, .55f, .42f, 1));
    built.add("default", button);
    TextField.TextFieldStyle input = new TextField.TextFieldStyle();
    input.font = font;
    input.fontColor = Color.WHITE;
    input.background = built.newDrawable("white", new Color(.09f, .13f, .18f, 1));
    input.cursor = built.newDrawable("white", Color.CYAN);
    input.selection = built.newDrawable("white", Color.DARK_GRAY);
    built.add("default", input);
    ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
    scroll.background = built.newDrawable("white", new Color(.06f, .09f, .13f, 1));
    built.add("default", scroll);
    return built;
  }

  private Table toolbar() {
    Table bar = new Table();
    bar.add(new Label("SWORDOL  MAP EDITOR", skin)).padRight(24);
    bar.add(button("Save", () -> controller.save())).width(90).height(32).padRight(8);
    bar.add(button("Undo", () -> controller.undo())).width(80).height(32).padRight(8);
    bar.add(button("Redo", () -> controller.redo())).width(80).height(32).padRight(24);
    summary = new Label("", skin);
    bar.add(summary).left().expandX();
    return bar;
  }

  private Table mapBrowser() {
    Table browser = new Table();
    browser.pad(8).top();
    browser.add(new Label("Maps", skin)).left();
    browser.row().padTop(6);
    maps = new Table();
    browser.add(maps).left().fillX();
    browser.row().padTop(14);
    browser.add(new Label("New map (id, width, height)", skin)).left();
    browser.row().padTop(4);
    newMapId = field("world.new", 200);
    browser.add(newMapId).left();
    browser.row().padTop(4);
    Table size = new Table();
    newMapWidth = field("20", 70);
    newMapHeight = field("15", 70);
    size.add(newMapWidth).padRight(6);
    size.add(newMapHeight).padRight(6);
    browser.add(size).left();
    browser.row().padTop(6);
    browser
        .add(
            button(
                "Create map",
                () -> {
                  controller.createMap(
                      newMapId.getText().trim(),
                      number(newMapWidth, "width"),
                      number(newMapHeight, "height"),
                      32,
                      32);
                  reload();
                }))
        .left()
        .width(200)
        .height(32);
    return browser;
  }

  private Table inspector() {
    Table panel = new Table();
    panel.pad(8).top();
    panel.add(new Label("Tool", skin)).left();
    panel.row().padTop(4);
    Table tools = new Table();
    for (final EditorController.Tool tool : EditorController.Tool.values()) {
      tools.add(button(tool.name(), () -> controller.selectTool(tool))).width(96).height(28).pad(2);
      if (tool.ordinal() % 3 == 2) {
        tools.row();
      }
    }
    panel.add(tools).left();
    panel.row().padTop(12);
    panel.add(new Label("Tile", skin)).left();
    panel.row().padTop(4);
    palette = new Table();
    panel.add(palette).left();
    panel.row().padTop(12);
    panel.add(new Label("Layers", skin)).left();
    panel.row().padTop(4);
    layers = new Table();
    panel.add(layers).left().fillX();
    panel.row().padTop(6);
    layerName = field("decor", 150);
    Table layerActions = new Table();
    layerActions.add(layerName).padRight(6);
    layerActions
        .add(button("Add", () -> controller.addLayer(layerName.getText().trim())))
        .width(70)
        .height(28);
    panel.add(layerActions).left();
    panel.row().padTop(4);
    Table order = new Table();
    order
        .add(button("Up", () -> controller.moveSelectedLayer(controller.selectedLayer() + 1)))
        .width(70)
        .height(28)
        .padRight(6);
    order
        .add(button("Down", () -> controller.moveSelectedLayer(controller.selectedLayer() - 1)))
        .width(70)
        .height(28)
        .padRight(6);
    order.add(button("Remove", () -> controller.removeSelectedLayer())).width(90).height(28);
    panel.add(order).left();
    panel.row().padTop(12);
    panel.add(new Label("Spawn: id kind template x y hp", skin)).left();
    panel.row().padTop(4);
    spawnFields = field("slime-1 monster slime 3 1 30", 300);
    panel.add(spawnFields).left();
    panel.row().padTop(4);
    Table spawnActions = new Table();
    spawnActions.add(button("Add spawn", () -> addSpawn())).width(140).height(28).padRight(6);
    spawnActions
        .add(button("Remove spawn", () -> controller.removeSpawn(word(spawnFields, 0, "spawn id"))))
        .width(150)
        .height(28);
    panel.add(spawnActions).left();
    panel.row().padTop(12);
    panel.add(new Label("Portal: id x y targetMap tx ty", skin)).left();
    panel.row().padTop(4);
    portalFields = field("to-town 19 7 world.town 1 1", 300);
    panel.add(portalFields).left();
    panel.row().padTop(4);
    Table portalActions = new Table();
    portalActions.add(button("Add portal", () -> addPortal())).width(140).height(28).padRight(6);
    portalActions
        .add(
            button(
                "Remove portal", () -> controller.removePortal(word(portalFields, 0, "portal id"))))
        .width(150)
        .height(28);
    panel.add(portalActions).left();
    return panel;
  }

  private void addSpawn() throws IOException {
    controller.addSpawn(
        word(spawnFields, 0, "spawn id"),
        word(spawnFields, 1, "kind"),
        word(spawnFields, 2, "template"),
        integer(spawnFields, 3, "x"),
        integer(spawnFields, 4, "y"),
        integer(spawnFields, 5, "hp"));
  }

  private void addPortal() throws IOException {
    controller.addPortal(
        word(portalFields, 0, "portal id"),
        integer(portalFields, 1, "x"),
        integer(portalFields, 2, "y"),
        word(portalFields, 3, "targetMap"),
        integer(portalFields, 4, "targetX"),
        integer(portalFields, 5, "targetY"));
  }

  private void refreshLists() {
    maps.clear();
    layers.clear();
    palette.clear();
    if (controller == null) {
      return;
    }
    for (final String assetId : controller.maps()) {
      maps.add(button(assetId, () -> open(assetId))).left().width(200).height(28).pad(2);
      maps.row();
    }
    if (!controller.hasMap()) {
      summary.setText("No map open");
      return;
    }
    MapDocument map = controller.session().document();
    for (int index = 0; index < map.layerCount(); index++) {
      final int layer = index;
      String mark = index == controller.selectedLayer() ? "> " : "   ";
      layers
          .add(
              button(
                  mark + index + "  " + map.layerName(index), () -> controller.selectLayer(layer)))
          .left()
          .width(280)
          .height(28)
          .pad(2);
      layers.row();
    }
    for (int gid = 0; gid <= map.totalTiles(); gid++) {
      final int tile = gid;
      palette
          .add(
              button(
                  gid == controller.selectedTile() ? "[" + gid + "]" : " " + gid + " ",
                  () -> controller.selectTile(tile)))
          .width(48)
          .height(28)
          .pad(2);
      if (gid % 5 == 4) {
        palette.row();
      }
    }
    summary.setText(
        controller.session().assetId()
            + "   "
            + map.width()
            + "x"
            + map.height()
            + "   layer "
            + controller.selectedLayer()
            + " of "
            + map.layerCount()
            + "   tile "
            + controller.selectedTile()
            + "   tool "
            + controller.tool()
            + (controller.session().isDirty() ? "   UNSAVED" : "   saved"));
  }

  private void open(String assetId) throws IOException {
    controller.openMap(assetId);
    reloadPreview();
    report("Opened " + assetId);
  }

  private void reload() throws IOException {
    reloadPreview();
    report("Created " + controller.session().assetId());
  }

  /** Rebuilds the preview from the document bytes using the runtime reader and client renderer. */
  private void reloadPreview() throws IOException {
    // Clear first: a canvas left showing the previous map would send clicks meant for what is
    // drawn into the document that is actually open.
    preview = null;
    if (sheets != null) {
      sheets.dispose();
      sheets = null;
    }
    MapData parsed = MapData.parse(controller.session().document().toJsonBytes());
    TileSheets loaded = TileSheets.load(parsed, assetId -> controller.project().bytes(assetId));
    preview = parsed;
    sheets = loaded;
  }

  private TextButton button(String text, EditorAction action) {
    TextButton created = new TextButton(text, skin);
    created.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            if (controller == null) {
              report("No project is open");
              return;
            }
            try {
              action.run();
              if (controller.hasMap()) {
                reloadPreview();
              }
              report(text + " done");
            } catch (IOException | RuntimeException failure) {
              report(text + " failed: " + message(failure));
            }
            refreshLists();
          }
        });
    return created;
  }

  private TextField field(String text, float width) {
    TextField created = new TextField(text, skin);
    created.setWidth(width);
    return created;
  }

  private static String word(TextField field, int index, String what) throws IOException {
    String[] parts = field.getText().trim().split("\\s+");
    if (index >= parts.length || parts[index].isEmpty()) {
      throw new IOException("Missing value: " + what);
    }
    return parts[index];
  }

  private static int integer(TextField field, int index, String what) throws IOException {
    String value = word(field, index, what);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      throw new IOException("Expected an integer for " + what + ", got " + value, invalid);
    }
  }

  private static int number(TextField field, String what) throws IOException {
    try {
      return Integer.parseInt(field.getText().trim());
    } catch (NumberFormatException invalid) {
      throw new IOException("Expected an integer for " + what, invalid);
    }
  }

  private static String message(Throwable failure) {
    String text = failure.getMessage();
    return text == null ? failure.getClass().getSimpleName() : text;
  }

  private void report(String text) {
    if (status != null) {
      status.setText(text);
    }
  }

  @Override
  public void render() {
    ScreenUtils.clear(.04f, .06f, .09f, 1);
    stage.act(Math.min(Gdx.graphics.getDeltaTime(), .1f));
    stage.draw();
    int limit = Integer.getInteger("swordol.editor.smokeFrames", 0);
    if (limit > 0 && ++frames >= limit) {
      String output = System.getProperty("swordol.editor.screenshot");
      if (output != null) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        Pixmap screenshot = new Pixmap(width, height, Pixmap.Format.RGBA8888);
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
    if (sheets != null) {
      sheets.dispose();
    }
    if (stage != null) {
      stage.dispose();
    }
    if (skin != null) {
      skin.dispose();
    }
  }

  /** The default project when no {@code swordol.editor.project} is given. */
  public static Path defaultProject() {
    return Paths.get(System.getProperty("swordol.editor.project", "assets/source/demo"));
  }

  /** An editor action that may reject the request. */
  private interface EditorAction {
    void run() throws IOException;
  }

  private final class MapCanvas extends Actor {
    MapCanvas() {
      addListener(
          new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
              if (preview == null || controller == null || !controller.hasMap()) {
                return true;
              }
              float cell = cellSize();
              int column = (int) Math.floor((x - offsetX(cell)) / cell);
              int row = (int) Math.floor((y - offsetY(cell)) / cell);
              try {
                controller.clickCell(column, row);
                reloadPreview();
                report("Cell " + column + "," + row + ": " + controller.tool());
              } catch (IOException | RuntimeException failure) {
                report("Cell " + column + "," + row + " rejected: " + message(failure));
              }
              refreshLists();
              return true;
            }
          });
    }

    private float cellSize() {
      return Math.max(4f, Math.min(getWidth() / preview.width(), getHeight() / preview.height()));
    }

    private float offsetX(float cell) {
      return (getWidth() - cell * preview.width()) / 2;
    }

    private float offsetY(float cell) {
      return (getHeight() - cell * preview.height()) / 2;
    }

    @Override
    public void draw(Batch batch, float alpha) {
      Texture white = skin.get("white", Texture.class);
      batch.setColor(.07f, .10f, .14f, 1);
      batch.draw(white, getX(), getY(), getWidth(), getHeight());
      if (preview == null) {
        batch.setColor(Color.WHITE);
        return;
      }
      float cell = cellSize();
      float originX = getX() + offsetX(cell);
      float originY = getY() + offsetY(cell);
      for (int y = 0; y < preview.height(); y++) {
        for (int x = 0; x < preview.width(); x++) {
          batch.setColor(
              (x + y) % 2 == 0 ? new Color(.12f, .16f, .20f, 1) : new Color(.10f, .14f, .18f, 1));
          batch.draw(white, originX + x * cell, originY + y * cell, cell - 1, cell - 1);
        }
      }
      batch.setColor(Color.WHITE);
      MapRenderer.draw(batch, preview, sheets, originX, originY, cell, cell);
      for (int y = 0; y < preview.height(); y++) {
        for (int x = 0; x < preview.width(); x++) {
          if (preview.isBlocked(x, y)) {
            batch.setColor(.92f, .26f, .24f, .45f);
            batch.draw(white, originX + x * cell, originY + y * cell, cell - 1, cell - 1);
          }
        }
      }
      for (MapData.Spawn spawn : preview.spawns()) {
        batch.setColor(.36f, .86f, .44f, .85f);
        batch.draw(
            white,
            originX + spawn.x() * cell + cell * .25f,
            originY + spawn.y() * cell + cell * .25f,
            cell * .5f,
            cell * .5f);
      }
      for (MapData.Portal portal : preview.portals()) {
        batch.setColor(.32f, .76f, .98f, .85f);
        batch.draw(
            white,
            originX + portal.x() * cell + cell * .15f,
            originY + portal.y() * cell + cell * .15f,
            cell * .7f,
            cell * .7f);
      }
      batch.setColor(Color.WHITE);
    }
  }
}
