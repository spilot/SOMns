package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class IntegerLiteralNode extends LiteralNode {

  private final long value;

  public IntegerLiteralNode(final long value) {
    this.value = value;
  }

  @Override
  public long executeLong(final VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return value;
  }

  public long getValue() {
    return value;
  }
}
