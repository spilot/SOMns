package som.interpreter.nodes.nary;

import bd.primitives.nodes.EagerPrimitive;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;
import tools.Send;


public abstract class EagerPrimitiveNode extends ExpressionNode
    implements EagerPrimitive, Send {
  protected final SSymbol selector;

  protected EagerPrimitiveNode(final SSymbol selector) {
    this.selector = selector;
  }

  @Override
  public SSymbol getSelector() {
    return selector;
  }
}
