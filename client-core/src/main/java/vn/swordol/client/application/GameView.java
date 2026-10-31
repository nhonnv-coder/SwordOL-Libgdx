package vn.swordol.client.application;

import com.google.gson.JsonObject;

public interface GameView {
  interface Input {
    void connect(String name);

    void move(int x, int y);

    void attack(String target);

    void disconnect();
  }

  default void setInput(Input input) {}

  void showState(JsonObject state);

  void showStatus(String status);
}
