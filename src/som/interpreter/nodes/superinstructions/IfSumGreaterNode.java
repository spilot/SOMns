package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.compiler.Variable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.primitives.arithmetic.AdditionPrim;
import som.primitives.arithmetic.GreaterThanPrim;
import som.vm.constants.Nil;
import tools.dym.superinstructions.GuardEvaluationCounter;


/**
 * Matches the following AST:
 *
 * IfInlinedLiteralsNode (expectedBool = true)
 *   EagerBinaryPrimitiveNode
 *     EagerBinaryPrimitiveNode
 *       LocalVariableReadNode (of type double)
 *       LocalVariableReadNode (of type double)
 *       AdditionPrim
 *     DoubleLiteralNode
 *     GreaterThanPrim
 *   ExpressionNode
 *
 * and replaces it with
 *
 * IfSumGreaterNode
 *   ExpressionNode
 */
abstract public class IfSumGreaterNode extends ExprWithTagsNode {
  // equivalent to:
  // (left + right > than) ifTrue: body
  private final FrameSlot       left, right;
  private final double          than;
  @Child private ExpressionNode bodyNode;

  // In case we need to revert from this optimistic optimization, keep the
  // original nodes around
  @SuppressWarnings("unused") private final IfInlinedLiteralNode originalSubtree;

  public IfSumGreaterNode(final Variable.Local left, final Variable.Local right,
      final double than,
      final ExpressionNode inlinedBodyNode,
      final IfInlinedLiteralNode originalSubtree) {
    this.left = left.getSlot();
    this.right = right.getSlot();
    this.than = than;
    this.bodyNode = inlinedBodyNode;
    this.originalSubtree = originalSubtree;
  }

  private boolean evaluateCondition(final VirtualFrame frame) throws FrameSlotTypeException {
    // Evaluate the condition in a fast way!
    return (frame.getDouble(left) + frame.getDouble(right)) > than;
  }

  @Specialization(rewriteOn = {FrameSlotTypeException.class})
  public Object executeSpecialized(final VirtualFrame frame) throws FrameSlotTypeException {
    if (evaluateCondition(frame)) {
      return bodyNode.executeGeneric(frame);
    } else {
      return Nil.nilObject;
    }
  }

  @Specialization(replaces = {"executeSpecialized"})
  public Object executeAndDeoptimize(final VirtualFrame frame) {
    // Execute and replace myself with the original subtree
    final Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    final Node parent = getParent();
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed(this);
    }
    return true;
  }

  /**
   * Replace ``node`` with a superinstruction. This assumes that the subtree has the correct
   * shape.
   */
  public static IfSumGreaterNode replaceNode(final IfInlinedLiteralNode node) {
    // fetch the branching condition, which is a comparison (>)
    final EagerBinaryPrimitiveNode condition =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getConditionNode());
    // fetch left-hand side of comparison ...
    final EagerBinaryPrimitiveNode conditionLeft =
        (EagerBinaryPrimitiveNode) unwrapReceiver(condition);
    // ... which is an addition of two local variables
    final LocalVariableReadNode leftOperand =
        (LocalVariableReadNode) unwrapReceiver(conditionLeft);
    final LocalVariableReadNode rightOperand =
        (LocalVariableReadNode) unwrapArgument(conditionLeft);
    // right-hand side of comparison is a double literal
    final DoubleLiteralNode thanNode = (DoubleLiteralNode) unwrapArgument(condition);
    final IfSumGreaterNode newNode = IfSumGreaterNodeGen.create(leftOperand.getLocal(),
        rightOperand.getLocal(),
        thanNode.getValue(),
        node.getBodyNode(),
        node).initialize(node.getSourceSection());
    node.replace(newNode);
    newNode.adoptChildren(); // because we adopt the body node
    VM.insertInstrumentationWrapper(newNode);
    return newNode;
  }

  /**
   * Helper functions to increase readability.
   */
  private static ExpressionNode unwrapReceiver(final EagerBinaryPrimitiveNode eagerNode) {
    return SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
  }

  private static ExpressionNode unwrapArgument(final EagerBinaryPrimitiveNode eagerNode) {
    return SOMNode.unwrapIfNecessary(eagerNode.getArgument());
  }

  /**
   * Check if the AST subtree has the correct shape.
   */
  public static boolean isIfSumGreaterNode(final boolean expectedBool,
      final ExpressionNode conditionNode,
      final VirtualFrame frame) {
    GuardEvaluationCounter.recordActivation(IfSumGreaterNode.class, conditionNode);
    // is this even ifTrue?
    if (!expectedBool) {
      return false;
    }
    // is the branching condition a greater-than comparison?
    if (SOMNode.unwrapIfNecessary(conditionNode) instanceof EagerBinaryPrimitiveNode) {
      final EagerBinaryPrimitiveNode condition =
          (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(conditionNode);
      if (condition.getPrimitive() instanceof GreaterThanPrim) {
        // yes! is the left-hand side a binary operation and the right-hand side a double
        // literal?
        if (unwrapReceiver(condition) instanceof EagerBinaryPrimitiveNode
            && unwrapArgument(condition) instanceof DoubleLiteralNode) {
          final EagerBinaryPrimitiveNode conditionLeft =
              (EagerBinaryPrimitiveNode) unwrapReceiver(condition);
          // yes! is the left-hand side an addition of two variables?
          if (conditionLeft.getPrimitive() instanceof AdditionPrim) {
            if (unwrapReceiver(conditionLeft) instanceof LocalVariableReadNode
                && unwrapArgument(conditionLeft) instanceof LocalVariableReadNode) {
              final LocalVariableReadNode leftOperand =
                  (LocalVariableReadNode) unwrapReceiver(conditionLeft);
              final LocalVariableReadNode rightOperand =
                  (LocalVariableReadNode) unwrapArgument(conditionLeft);
              // yes! are the two variables of type double?
              if (frame.isDouble(leftOperand.getLocal().getSlot())
                  && frame.isDouble(rightOperand.getLocal().getSlot())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}
