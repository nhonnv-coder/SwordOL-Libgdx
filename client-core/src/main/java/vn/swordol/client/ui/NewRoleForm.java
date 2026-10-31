package vn.swordol.client.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import java.util.List;
import vn.swordol.protocol.wire.LoginPackets;
import vn.swordol.protocol.wire.RoleLooks;

/**
 * The creation window of the original, CTRL_NEWROLE_WND, as one Scene2D widget: a name, a
 * profession, a sex and the slot it goes into. It only collects input and reports it; it owns no
 * session state and talks to no network.
 *
 * <p>The rules it enforces are the screen's own. The name field stops at ten characters the way
 * setCharLimit(10) does, a profession whose whole row of newRoleLookFaceDatID is zero is not
 * offered at all, the sex list holds only what that profession has a look for, and a slot already
 * holding a character cannot be chosen.
 */
public final class NewRoleForm extends Table {
  /** What the form reports when the player confirms; the screen above decides what to do. */
  public interface Listener {
    void createRole(String name, int sex, int profession, int slot);
  }

  private final Skin skin;
  private final TextField name;
  private final SelectBox<Option> profession;
  private final SelectBox<Option> sex;
  private final SelectBox<Option> slot;
  private final TextButton create;
  private final Label notice;
  private Listener listener;

  public NewRoleForm(Skin skin) {
    this.skin = skin;
    this.name = new TextField("", skin);
    this.profession = new SelectBox<>(skin);
    this.sex = new SelectBox<>(skin);
    this.slot = new SelectBox<>(skin);
    this.create = new TextButton("Tạo nhân vật", skin);
    this.notice = new Label("", skin);

    name.setMaxLength(LoginPackets.ROLE_NAME_LIMIT);
    name.setMessageText("Tối đa " + LoginPackets.ROLE_NAME_LIMIT + " ký tự");
    profession.setItems(professionOptions());
    profession.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            refreshSexOptions();
          }
        });
    create.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            submit();
          }
        });
    notice.setWrap(true);

    row("Tên nhân vật", name);
    row("Môn phái", profession);
    row("Giới tính", sex);
    row("Ô nhân vật", slot);
    add(create).colspan(2).left().width(220).height(40).padBottom(8).row();
    add(notice).colspan(2).growX().height(34).row();
    refreshSexOptions();
    refreshSlots(java.util.Collections.<LoginPackets.Role>emptyList());
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /** Offers only the slots this account has left; disables the form when all three are taken. */
  public void refreshSlots(List<LoginPackets.Role> roles) {
    Array<Option> free = new Array<>();
    for (int index = 0; index < LoginPackets.ROLE_SLOTS; index++) {
      boolean taken = false;
      for (LoginPackets.Role role : roles) if (role.slot() == index) taken = true;
      if (!taken) free.add(new Option(index, "Ô " + (index + 1)));
    }
    slot.setItems(free);
    boolean room = free.size > 0;
    slot.setDisabled(!room);
    create.setDisabled(!room);
    notice.setText(room ? "" : "Đã dùng hết 3 ô nhân vật.");
  }

  public void setEnabled(boolean enabled) {
    name.setDisabled(!enabled);
    profession.setDisabled(!enabled);
    sex.setDisabled(!enabled);
    slot.setDisabled(!enabled || slot.getItems().size == 0);
    create.setDisabled(!enabled || slot.getItems().size == 0);
  }

  private void submit() {
    if (listener == null || create.isDisabled()) return;
    String wanted = name.getText().trim();
    if (wanted.isEmpty()) {
      notice.setText("Hãy nhập tên.");
      return;
    }
    Option chosenSlot = slot.getSelected(), chosenSex = sex.getSelected();
    Option chosenProfession = profession.getSelected();
    if (chosenSlot == null || chosenSex == null || chosenProfession == null) return;
    notice.setText("");
    listener.createRole(wanted, chosenSex.value, chosenProfession.value, chosenSlot.value);
  }

  /** The sex list follows the look table: a column of zero is a button the original deactivates. */
  private void refreshSexOptions() {
    Option chosen = profession.getSelected();
    Array<Option> options = new Array<>();
    if (chosen != null) {
      if (RoleLooks.offers(chosen.value, RoleLooks.MALE))
        options.add(new Option(RoleLooks.MALE, "Nam"));
      if (RoleLooks.offers(chosen.value, RoleLooks.FEMALE))
        options.add(new Option(RoleLooks.FEMALE, "Nữ"));
    }
    sex.setItems(options);
    sex.setDisabled(options.size < 2);
  }

  /**
   * The original shows no profession name of its own: NewRoleWnd_Flush asks the server for the
   * description with packet 1602 and shows whatever comes back, so this numbers them until that
   * request is implemented rather than inventing sect names.
   */
  private static Array<Option> professionOptions() {
    Array<Option> options = new Array<>();
    for (int index = 1; index <= RoleLooks.PROFESSIONS; index++) {
      if (RoleLooks.creatable(index)) options.add(new Option(index, "Môn phái " + index));
    }
    return options;
  }

  private void row(String title, Actor input) {
    add(new Label(title, skin)).width(165).left().padBottom(10);
    add(input).growX().height(38).padBottom(10).row();
  }

  /** A choice that shows a label and carries the number the protocol wants. */
  private static final class Option {
    private final int value;
    private final String label;

    Option(int value, String label) {
      this.value = value;
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
