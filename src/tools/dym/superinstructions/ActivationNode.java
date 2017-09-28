package tools.dym.superinstructions;

import sun.rmi.server.Activation;

import java.util.List;
import java.util.Objects;

/**
 * Created by fred on 31/07/17.
 */
public class ActivationNode {
  private final String className;

  public ActivationNode(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ActivationNode that = (ActivationNode) o;
    return Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className);
  }

  public String getAbbreviated() {
    String[] splitted = className.split("\\.");
    return splitted[splitted.length - 1];
  }

  @Override
  public String toString() {
    return String.format("[%s]", getAbbreviated());
  }
}
