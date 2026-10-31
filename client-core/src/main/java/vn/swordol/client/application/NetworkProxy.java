package vn.swordol.client.application;

import com.google.gson.JsonObject;
import java.util.function.Consumer;
import org.puremvc.java.patterns.proxy.Proxy;
import vn.swordol.client.network.JsonConnection;

/** Adapts the local JSON transport to PureMVC notifications, without owning socket code. */
final class NetworkProxy extends Proxy {
  static final String NAME = "game.network";
  private final JsonConnection connection;

  NetworkProxy(Consumer<Runnable> render) {
    super(NAME);
    connection =
        new JsonConnection(
            render,
            response -> sendNotification(GameFacade.RESPONSE, response),
            status -> sendNotification(GameFacade.STATUS, status));
  }

  void connect(String host, int port) {
    connection.connect(host, port);
  }

  void request(String type, JsonObject payload) {
    connection.request(type, payload);
  }

  void disconnect() {
    connection.disconnect();
  }

  @Override
  public void onRemove() {
    connection.close();
  }
}
