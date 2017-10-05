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
    return Objects.hash(Arrays.deepHashCode(trace), javaType);
  }

  public String getTraceAsString() {
    return Arrays.stream(trace).map(Object::toString).collect(Collectors.joining(","));
  }

  public String toString() {
    return String.format("%s[%s]", getTraceAsString(), javaType);
  }

  public String getTraceAsPrettyString() {
    return Arrays.stream(trace).map(
            entry -> entry instanceof String ? abbreviateClass((String)entry) : entry.toString()
    ).collect(Collectors.joining(","));
  }

  public String toPrettyString() {
    return String.format("%s[%s]", getTraceAsPrettyString(), abbreviateClass(javaType));
  }

  public Object[] getTrace() {
    return trace;
  }

  public String getLeafClass() {
    return getClass(getNumberOfClasses() - 1);
  }

  public int getLeafChildIndex() {
    return getChildIndex(getNumberOfClasses() - 2);
  }

  public int getNumberOfClasses() {
    return (trace.length + 1) / 2;
  }

  public String getClass(int i) {
    assert i < getNumberOfClasses();
    return (String)trace[i * 2];
  }

  public int getChildIndex(int i) {
    assert i < getNumberOfClasses() - 1;
    return (Integer)trace[i * 2 + 1];
  }

  public String getJavaType() {
    return javaType;
  }

  static public boolean subtraceEquals(Object[] a, Object[] b, int length) {
    if(a.length < length || b.length < length) return false;
    for(int i = 0; i < length; i++) {
      if(!a[i].equals(b[i])) {
        return false;
      }
    }
    return true;
  }

  public boolean traceStartsWith(Object[] prefix) {
    if(trace.length < prefix.length) return false;
    for(int i = 0; i < prefix.length; i++)
      if(!trace[i].equals(prefix[i])) {
        return false;
    }
    return true;
  }

  static public Object[] getSuffix(Object[] trace, int length) {
    Object[] suffix = new Object[length];
    System.arraycopy(trace, trace.length - length, suffix, 0, length);
    return suffix;
  }

  static public String abbreviateClass(String className) {
    String[] splitted = className.split("\\.");
    return splitted[splitted.length - 1];
  }
}
