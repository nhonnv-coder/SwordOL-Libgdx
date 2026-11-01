package vn.swordol.client.ui.legacy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.swordol.assets.runtime.UiLayout;

/**
 * One original window placed the way the client places it: every control sits at the coordinates
 * its own {@code .ui} layout records, inside the 640x360 surface those layouts are drawn on. This
 * is the Scene2D counterpart of {@code CtrlManager} opening a {@code CustomScreen}; a screen above
 * binds behaviour by the control ids the original uses, and invents no positions of its own.
 *
 * <p>Layout coordinates grow downwards from the top left, Scene2D's grow upwards from the bottom
 * left, so only the y axis is converted. Nothing is scaled here: the stage this group lives on
 * carries a viewport of the same 640x360, so one layout unit stays one stage unit.
 *
 * <p>Controls of a class this build does not draw yet keep their box and stay empty rather than
 * disappearing, so a window never silently loses a control and its focus order stays honest.
 */
public final class LegacyWindow extends Group {
  private final UiLayout layout;
  private final Map<Integer, Actor> actors = new LinkedHashMap<>();

  public LegacyWindow(UiLayout layout, Skin skin) {
    this.layout = layout;
    setSize(layout.width(), layout.height());
    for (UiLayout.Control control : layout.controls()) {
      Actor actor = build(control, skin);
      if (actor == null) continue;
      actor.setBounds(
          control.x(),
          LegacyPlacement.stageY(layout.height(), control.y(), control.height()),
          control.width(),
          control.height());
      actors.put(control.id(), actor);
      addActor(actor);
    }
  }

  public UiLayout layout() {
    return layout;
  }

  /** The actor placed for this original control id, or null when the window holds no such one. */
  public Actor actor(int controlId) {
    return actors.get(controlId);
  }

  public TextField field(int controlId) {
    Actor actor = actors.get(controlId);
    return actor instanceof TextField ? (TextField) actor : null;
  }

  public TextButton button(int controlId) {
    Actor actor = actors.get(controlId);
    return actor instanceof TextButton ? (TextButton) actor : null;
  }

  public Label label(int controlId) {
    Actor actor = actors.get(controlId);
    return actor instanceof Label ? (Label) actor : null;
  }

  private static Actor build(UiLayout.Control control, Skin skin) {
    switch (control.kind()) {
      case BUTTON:
        TextButton button = new TextButton(control.text(), skin);
        button.getLabel().setColor(colour(control.textColor()));
        return button;
      case EDIT:
        TextField field = new TextField(control.text(), skin);
        if (control.charLimit() > 0) field.setMaxLength(control.charLimit());
        return field;
      case TEXT:
        Label label = new Label(control.text(), skin);
        label.setWrap(true);
        label.setColor(colour(control.textColor()));
        return label;
      default:
        // A list or a grid: its box is kept by the window, but this build draws no contents yet.
        return new Group();
    }
  }

  /** The layout stores a packed 0xRRGGBB; black is a real choice there, not a missing value. */
  private static Color colour(int packed) {
    return new Color(
        ((packed >> 16) & 0xFF) / 255f, ((packed >> 8) & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
  }
}
