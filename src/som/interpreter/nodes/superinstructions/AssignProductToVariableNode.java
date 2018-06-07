package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import som.VM;
import som.compiler.Variable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.MultiplicationPrim;
import tools.dym.Tags;
import tools.dym.superinstructions.GuardEvaluationCounter;


/**
 * Matches the following AST:
 *
 * LocalVariableWriteNode
 *   EagerBinaryPrimitiveNode
 *     LocalVariableReadNode (of type Double)
 *     LocalVariableReadNode (of type Double)
 *     MultiplicationPrim
 *
 * and replaces it with
 *
 * AssignProductToVariable
 */
public abstract class AssignProductToVariableNode extends LocalVariableNode {
  protected final FrameSlot         leftSlot, rightSlot;
  protected final LocalVariableNode originalSubtree;

  public AssignProductToVariableNode(final Variable.Local destination,
      final Variable.Local left,
      final Variable.Local right,
      final LocalVariableNode originalSubtree) {
    super(destination);
    this.leftSlot = left.getSlot();
    this.rightSlot = right.getSlot();
    this.originalSubtree = originalSubtree;
  }

  public AssignProductToVariableNode(final AssignProductToVariableNode node) {
    super(node.var);
    this.leftSlot = node.getLeftSlot();
    this.rightSlot = node.getRightSlot();
    this.originalSubtree = node.getOriginalSubtree();
  }

  public FrameSlot getLeftSlot() {
    return leftSlot;
  }

  public FrameSlot getRightSlot() {
    return rightSlot;
  }

  public LocalVariableNode getOriginalSubtree() {
    return originalSubtree;
  }

  @Specialization(guards = "isDoubleKind(frame)", rewriteOn = {FrameSlotTypeException.class})
  public final double writeDouble(final VirtualFrame frame) throws FrameSlotTypeException {
    // Read the two slots, multiply their contents, set the variable
    // This might throw a FrameSlotTypeException, in which case the
    // original subtree is restored
    final double newValue = frame.getDouble(leftSlot) * frame.getDouble(rightSlot);
    frame.setDouble(slot, newValue);
    return newValue;
  }

  @Specialization(replaces = {"writeDouble"})
  public final Object writeGeneric(final VirtualFrame frame) {
    // Replace myself with the stored original subtree
    final Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  // uses frame to make sure guard is not converted to assertion
  protected final boolean isDoubleKind(final VirtualFrame frame) {
    if (slot.getKind() == FrameSlotKind.Double) {
      return true;
    }
    if (slot.getKind() == FrameSlotKind.Illegal) {
      slot.setKind(FrameSlotKind.Double);
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
     * parsing stage, whereas the ``AssignProductToVariableNode`` superinstruction is only
     * inserted
     * into the AST *after* parsing.
     */
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  /**
   * Check if the AST subtree has the correct shape.
   */
  public static boolean isAssignProductOperation(ExpressionNode exp,
      final VirtualFrame frame) {
    GuardEvaluationCounter.recordActivation(AssignProductToVariableNode.class, exp);
    exp = SOMNode.unwrapIfNecessary(exp);
    // Check that the expression is a multiplication of two local variables ...
    if (exp instanceof EagerBinaryPrimitiveNode) {
      final EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) exp;
      if (SOMNode.unwrapIfNecessary(
          eagerNode.getReceiver()) instanceof LocalVariableReadNode) {
        if (SOMNode.unwrapIfNecessary(
            eagerNode.getArgument()) instanceof LocalVariableReadNode) {
          if (SOMNode.unwrapIfNecessary(
              eagerNode.getPrimitive()) instanceof MultiplicationPrim) {
            // ... and extract the variables ...
            final LocalVariableReadNode left =
                (LocalVariableReadNode) SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
            final LocalVariableReadNode right =
                (LocalVariableReadNode) SOMNode.unwrapIfNecessary(eagerNode.getArgument());
            // ... and check that they currently store Double values
            if (frame.isDouble(left.getLocal().getSlot())) {
              if (frame.isDouble(right.getLocal().getSlot())) {
                return true;
              } else {
                System.out.println(AssignProductToVariableNode.class.getSimpleName()
                    + "right.getLocal().getSlot() expected: Double, actual: "
                    + right.getLocal().getSlot().getKind()
                    + " level: 1");
              }
            } else {
              System.out.println(AssignProductToVariableNode.class.getSimpleName()
                  + "left.getLocal().getSlot() expected: Double, actual: "
                  + left.getLocal().getSlot().getKind()
                  + " level: 2");
            }
          } else {
            System.out.println(
                AssignProductToVariableNode.class.getSimpleName()
                    + ": SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) expected: MultiplicationPrim actual: "
                    + SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()).getClass()
                             .getSimpleName()
                    + " level: 3");
          }
        } else {
          System.out.println(AssignProductToVariableNode.class.getSimpleName()
              + ": SOMNode.unwrapIfNecessary(eagerNode.getArgument()) expected: LocalVariableReadNode actual: "
              + SOMNode.unwrapIfNecessary(eagerNode.getArgument()).getClass().getSimpleName()
              + " level: 4");
        }
      } else {
        System.out.println(AssignProductToVariableNode.class.getSimpleName()
            + ": SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) expected: LocalVariableReadNode actual: "
            + SOMNode.unwrapIfNecessary(eagerNode.getReceiver()).getClass().getSimpleName()
            + " level: 5");
      }
    } else {
      System.out.println(AssignProductToVariableNode.class.getSimpleName()
          + ": exp expected: EagerBinaryPrimitiveNode, actual: "
          + exp.getClass().getSimpleName() + " level: 6");
    }
    return false;
  }

  /**
   * Replace ``node`` with a superinstruction. This assumes that the subtree has the correct
   * shape.
   */
  public static void replaceNode(final LocalVariableWriteNode node) {
    final EagerBinaryPrimitiveNode eagerNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getExp());
    final Variable.Local left =
        ((LocalVariableReadNode) SOMNode.unwrapIfNecessary(
            eagerNode.getReceiver())).getLocal();
    final Variable.Local right =
        ((LocalVariableReadNode) SOMNode.unwrapIfNecessary(
            eagerNode.getArgument())).getLocal();
    final AssignProductToVariableNode newNode =
        AssignProductToVariableNodeGen.create(node.getLocal(),
            left,
            right,
            node).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }
}
