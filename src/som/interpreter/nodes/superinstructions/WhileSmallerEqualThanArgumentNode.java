package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.compiler.Variable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.arithmetic.LessThanOrEqualPrim;
import som.vm.constants.Nil;
import tools.dym.Tags;
import tools.dym.superinstructions.GuardEvaluationCounter;


/**
 * Matches the following AST:
 *
 * WhileInlinedLiteralsNode (expectedBool == true)
 * EagerBinaryPrimitiveNode
 * LocalVariableReadNode (of type Long)
 * LocalArgumentReadNode (of type Long)
 * LessThanOrEqualPrim
 * ExpressionNode
 *
 * and replaces it with
 *
 * WhileSmallerEqualThanArgumentNode
 * ExpressionNode
 *
 */
abstract public class WhileSmallerEqualThanArgumentNode extends ExprWithTagsNode {

  private FrameSlot             variableSlot;
  private final int             argumentIndex;
  @Child private ExpressionNode bodyNode;

  @SuppressWarnings("unused") private final WhileInlinedLiteralsNode originalSubtree;

  public WhileSmallerEqualThanArgumentNode(final Variable.Local variable,
      final int argumentIndex,
      final ExpressionNode bodyNode, final WhileInlinedLiteralsNode originalSubtree) {
    this.variableSlot = variable.getSlot();
    this.argumentIndex = argumentIndex;
    this.bodyNode = bodyNode;
    this.originalSubtree = originalSubtree;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == Tags.LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  private boolean evaluateCondition(final VirtualFrame frame) throws FrameSlotTypeException {
    final Object argumentValue = SArguments.arg(frame, argumentIndex);
    if (!(argumentValue instanceof Long)) {
      throw new FrameSlotTypeException(); // Argument is not of type Long! (should never
                                          // happen)
    }
    // Frame.getLong might throw FrameSlotTypeException
    return frame.getLong(variableSlot) <= (Long) argumentValue;
  }

  @Specialization(rewriteOn = {FrameSlotTypeException.class})
  public Object executeSpecialized(final VirtualFrame frame) throws FrameSlotTypeException {
    long iterationCount = 0;

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    boolean loopConditionResult = evaluateCondition(frame);

    try {
      while (loopConditionResult) {
        bodyNode.executeGeneric(frame);
        loopConditionResult = evaluateCondition(frame);

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
        ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(iterationCount, this);
      }
    }
    return Nil.nilObject;
  }

  @Specialization(replaces = {"executeSpecialized"})
  public Object executeAndDeoptimize(final VirtualFrame frame) {
    // Execute the original subtree and replace myself with it
    final Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }

  /**
   * Check if the AST subtree has the correct shape.
   */
  public static boolean isWhileSmallerEqualThanArgumentNode(final boolean expectedBool,
      ExpressionNode conditionNode,
      final VirtualFrame frame) {
    GuardEvaluationCounter.recordActivation(WhileSmallerEqualThanArgumentNode.class,
        conditionNode);
    // whileFalse: does not match
    if (!expectedBool) {
      return false;
    } else {
      System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
          + ": expectedBool was false.");
    }
    conditionNode = SOMNode.unwrapIfNecessary(conditionNode);

    // ... is the condition a binary operation?
    if (conditionNode instanceof EagerBinaryPrimitiveNode) {
      final EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) conditionNode;
      // is the operation ``LocalVariable <= LocalArgument``?
      if (SOMNode.unwrapIfNecessary(
          eagerNode.getReceiver()) instanceof LocalVariableReadNode) {
        if (SOMNode.unwrapIfNecessary(
            eagerNode.getArgument()) instanceof LocalArgumentReadNode) {
          if (SOMNode.unwrapIfNecessary(
              eagerNode.getPrimitive()) instanceof LessThanOrEqualPrim) {
            final LocalArgumentReadNode arg =
                (LocalArgumentReadNode) SOMNode.unwrapIfNecessary(eagerNode.getArgument());
            final LocalVariableReadNode variable =
                (LocalVariableReadNode) SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
            // Are variable and argument both of type Long?
            if (SArguments.arg(frame, arg.getArgumentIndex()) instanceof Long) {
              if (frame.isLong(variable.getLocal().getSlot())) {
                return true;
              } else {
                System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
                    + ": frame.isLong(variable.getLocal().getSlot()) should have been long, but was: "
                    + variable.getLocal().getSlot().getKind().toString());
              }
            } else {
              System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
                  + ": SArguments.arg(frame, arg.getArgumentIndex()) should have type long, but was: "
                  + SArguments.arg(frame, arg.getArgumentIndex()).getClass()
                              .getSimpleName());
            }
          } else {
            System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
                + ": SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()); expected LessThanOrEqualPrim, actual "
                + SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()).getClass()
                         .getSimpleName());
          }
        } else {
          System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
              + ": SOMNode.unwrapIfNecessary(eagerNode.getArgument()); expected LocalArgumentReadNode, actual "
              + SOMNode.unwrapIfNecessary(eagerNode.getArgument()).getClass()
                       .getSimpleName());
        }
      } else {
        System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
            + ": SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof LocalVariableReadNode, actual "
            + SOMNode.unwrapIfNecessary(eagerNode.getReceiver()).getClass().getSimpleName());
      }
    } else {
      System.err.println(WhileSmallerEqualThanArgumentNode.class.getSimpleName()
          + " conditionNode should have been EagerBinaryPrimitiveNode, but was: "
          + conditionNode.getClass().getSimpleName());
      return false;
    }

    return false;
  }

  /**
   * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct
   * shape.
   */
  static public WhileSmallerEqualThanArgumentNode replaceNode(
      final WhileInlinedLiteralsNode node) {
    // Extract local variable slot and argument index
    final EagerBinaryPrimitiveNode conditionNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getConditionNode());
    final LocalVariableReadNode variableRead =
        (LocalVariableReadNode) SOMNode.unwrapIfNecessary(conditionNode.getReceiver());
    final LocalArgumentReadNode argumentRead =
        (LocalArgumentReadNode) SOMNode.unwrapIfNecessary(conditionNode.getArgument());
    // replace node with superinstruction
    final WhileSmallerEqualThanArgumentNode newNode =
        WhileSmallerEqualThanArgumentNodeGen.create(
            variableRead.getLocal(), argumentRead.getArgumentIndex(), node.getBodyNode(), node)
                                            .initialize(
                                                node.getSourceSection());
    node.replace(newNode);
    newNode.adoptChildren();
    // Without the following line, WhileSmallerEqualThanArgumentNode is not taken into
    // account when running the dynamic metrics tool.
    // However, if we uncomment the following line, `./som -dm` fails because of
    // the instrumentation nodes are messed up. But why?
    // VM.insertInstrumentationWrapper(newNode);
    return newNode;
  }

  public ExpressionNode getBodyNode() {
    return bodyNode;
  }
}
