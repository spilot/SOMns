package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import som.VM;
import som.compiler.Variable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.AdditionPrim;
import tools.dym.Tags;
import tools.dym.superinstructions.GuardEvaluationCounter;


/**
 * Matches the following AST:
 *
 * LocalVariableWriteNode
 * EagerBinaryPrimitiveNode
 * LocalVariableReadNode (with the same variable as LocalVariableWriteNode above)
 * IntegerLiteralNode
 * AdditionPrim
 *
 * and replaces it with
 *
 * IncrementOperationNode
 *
 */
public abstract class IncrementOperationNode extends LocalVariableNode {
  private final long              increment;
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

  @Specialization(guards = "isLongKind(frame)", rewriteOn = {
      FrameSlotTypeException.class,
      ArithmeticException.class
  })
  public final long writeLong(final VirtualFrame frame) throws FrameSlotTypeException {
    final long newValue = Math.addExact(frame.getLong(slot), increment);
    frame.setLong(slot, newValue);
    return newValue;
  }

  @Specialization(replaces = {"writeLong"})
  public final Object writeGeneric(final VirtualFrame frame) {
    // Replace myself with the stored original subtree.
    // This could happen because the frame slot type has changed or because of an overflow.
    final Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  protected final boolean isLongKind(final VirtualFrame frame) { // uses frame to make sure
                                                                 // guard is not converted to
                                                                 // assertion
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
  public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
    /*
     * This should never happen because ``replaceAfterScopeChange`` is only called in the
     * parsing stage, whereas the ``IncrementOperationNode`` superinstruction is only inserted
     * into the AST *after* parsing.
     */
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  public LocalVariableNode getOriginalSubtree() {
    return originalSubtree;
  }

  /**
   * Check if the AST subtree has the shape of an increment operation.
   */
  public static boolean isIncrementOperation(ExpressionNode exp, final Variable.Local var) {
    GuardEvaluationCounter.recordActivation(IncrementOperationNode.class, exp);
    exp = SOMNode.unwrapIfNecessary(exp);
    if (exp instanceof EagerBinaryPrimitiveNode) {
      final EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) exp;
      if (SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof LocalVariableReadNode
          && SOMNode.unwrapIfNecessary(eagerNode.getArgument()) instanceof IntegerLiteralNode
          && SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) instanceof AdditionPrim) {
        final LocalVariableReadNode read =
            (LocalVariableReadNode) SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
        if (read.getLocal().equals(var)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct
   * shape.
   */
  public static void replaceNode(final LocalVariableWriteNode node) {
    final EagerBinaryPrimitiveNode eagerNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getExp());
    final long increment =
        ((IntegerLiteralNode) SOMNode.unwrapIfNecessary(eagerNode.getArgument())).getValue();
    final IncrementOperationNode newNode = IncrementOperationNodeGen.create(node.getLocal(),
        increment,
        node).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }
}
