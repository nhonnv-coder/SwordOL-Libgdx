package vn.swordol.client.ui.legacy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;

/**
 * The widget styles every native screen shares. Built in code from one raster font and a single
 * white pixel, so no style file has to ship; the original's own artwork replaces these surfaces per
 * control wherever a layout names an image.
 */
public final class LegacySkin {
  private LegacySkin() {}

  public static Skin create() {
    Skin result = new Skin();
    BitmapFont font = new BitmapFont(Gdx.files.classpath("fonts/native-ui.fnt"));
    result.add("font", font);
    Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    white.setColor(Color.WHITE);
    white.fill();
    result.add("white", new Texture(white));
    white.dispose();
    result.add("default", new Label.LabelStyle(font, new Color(.85f, .9f, .96f, 1)));
    TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
    button.font = font;
    button.fontColor = Color.WHITE;
    button.disabledFontColor = Color.GRAY;
    button.up = result.newDrawable("white", new Color(.14f, .27f, .37f, 1));
    button.down = result.newDrawable("white", new Color(.24f, .44f, .55f, 1));
    button.disabled = result.newDrawable("white", new Color(.1f, .14f, .18f, 1));
    result.add("default", button);
    TextField.TextFieldStyle field = new TextField.TextFieldStyle();
    field.font = font;
    field.fontColor = Color.WHITE;
    field.background = result.newDrawable("white", new Color(.09f, .14f, .2f, 1));
    field.cursor = result.newDrawable("white", Color.CYAN);
    field.selection = result.newDrawable("white", Color.DARK_GRAY);
    result.add("default", field);
    List.ListStyle list = new List.ListStyle();
    list.font = font;
    list.fontColorSelected = Color.WHITE;
    list.fontColorUnselected = Color.LIGHT_GRAY;
    list.selection = button.down;
    ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
    scroll.background = field.background;
    SelectBox.SelectBoxStyle select =
        new SelectBox.SelectBoxStyle(font, Color.WHITE, field.background, scroll, list);
    result.add("default", select);
    return result;
  }
}
