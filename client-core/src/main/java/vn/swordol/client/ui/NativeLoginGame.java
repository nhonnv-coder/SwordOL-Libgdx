package vn.swordol.client.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import vn.swordol.client.modules.login.*;
import vn.swordol.client.ui.legacy.LegacySkin;
import vn.swordol.protocol.wire.LoginPackets;

/** Native Scene2D authentication/role UI; network is only initiated by explicit input. */
public final class NativeLoginGame extends ApplicationAdapter implements LoginView {
  private Stage stage;
  private Skin skin;
  private LoginFacade facade;
  private Input actions;
  private TextField endpoint, account, password;
  private SelectBox<LoginPackets.ServerLine> servers;
  private SelectBox<LoginPackets.Role> roles;
  private TextButton load, login, enter;
  private Label status, phase, map;
  private GameSession.Snapshot snapshot;
  private NativeWorldScreen worldScreen;
  private NewRoleForm newRole;
  private RegisterForm registerForm;
  private Table root;
  private int frames;

  @Override
  public void create() {
    stage = new Stage(new FitViewport(1060, 720));
    Gdx.input.setInputProcessor(stage);
    skin = LegacySkin.create();
    root = new Table();
    root.setFillParent(true);
    root.pad(32);
    stage.addActor(root);
    root.add(new Label("VÕ LÂM HỘI TỤ", skin)).left().colspan(2).row();
    root.add(new Label("Đăng nhập và chọn nhân vật", skin)).left().padBottom(22).colspan(2).row();
    endpoint =
        new TextField(
            System.getProperty("swordol.accountEndpoint", "http://127.0.0.1:7780/account"), skin);
    row(root, "Máy chủ tài khoản", endpoint);
    load = new TextButton("Tải danh sách máy chủ", skin);
    root.add(load).colspan(2).left().padBottom(14).row();
    servers = new SelectBox<>(skin);
    row(root, "Máy chủ / tuyến", servers);
    account = new TextField("", skin);
    account.setMaxLength(128);
    row(root, "Tài khoản", account);
    password = new TextField("", skin);
    password.setPasswordMode(true);
    password.setPasswordCharacter('*');
    password.setMaxLength(128);
    row(root, "Mật khẩu", password);
    login = new TextButton("Đăng nhập", skin);
    root.add(login).colspan(2).left().padBottom(20).row();
    roles = new SelectBox<>(skin);
    row(root, "Nhân vật", roles);
    registerForm = new RegisterForm(skin);
    registerForm.setListener((account, password, repeated) -> {
      if (actions != null) actions.register(account, password, repeated);
    });
    root.add(registerForm).colspan(2).growX().padBottom(14).row();
    newRole = new NewRoleForm(skin);
    newRole.setListener((name, sex, profession, slot) -> {
      if (actions != null) actions.createRole(name, sex, profession, slot);
    });
    root.add(newRole).colspan(2).growX().padBottom(14).row();
    Table buttons = new Table();
    enter = new TextButton("Vào game", skin);
    TextButton disconnect = new TextButton("Ngắt kết nối", skin);
    buttons.add(enter).width(180).height(40).padRight(12);
    buttons.add(disconnect).width(180).height(40);
    root.add(buttons).colspan(2).left().padBottom(20).row();
    phase = new Label("DISCONNECTED", skin);
    status = new Label("", skin);
    status.setWrap(true);
    map = new Label("", skin);
    map.setWrap(true);
    root.add(phase).colspan(2).left().row();
    root.add(status).colspan(2).growX().height(48).row();
    root.add(map).colspan(2).growX().height(52).row();
    root.add(new Label("Không lưu mật khẩu. Chỉ kết nối khi bạn bấm.", skin))
        .colspan(2)
        .left()
        .expandY()
        .bottom();
    load.addListener(
        change(() -> {
          if (actions != null) actions.loadServers(endpoint.getText());
        }));
    login.addListener(
        change(() -> {
          if (actions != null) {
            char[] secret = password.getText().toCharArray();
            password.setText("");
            actions.login(servers.getSelected(), account.getText(), secret);
          }
        }));
    enter.addListener(
        change(() -> {
          if (actions != null && roles.getSelected() != null)
            actions.enter(roles.getSelected().id());
        }));
    disconnect.addListener(
        change(() -> {
          password.setText("");
          if (actions != null) actions.disconnect();
        }));
    facade = new LoginFacade(Gdx.app::postRunnable);
    facade.attach(this);
    worldScreen =
        new NativeWorldScreen(
            skin.getFont("font"),
            skin.get("white", Texture.class),
            System.getProperty("swordol.legacyAssets"));
    facade.attachWorld(worldScreen);
  }

  private void row(Table root, String title, Actor input) {
    root.add(new Label(title, skin)).width(165).left().padBottom(12);
    root.add(input).growX().height(38).padBottom(12).row();
  }

  private static ChangeListener change(Runnable action) {
    return new ChangeListener() {
      @Override
      public void changed(ChangeEvent e, Actor actor) {
        action.run();
      }
    };
  }

  @Override
  public void setInput(Input input) {
    actions = input;
  }

  @Override
  public void showSession(GameSession.Snapshot next) {
    boolean replace = snapshot == null || next.servers() != snapshot.servers();
    if (replace) {
      com.badlogic.gdx.utils.Array<LoginPackets.ServerLine> list =
          new com.badlogic.gdx.utils.Array<>();
      for (LoginPackets.ServerLine line : next.servers()) list.add(line);
      servers.setItems(list);
    }
    if (snapshot == null || next.roles() != snapshot.roles()) {
      com.badlogic.gdx.utils.Array<LoginPackets.Role> list = new com.badlogic.gdx.utils.Array<>();
      for (LoginPackets.Role role : next.roles()) list.add(role);
      roles.setItems(list);
      newRole.refreshSlots(next.roles());
    }
    snapshot = next;
    phase.setText(stageLabel(next.stage()));
    status.setText(next.status());
    boolean idle =
        next.stage() == GameSession.Stage.DISCONNECTED
            || next.stage() == GameSession.Stage.ERROR
            || next.stage() == GameSession.Stage.SERVERS_READY;
    load.setDisabled(!idle);
    endpoint.setDisabled(!idle);
    servers.setDisabled(!idle);
    account.setDisabled(!idle);
    password.setDisabled(!idle);
    login.setDisabled(!idle || next.servers().isEmpty());
    enter.setDisabled(next.stage() != GameSession.Stage.ROLES_READY || next.roles().isEmpty());
    roles.setDisabled(next.stage() != GameSession.Stage.ROLES_READY);
    newRole.setEnabled(next.stage() == GameSession.Stage.ROLES_READY);
    registerForm.setEnabled(next.stage() == GameSession.Stage.SERVERS_READY);
    map.setText(
        next.mapId() == 0
            ? ""
            : ("Bản đồ "
                + next.mapId()
                + ": "
                + java.text.Normalizer.normalize(next.mapName(), java.text.Normalizer.Form.NFC)
                + "\nĐã nhận thông tin bản đồ; phần thế giới chơi được đang được hoàn thiện."));
  }

  @Override
  public void showInputError(String message) {
    status.setText(message);
  }

  private static String stageLabel(GameSession.Stage stage) {
    switch (stage) {
      case DISCONNECTED:
        return "Chưa kết nối";
      case LOADING_SERVERS:
        return "Danh sách máy chủ";
      case SERVERS_READY:
        return "Chọn máy chủ";
      case REGISTERING:
        return "Đăng ký tài khoản";
      case AUTHENTICATING:
        return "Đăng nhập tài khoản";
      case CONNECTING_GAME:
        return "Kết nối game";
      case ROLES_READY:
        return "Chọn nhân vật";
      case ENTERING_WORLD:
        return "Vào game";
      case MAP_RECEIVED:
        return "Tải bản đồ";
      case IN_WORLD:
        return "Trong thế giới";
      default:
        return "Cần kiểm tra kết nối";
    }
  }

  @Override
  public void render() {
    ScreenUtils.clear(.025f, .04f, .065f, 1);
    boolean inWorld = worldScreen != null && worldScreen.isShowing();
    if (root != null) root.setVisible(!inWorld);
    if (inWorld) worldScreen.render();
    stage.act(Math.min(Gdx.graphics.getDeltaTime(), .1f));
    stage.draw();
    int limit = Integer.getInteger("swordol.smokeFrames", 0);
    if (limit > 0 && ++frames >= limit) {
      String file = System.getProperty("swordol.screenshot");
      if (file != null) saveScreenshot(file);
      Gdx.app.exit();
    }
  }

  public static void saveScreenshot(String file) {
    Pixmap pixels =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      PixmapIO.writePNG(Gdx.files.absolute(file), pixels, -1, true);
    } finally {
      pixels.dispose();
    }
  }

  @Override
  public void resize(int width, int height) {
    stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    if (facade != null) facade.close();
    if (worldScreen != null) worldScreen.dispose();
    if (stage != null) stage.dispose();
    if (skin != null) skin.dispose();
  }
}
