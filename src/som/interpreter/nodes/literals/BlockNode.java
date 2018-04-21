package som.interpreter.nodes.literals;

import java.util.ArrayList;

import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.debugger.Tags.LiteralTag;


public class BlockNode extends LiteralNode {

  protected final SInvokable blockMethod;
  protected final boolean    needsAdjustmentOnScopeChange;

  public BlockNode(final SInvokable blockMethod, final boolean needsAdjustmentOnScopeChange) {
    this.blockMethod = blockMethod;
    this.needsAdjustmentOnScopeChange = needsAdjustmentOnScopeChange;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (LiteralTag.class == tag) {
      return false; // Blocks should not be indicated as literals, looks strange.
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public SInvokable getBlockMethod() {
    return blockMethod;
  }

  public Argument[] getArguments() {
    Method method = (Method) blockMethod.getInvokable();
    Variable[] variables = method.getLexicalScope().getVariables();
    ArrayList<Argument> args = new ArrayList<>();
    for (Variable v : variables) {
      if (v instanceof Argument) {
        args.add((Argument) v);
      }
    }
    return args.toArray(new Argument[0]);
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return new SBlock(blockMethod, null);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
    if (!needsAdjustmentOnScopeChange && !inliner.outerScopeChanged()) {
      return;
    }

    Method blockIvk = (Method) blockMethod.getInvokable();
    Method adapted = blockIvk.cloneAndAdaptAfterScopeChange(
        inliner.getScope(blockIvk), inliner.contextLevel + 1, true,
        inliner.outerScopeChanged());
    SInvokable adaptedIvk = new SInvokable(blockMethod.getSignature(),
        AccessModifier.BLOCK_METHOD,
        adapted, blockMethod.getEmbeddedBlocks());
    replace(createNode(adaptedIvk));
  }

  protected BlockNode createNode(final SInvokable adapted) {
    return new BlockNode(adapted, needsAdjustmentOnScopeChange).initialize(sourceSection);
  }

  @Override
  public ExpressionNode inline(final MethodBuilder builder) {
    return blockMethod.getInvokable().inline(builder, blockMethod);
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SInvokable blockMethod,
        final boolean needsAdjustmentOnScopeChange) {
      super(blockMethod, needsAdjustmentOnScopeChange);
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return new SBlock(blockMethod, frame.materialize());
    }

    @Override
    protected BlockNode createNode(final SInvokable adapted) {
      return new BlockNodeWithContext(adapted, needsAdjustmentOnScopeChange).initialize(
          sourceSection);
    }
  }
}
