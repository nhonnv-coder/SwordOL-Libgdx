package vn.swordol.client.application;

import com.google.gson.JsonObject;
import org.puremvc.java.interfaces.INotification;
import org.puremvc.java.patterns.mediator.Mediator;

final class GameMediator extends Mediator {
  static final String NAME = "game.screen";
  private final NetworkProxy network;

  GameMediator(GameView view, NetworkProxy network) {
    super(NAME, view);
    this.network = network;
  }

  @Override
  public void onRegister() {
    ((GameView) getViewComponent())
        .setInput(
            new GameView.Input() {
              public void connect(String name) {
                ((GameFacade) facade).connect("127.0.0.1", 7777, name);
              }

              public void move(int x, int y) {
                ((GameFacade) facade).move(x, y);
              }

              public void attack(String target) {
                ((GameFacade) facade).attack(target);
              }

              public void disconnect() {
                ((GameFacade) facade).disconnect();
              }
            });
  }

  @Override
  public String[] listNotificationInterests() {
    return new String[] {GameFacade.STATE, GameFacade.STATUS};
  }

  @Override
  public void handleNotification(INotification n) {
    GameView screen = (GameView) getViewComponent();
    if (screen == null) return;
    if (GameFacade.STATE.equals(n.getName())) screen.showState((JsonObject) n.getBody());
    else screen.showStatus((String) n.getBody());
  }

  @Override
  public void onRemove() {
    network.disconnect();
    ((GameView) getViewComponent()).setInput(null);
    setViewComponent(null);
  }
}
