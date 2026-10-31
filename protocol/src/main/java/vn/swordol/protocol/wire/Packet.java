package vn.swordol.protocol.wire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An inbound logical packet. No field names or server rules are inferred from its opcode. */
public final class Packet {
  private final short opcode;
  private final List<Field> fields;

  Packet(short opcode, List<Field> fields) {
    this.opcode = opcode;
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
  }

  public short opcode() {
    return opcode;
  }

  public List<Field> fields() {
    return fields;
  }

  /** Keeps the original tag, including inbound tag5 which carries a four-byte integer. */
  public static final class Field {
    private final int tag;
    private final Object value;

    Field(int tag, Object value) {
      this.tag = tag;
      this.value = value instanceof byte[] ? ((byte[]) value).clone() : value;
    }

    public int tag() {
      return tag;
    }

    public int number() {
      if (!(value instanceof Integer)) throw new IllegalStateException("Not a numeric field");
      return (Integer) value;
    }

    public String text() {
      if (!(value instanceof String)) throw new IllegalStateException("Not a text field");
      return (String) value;
    }

    public byte[] bytes() {
      if (!(value instanceof byte[])) throw new IllegalStateException("Not a binary field");
      return ((byte[]) value).clone();
    }
  }
}
