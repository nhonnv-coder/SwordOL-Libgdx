package vn.swordol.editor;

import java.io.IOException;
import vn.swordol.assets.pipeline.MapDocument;

/**
 * One reversible map edit. {@code apply} records what it replaced, so {@code revert} restores the
 * exact previous state and a redo re-applies the same change.
 */
public interface EditorCommand {
  String describe();

  void apply(MapDocument map) throws IOException;

  void revert(MapDocument map) throws IOException;
}
