package vn.swordol.editor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import vn.swordol.assets.pipeline.MapDocument;

/**
 * One open map with its bounded undo/redo history. A command that fails changes nothing: the
 * document, the history and the dirty flag stay as they were.
 *
 * <p>Not thread safe; the editor applies commands on its UI thread.
 */
public final class MapEditSession {
  static final int MAX_HISTORY = 200;
  private final String assetId;
  private final MapDocument document;
  private final Deque<EditorCommand> undo = new ArrayDeque<>();
  private final Deque<EditorCommand> redo = new ArrayDeque<>();
  private boolean dirty;

  MapEditSession(String assetId, MapDocument document) {
    this.assetId = assetId;
    this.document = document;
  }

  public String assetId() {
    return assetId;
  }

  public MapDocument document() {
    return document;
  }

  public void apply(EditorCommand command) throws IOException {
    command.apply(document);
    redo.clear();
    undo.push(command);
    if (undo.size() > MAX_HISTORY) {
      undo.removeLast();
    }
    dirty = true;
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public void undo() throws IOException {
    if (undo.isEmpty()) {
      throw new IOException("Nothing to undo");
    }
    EditorCommand command = undo.peek();
    command.revert(document);
    undo.pop();
    redo.push(command);
    dirty = true;
  }

  public void redo() throws IOException {
    if (redo.isEmpty()) {
      throw new IOException("Nothing to redo");
    }
    EditorCommand command = redo.peek();
    command.apply(document);
    redo.pop();
    undo.push(command);
    dirty = true;
  }

  /** True when the document differs from the last saved state, conservatively. */
  public boolean isDirty() {
    return dirty;
  }

  /** Describes the next undo step, or null when there is none. */
  public String undoDescription() {
    return undo.isEmpty() ? null : undo.peek().describe();
  }

  /** Describes the next redo step, or null when there is none. */
  public String redoDescription() {
    return redo.isEmpty() ? null : redo.peek().describe();
  }

  void markSaved() {
    dirty = false;
  }
}
