package vn.swordol.client.modules.login;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import org.puremvc.java.interfaces.INotification;
import org.puremvc.java.patterns.command.SimpleCommand;
import org.puremvc.java.patterns.facade.Facade;
import org.puremvc.java.patterns.mediator.Mediator;
import org.puremvc.java.patterns.proxy.Proxy;
import vn.swordol.client.modules.world.WorldListener;
import vn.swordol.client.modules.world.WorldState;
import vn.swordol.client.modules.world.WorldView;
import vn.swordol.protocol.wire.LoginPackets;
import vn.swordol.protocol.wire.MapReferences;
import vn.swordol.protocol.wire.MapResource;
import vn.swordol.protocol.wire.WorldPackets;

/**
 * PureMVC composition for one native session: account login, role selection and the world that
 * follows on the same connection. One facade per session lifecycle; the login and the world are two
 * mediators and two proxies over it, rather than a second facade owning a second connection.
 */
public final class LoginFacade extends Facade implements AutoCloseable {
  private static final String STATE = "native.login.state", INTENT = "native.login.intent";
  private static final String PROXY = "native.login.network", MEDIATOR = "native.login.view";
  private static final String WORLD_PROXY = "native.world.state",
      WORLD_MEDIATOR = "native.world.view";
  private static final String WORLD_MAP = "native.world.map", WORLD_STATE = "native.world.changed";
  private static final String WORLD_GRID = "native.world.grid";
  private final LoginProxy network;
  private final WorldProxy worldState;

  public LoginFacade(Consumer<Runnable> render) {
    worldState = new WorldProxy();
    registerProxy(worldState);
    network = new LoginProxy(render);
    registerProxy(network);
    registerCommand(
        INTENT,
        () ->
            new SimpleCommand() {
              @Override
              public void execute(INotification notification) {
                ((Runnable) notification.getBody()).run();
              }
            });
  }

  public void attach(LoginView view) {
    detach();
    registerMediator(new LoginMediator(view));
    view.showSession(network.session.snapshot());
  }

  /** Attaches the world screen; it stays empty until the server sends a map. */
  public void attachWorld(WorldView view) {
    removeMediator(WORLD_MEDIATOR);
    registerMediator(new WorldMediator(view));
  }

  public void detach() {
    removeMediator(MEDIATOR);
    removeMediator(WORLD_MEDIATOR);
  }

  @Override
  public void close() {
    detach();
    removeCommand(INTENT);
    removeProxy(PROXY);
    removeProxy(WORLD_PROXY);
  }

  private final class WorldProxy extends Proxy {
    final WorldState state = new WorldState();

    WorldProxy() {
      super(WORLD_PROXY);
    }
  }

  private final class LoginProxy extends Proxy {
    final GameSession session;

    LoginProxy(Consumer<Runnable> render) {
      super(PROXY);
      session =
          new GameSession(
              render,
              state -> sendNotification(STATE, state),
              new WorldListener() {
                @Override
                public void mapLoaded(
                    WorldPackets.MapIdentity identity,
                    MapResource resource,
                    MapReferences references,
                    Map<Integer, byte[]> images) {
                  worldState.state.map(identity, resource);
                  sendNotification(
                      WORLD_MAP, new Object[] {identity, resource, references, images});
                  sendNotification(WORLD_STATE, worldState.state);
                }

                @Override
                public void mapUpdate(WorldPackets.MapUpdate update) {
                  worldState.state.mapUpdate(update);
                  if (update.action() != 0) sendNotification(WORLD_GRID, worldState.state);
                  sendNotification(WORLD_STATE, worldState.state);
                }

                @Override
                public void user(WorldPackets.UserInfo info) {
                  worldState.state.user(info);
                  sendNotification(WORLD_STATE, worldState.state);
                }

                @Override
                public void otherPlayer(WorldPackets.PlayerInfo info) {
                  worldState.state.otherPlayer(info);
                  sendNotification(WORLD_STATE, worldState.state);
                }

                @Override
                public void walk(WorldPackets.Walk walk) {
                  worldState.state.walk(walk);
                  sendNotification(WORLD_STATE, worldState.state);
                }
              });
    }

    @Override
    public void onRemove() {
      session.close();
    }
  }

  private final class LoginMediator extends Mediator {
    LoginMediator(LoginView view) {
      super(MEDIATOR, view);
    }

    @Override
    public void onRegister() {
      ((LoginView) getViewComponent())
          .setInput(
              new LoginView.Input() {
                private void intent(Runnable action) {
                  try {
                    sendNotification(INTENT, action);
                  } catch (IllegalArgumentException | IllegalStateException ex) {
                    LoginView view = (LoginView) getViewComponent();
                    if (view != null)
                      view.showInputError(
                          "Kiểm tra địa chỉ máy chủ, tuyến đã chọn và các ô còn thiếu.");
                  }
                }

                public void loadServers(String endpoint) {
                  intent(() -> network.session.loadServers(URI.create(endpoint.trim())));
                }

                public void login(LoginPackets.ServerLine server, String account, char[] password) {
                  try {
                    intent(() -> network.session.login(server, account, password));
                  } finally {
                    Arrays.fill(password, '\0');
                  }
                }

                public void createRole(String name, int sex, int profession, int slot) {
                  intent(() -> network.session.createRole(name, sex, profession, slot));
                }

                public void enter(int id) {
                  intent(() -> network.session.enter(id));
                }

                public void disconnect() {
                  intent(() -> {
                    worldState.state.leave();
                    network.session.disconnect();
                  });
                }
              });
    }

    @Override
    public String[] listNotificationInterests() {
      return new String[] {STATE};
    }

    @Override
    public void handleNotification(INotification n) {
      LoginView view = (LoginView) getViewComponent();
      if (view != null) view.showSession((GameSession.Snapshot) n.getBody());
    }

    @Override
    public void onRemove() {
      worldState.state.leave();
      network.session.disconnect();
      LoginView view = (LoginView) getViewComponent();
      if (view != null) view.setInput(null);
      setViewComponent(null);
    }
  }

  private final class WorldMediator extends Mediator {
    WorldMediator(WorldView view) {
      super(WORLD_MEDIATOR, view);
    }

    @Override
    public void onRegister() {
      ((WorldView) getViewComponent())
          .setWorldInput(
              new WorldView.Input() {
                /**
                 * Input can arrive a frame after the world ended; that is ignored, not thrown at
                 * the UI.
                 */
                private void intent(Runnable action) {
                  try {
                    sendNotification(INTENT, action);
                  } catch (IllegalArgumentException | IllegalStateException ignored) {
                  }
                }

                public void step(int direction) {
                  intent(() -> {
                    if (!worldState.state.step(direction)) return;
                    network.session.requestWalk(
                        direction, worldState.state.userX(), worldState.state.userY());
                    worldState.state.takeWalkRequest();
                    sendNotification(WORLD_STATE, worldState.state);
                  });
                }

                public void requestPlayer(int id) {
                  intent(() -> network.session.requestPlayer(id));
                }

                public void leave() {
                  intent(() -> {
                    worldState.state.leave();
                    network.session.disconnect();
                  });
                }
              });
    }

    @Override
    public String[] listNotificationInterests() {
      return new String[] {WORLD_MAP, WORLD_GRID, WORLD_STATE, STATE};
    }

    @Override
    public void handleNotification(INotification n) {
      WorldView view = (WorldView) getViewComponent();
      if (view == null) return;
      if (WORLD_MAP.equals(n.getName())) {
        Object[] body = (Object[]) n.getBody();
        view.showMap(
            (WorldPackets.MapIdentity) body[0],
            (MapResource) body[1],
            (MapReferences) body[2],
            (Map<Integer, byte[]>) body[3]);
        return;
      }
      if (WORLD_GRID.equals(n.getName())) {
        view.mapChanged();
        return;
      }
      if (STATE.equals(n.getName())) {
        // A dead session must not leave a world on screen that nothing updates any more.
        GameSession.Stage stage = ((GameSession.Snapshot) n.getBody()).stage();
        if (stage == GameSession.Stage.ERROR || stage == GameSession.Stage.DISCONNECTED) {
          worldState.state.leave();
          view.clearWorld();
        }
        return;
      }
      view.showWorld((WorldState) n.getBody());
    }

    @Override
    public void onRemove() {
      WorldView view = (WorldView) getViewComponent();
      if (view != null) {
        view.clearWorld();
        view.setWorldInput(null);
      }
      setViewComponent(null);
    }
  }
}
