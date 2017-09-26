package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import som.compiler.Variable;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.LocalVariableNode;
import tools.dym.Tags;

public abstract class IncrementOperationNode extends LocalVariableNode {
  private final long increment;
  private final LocalVariableNode originalSubtree;

  public IncrementOperationNode(final Variable.Local variable,
                                final long increment,
                                final LocalVariableNode originalSubtree) {
    super(variable);
    this.increment = increment;
    this.originalSubtree = originalSubtree;
  }

  public IncrementOperationNode(final IncrementOperationNode node) {
    super(node.var);
    this.increment = node.getIncrement();
    this.originalSubtree = node.getOriginalSubtree();
  }

  public long getIncrement() {
    return increment;
  }

  @Specialization(guards = "isLongKind(frame)", rewriteOn = {FrameSlotTypeException.class})
  public final long writeLong(final VirtualFrame frame) throws FrameSlotTypeException {
    long newValue = frame.getLong(slot) + increment;
    frame.setLong(slot, newValue);
    return newValue;
  }

  @Specialization(replaces = {"writeLong"})
  public final Object writeGeneric(final VirtualFrame frame) {
    Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  protected final boolean isLongKind(final VirtualFrame frame) { // uses frame to make sure guard is not converted to assertion
    if (slot.getKind() == FrameSlotKind.Long) {
      return true;
    }
    if (slot.getKind() == FrameSlotKind.Illegal) {
      slot.setKind(FrameSlotKind.Long);
      return true;
    }
    return false;
  }

  @Override
  protected final boolean isTaggedWith(final Class<?> tag) {
    if (tag == Tags.LocalVarWrite.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "[" + var.name + "]";
  }

  @Override
  public void replaceAfterScopeChange(final InliningVisitor inliner) {
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  public LocalVariableNode getOriginalSubtree() {
    return originalSubtree;
  }
}