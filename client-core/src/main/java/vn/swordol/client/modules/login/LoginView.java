package vn.swordol.client.modules.login;

import vn.swordol.protocol.wire.LoginPackets;

public interface LoginView {
  interface Input {
    void loadServers(String endpoint);

    void login(LoginPackets.ServerLine server, String account, char[] password);

    void enter(int roleId);

    void disconnect();
  }

  void setInput(Input input);

  void showSession(GameSession.Snapshot snapshot);

  void showInputError(String message);
}
