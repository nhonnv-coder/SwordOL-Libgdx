package vn.swordol.client.modules.login;

import vn.swordol.protocol.wire.LoginPackets;

public interface LoginView {
  interface Input {
    void loadServers(String endpoint);

    void login(LoginPackets.ServerLine server, String account, char[] password);

    /** Registers an account on the endpoint already loaded; it does not sign in. */
    void register(String account, char[] password, String third);

    /**
     * Creates a character the way the original creation screen does.
     *
     * @param sex {@link vn.swordol.protocol.wire.RoleLooks#MALE} or {@code FEMALE}
     * @param profession 1 to {@link vn.swordol.protocol.wire.RoleLooks#PROFESSIONS}
     */
    void createRole(String name, int sex, int profession, int slot);

    void enter(int roleId);

    void disconnect();
  }

  void setInput(Input input);

  void showSession(GameSession.Snapshot snapshot);

  void showInputError(String message);
}
