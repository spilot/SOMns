package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import som.interpreter.ReturnException;
import som.vm.NotYetImplementedException;
import tools.dym.profiles.TypeCounter;

import java.util.Map;

/**
 * Created by fred on 27/07/17.
 */
public class TypeCountingNode<T extends TypeCounter> extends ExecutionEventNode {
  protected final T counter;

  public TypeCountingNode(final T counter) { this.counter = counter; }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.recordType(result.getClass());
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable e) {
    // TODO: make language independent
    if (e instanceof ReturnException) {
      counter.recordType(((ReturnException) e).result().getClass());
    } else if(e instanceof UnexpectedResultException) {
      counter.recordType(((UnexpectedResultException) e).getResult().getClass());
    } else {
      throw new NotYetImplementedException();
    }
  }

}
