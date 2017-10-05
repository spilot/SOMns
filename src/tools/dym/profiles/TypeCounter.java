package tools.dym.profiles;

import com.oracle.truffle.api.source.SourceSection;

import java.util.HashMap;
import java.util.Map;

/**
 * A counter which keeps track of the number of node activations
 * and their respective result types (that is, Java types).
 */
public class TypeCounter {
  protected final SourceSection source;
  private Map<Class<?>, Long> activations;

  public TypeCounter(final SourceSection source) {
    this.source = source;
    this.activations = new HashMap<>();
  }

  public SourceSection getSourceSection() {
    return source;
  }

  public void recordType(Object result) {
    Class<?> type = result.getClass();
    activations.merge(type, 1L, Long::sum);
  }

  @Override
  public String toString() {
    return "TypeCnt[" + activations.size() + " types]";
  }

  public Map<Class<?>, Long> getActivations() {
    return this.activations;
  }
}
