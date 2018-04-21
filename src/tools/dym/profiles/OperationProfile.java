package tools.dym.profiles;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;


public final class OperationProfile extends Counter {

  private final String                    operation;
  private final Set<Class<?>>             tags;
  private final Deque<Object[]>           argumentsForExecutions;
  protected final int                     numArgsAndResult;
  protected final Map<Arguments, Integer> argumentTypes;

  public OperationProfile(final SourceSection source, final String operation,
      final Set<Class<?>> tags, final int numArgsAndResult) {
    super(source);
    this.numArgsAndResult = numArgsAndResult;
    this.operation = operation;
    this.tags = tags;
    argumentsForExecutions = new ArrayDeque<>();
    argumentTypes = new HashMap<>();
    assert operation != null;
  }

  protected void recordArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, Integer::sum);
  }

  public void enterMainNode() {
    argumentsForExecutions.push(new Object[numArgsAndResult]);
  }

  public String getOperation() {
    return operation;
  }

  public Set<Class<?>> getTags() {
    return tags;
  }

  public int getNumArgsAndResult() {
    return numArgsAndResult;
  }

  public Map<Arguments, Integer> getArgumentTypes() {
    return argumentTypes;
  }

  @TruffleBoundary
  public void profileArgument(final int argIdx, final Object value) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    // Example: the `+` is problematic in `def length: 1 + self.length()`
    if (!argumentsForExecutions.isEmpty()) {
      argumentsForExecutions.peek()[argIdx] = value;
    }
  }

  @TruffleBoundary
  public void profileReturn(final Object returnValue) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    // Example: the `+` is problematic in `def length: 1 + self.length()`
    if (!argumentsForExecutions.isEmpty()) {
      Object[] arguments = argumentsForExecutions.pop();
      arguments[0] = returnValue;
      recordArguments(arguments);
    }
  }

  @Override
  public String toString() {
    return "OpProf(" + operation + ")" + InvocationProfile.argumentsMapToString(argumentTypes);
  }
}
