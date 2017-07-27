package tools.dym.profiles;

import com.oracle.truffle.api.source.SourceSection;
import som.interpreter.Types;
import tools.dym.nodes.ActivationType;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fred on 27/07/17.
 */
public class TypeCounter {
  protected final SourceSection source;
  private Map<ActivationType, Long> activations;

  public TypeCounter(final SourceSection source) {
    this.source = source;
    this.activations = new HashMap<>();
  }

  public SourceSection getSourceSection() {
    return source;
  }

  public void recordType(Object result) {
    ActivationType type = new ActivationType(result.getClass(), Types.getClassOf(result).getInstanceFactory());
    activations.merge(type, 1L, Long::sum);
  }

  @Override
  public String toString() {
    return "TypeCnt[" + activations.size() + " types]";
  }

  public long getTotalActivations() {
    return activations.values().stream().mapToLong(Long::intValue).sum();
  }

  public Map<ActivationType, Long> getActivations() {
    return this.activations;
  }
}
