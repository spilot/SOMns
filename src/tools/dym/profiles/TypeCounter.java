package tools.dym.profiles;

import com.oracle.truffle.api.source.SourceSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fred on 27/07/17.
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

  public void recordType(Class<?> type) {
    activations.merge(type, 1L, Long::sum);
  }

  @Override
  public String toString() {
    return "TypeCnt[" + activations.size() + " types]";
  }

  public long getTotalActivations() {
    return activations.values().stream().mapToLong(Long::intValue).sum();
  }
}
