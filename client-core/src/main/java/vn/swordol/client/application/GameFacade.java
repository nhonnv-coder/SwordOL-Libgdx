package vn.swordol.client.application;

import com.google.gson.JsonObject;
import java.util.function.Consumer;
import org.puremvc.java.interfaces.INotification;
import org.puremvc.java.patterns.command.SimpleCommand;
import org.puremvc.java.patterns.facade.Facade;

/** All public gameplay methods and notifications are called on the render thread. */
public final class GameFacade extends Facade implements AutoCloseable {
  static final String CONNECT = "game.connect",
      SEND = "game.send",
      RESPONSE = "game.response",
      STATE = "game.state",
      STATUS = "game.status";
  private NetworkProxy network;

  /** Reuses PureMVC's process singleton when a mobile Activity recreates its view. */
  public static synchronized GameFacade open(Consumer<Runnable> render) {
    GameFacade current = (GameFacade) Facade.getInstance(() -> new GameFacade(render));
    if (!current.hasProxy(NetworkProxy.NAME)) current.start(render);
    return current;
  }

  public GameFacade(Consumer<Runnable> render) {
    start(render);
  }

  private void start(Consumer<Runnable> render) {
    network = new NetworkProxy(render);
    registerProxy(network);
    registerCommand(
        CONNECT,
        () ->
            new SimpleCommand() {
              @Override
              public void execute(INotification n) {
                Login login = (Login) n.getBody();
                network.connect(login.host, login.port);
                JsonObject data = new JsonObject();
                data.addProperty("name", login.name);
                network.request("LOGIN", data);
              }
            });
    registerCommand(
        SEND,
        () ->
            new SimpleCommand() {
              @Override
              public void execute(INotification n) {
                Action a = (Action) n.getBody();
                network.request(a.type, a.payload);
              }
            });
    registerCommand(
        RESPONSE,
        () ->
            new SimpleCommand() {
              @Override
              public void execute(INotification n) {
                JsonObject response = (JsonObject) n.getBody();
                if (!response.get("ok").getAsBoolean()) {
                  JsonObject error = response.getAsJsonObject("error");
                  sendNotification(
                      STATUS,
                      error.get("code").getAsString() + ": " + error.get("message").getAsString());
                  return;
                }
                String type = response.get("type").getAsString();
                if ("LOGIN".equals(type)) {
                  sendNotification(STATUS, "Connected. Entering world...");
                  sendNotification(SEND, new Action("ENTER_WORLD", new JsonObject()));
                } else if (!"PING".equals(type)) {
                  sendNotification(STATE, response.getAsJsonObject("payload"));
                  sendNotification(
                      STATUS,
                      "Connected · Click a tile to move. Stand beside the monster to attack.");
                }
              }
            });
  }

  public void attach(GameView view) {
    detach();
    registerMediator(new GameMediator(view, network));
  }

  public void detach() {
    removeMediator(GameMediator.NAME);
  }

  public void connect(String host, int port, String name) {
    sendNotification(STATUS, "Connecting...");
    sendNotification(CONNECT, new Login(host, port, name));
  }

  public void move(int x, int y) {
    JsonObject p = new JsonObject();
    p.addProperty("x", x);
    p.addProperty("y", y);
    sendNotification(SEND, new Action("MOVE", p));
  }

  public void attack(String id) {
    JsonObject p = new JsonObject();
    p.addProperty("targetId", id);
    sendNotification(SEND, new Action("ATTACK", p));
  }

  public void disconnect() {
    network.disconnect();
    sendNotification(STATUS, "Disconnected");
  }

  @Override
  public void close() {
    detach();
    removeProxy(NetworkProxy.NAME);
    removeCommand(CONNECT);
    removeCommand(SEND);
    removeCommand(RESPONSE);
  }

  private static final class Login {
    final String host, name;
    final int port;

    Login(String h, int p, String n) {
      host = h;
      port = p;
      name = n;
    }
  }

  private static final class Action {
    final String type;
    final JsonObject payload;

    Action(String t, JsonObject p) {
      type = t;
      payload = p;
    }
  }
}
