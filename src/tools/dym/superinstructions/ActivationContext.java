package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by fred on 03/10/17.
 */
public class ActivationContext {
  private final Object[] trace;
  private final String javaType;

  public ActivationContext(Object[] trace, String javaType) {
    this.trace = trace;
    this.javaType = javaType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ActivationContext that = (ActivationContext) o;
    return Arrays.equals(trace, that.trace) &&
            Objects.equals(javaType, that.javaType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(trace, javaType);
  }

  public String getTraceAsString() {
    return Arrays.stream(trace).map(Object::toString).collect(Collectors.joining(","));
  }

  public String toString() {
    return String.format("%s[%s]", getTraceAsString(), javaType);
  }
}
