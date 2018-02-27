package som.instrumentation;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import bd.nodes.PreevaluatedExpression;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;
import tools.Send;


// TODO: see whether we can get the code generator to do this for us, there is some issue with the pre-evaluated method stuff, but works for other node
public class MessageSendNodeWrapper
    implements InstrumentableFactory<AbstractMessageSendNode> {

  @Override
  public WrapperNode createWrapper(
      final AbstractMessageSendNode node, final ProbeNode probe) {
    return new MessageSendWrapper(node, probe);
  }

  private static final class MessageSendWrapper
      extends AbstractMessageSendNode implements WrapperNode {

    @Child private ExpressionNode delegate;
    @Child private ProbeNode      probe;

    private MessageSendWrapper(final AbstractMessageSendNode delegate, final ProbeNode probe) {
      super(null);
      this.delegate = delegate;
      this.probe = probe;
    }

    @Override
    public Node getDelegateNode() {
      return delegate;
    }

    @Override
    public ProbeNode getProbeNode() {
      return probe;
    }

    @Override
    public NodeCost getCost() {
      return NodeCost.NONE;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
      Object returnValue;
      for (;;) {
        boolean wasOnReturnExecuted = false;
        try {
          probe.onEnter(frame);
          Object result = ((PreevaluatedExpression) delegate).doPreEvaluated(frame, args);
          wasOnReturnExecuted = true;
          probe.onReturnValue(frame, result);
          return result;
        } catch (Throwable t) {
          Object result = probe.onReturnExceptionalOrUnwind(frame, t, wasOnReturnExecuted);
          if (result == ProbeNode.UNWIND_ACTION_REENTER) {
            continue;
          } else if (result != null) {
            returnValue = result;
            break;
          } else {
            throw t;
          }
        }
      }
      return returnValue;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object returnValue;
      for (;;) {
        boolean wasOnReturnExecuted = false;
        try {
          probe.onEnter(frame);
          Object result = delegate.executeGeneric(frame);
          wasOnReturnExecuted = true;
          probe.onReturnValue(frame, result);
          return result;
        } catch (Throwable t) {
          Object result = probe.onReturnExceptionalOrUnwind(frame, t, wasOnReturnExecuted);
          if (result == ProbeNode.UNWIND_ACTION_REENTER) {
            continue;
          } else if (result != null) {
            returnValue = result;
            break;
          } else {
            throw t;
          }
        }
      }
      return returnValue;
    }

    @Override
    public SourceSection getSourceSection() {
      return delegate.getSourceSection();
    }

    @Override
    public SSymbol getSelector() {
      return ((Send) delegate).getSelector();
    }
  }
}
