package vn.swordol.client.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import vn.swordol.protocol.wire.LoginPackets;

/**
 * The registration window of the original, CTRL_REGISTER_WND, as one Scene2D widget. Its three
 * boxes and their rules come from the decoded layout of window 114: "Tài khoản:", "Mật khẩu:" and
 * "Trùng lặp:", each capped at thirteen characters, with the window's own note saying six to
 * thirteen. The repeated password is sent rather than compared here, as the original sends it.
 *
 * <p>It collects input and reports it. It owns no session state, keeps no password after reporting
 * one, and never signs in: the login fields above are still where a new account is used.
 *
 * <p>shortcut: laid out as a form, not at the original's own coordinates, when to upgrade: the
 * screens are driven by the decoded .ui layouts.
 */
public final class RegisterForm extends Table {
  /** What the form reports when the player confirms; the password array becomes the caller's. */
  public interface Listener {
    void register(String account, char[] password, String repeated);
  }

  private final Skin skin;
  private final TextField account;
  private final TextField password;
  private final TextField repeated;
  private final TextButton submit;
  private final Label notice;
  private Listener listener;

  public RegisterForm(Skin skin) {
    this.skin = skin;
    this.account = new TextField("", skin);
    this.password = new TextField("", skin);
    this.repeated = new TextField("", skin);
    this.submit = new TextButton("Đăng ký tài khoản", skin);
    this.notice = new Label("", skin);

    password.setPasswordMode(true);
    password.setPasswordCharacter('*');
    repeated.setPasswordMode(true);
    repeated.setPasswordCharacter('*');
    String hint =
        LoginPackets.REGISTER_FIELD_MINIMUM + "~" + LoginPackets.REGISTER_FIELD_LIMIT + " ký tự";
    for (TextField box : new TextField[] {account, password, repeated}) {
      box.setMaxLength(LoginPackets.REGISTER_FIELD_LIMIT);
      box.setMessageText(hint);
    }
    submit.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            send();
          }
        });
    notice.setWrap(true);

    row("Tài khoản:", account);
    row("Mật khẩu:", password);
    row("Trùng lặp:", repeated);
    add(submit).colspan(2).left().width(260).height(40).padBottom(8).row();
    add(notice).colspan(2).growX().height(34).row();
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void setEnabled(boolean enabled) {
    account.setDisabled(!enabled);
    password.setDisabled(!enabled);
    repeated.setDisabled(!enabled);
    submit.setDisabled(!enabled);
  }

  /** Clears the two secret boxes; the screen calls this once a registration has been reported. */
  public void clearSecrets() {
    password.setText("");
    repeated.setText("");
  }

  private void send() {
    if (listener == null || submit.isDisabled()) return;
    String name = account.getText().trim();
    String secret = password.getText();
    String again = repeated.getText();
    if (name.length() < LoginPackets.REGISTER_FIELD_MINIMUM
        || secret.length() < LoginPackets.REGISTER_FIELD_MINIMUM
        || again.length() < LoginPackets.REGISTER_FIELD_MINIMUM) {
      notice.setText(
          "Mỗi ô cần "
              + LoginPackets.REGISTER_FIELD_MINIMUM
              + "~"
              + LoginPackets.REGISTER_FIELD_LIMIT
              + " ký tự.");
      return;
    }
    notice.setText("");
    char[] owned = secret.toCharArray();
    clearSecrets();
    listener.register(name, owned, again);
  }

  private void row(String title, Actor input) {
    add(new Label(title, skin)).width(165).left().padBottom(10);
    add(input).growX().height(38).padBottom(10).row();
  }
}
