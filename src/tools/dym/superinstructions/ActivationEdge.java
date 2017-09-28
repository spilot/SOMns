package tools.dym.superinstructions;

import java.util.Objects;

/**
 * Created by fred on 31/07/17.
 */
public class ActivationEdge {
  private final ActivationNode parent, child;
  private final int childIndex;
  private final String javaType;

  public ActivationEdge(ActivationNode parent, ActivationNode child, int childIndex,
                        String javaType) {
    this.parent = parent;
    this.child = child;
    this.childIndex = childIndex;
    this.javaType = javaType;
  }

  public ActivationNode getParent() {
    return parent;
  }

  public ActivationNode getChild() {
    return child;
  }

  public String getJavaType() {
    return javaType;
  }

  public int getChildIndex() {
    return childIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ActivationEdge that = (ActivationEdge) o;
    return childIndex == that.childIndex &&
            Objects.equals(parent, that.parent) &&
            Objects.equals(child, that.child) &&
            Objects.equals(javaType, that.javaType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, child, childIndex, javaType);
  }

  public String getAbbreviatedJavaType() {
    String[] splitted = javaType.split("\\.");
    return splitted[splitted.length - 1];
  }

  @Override
  public String toString() {
    return String.format("{%s --%d--> %s of %s}", getParent(), getChildIndex(), getChild(), getAbbreviatedJavaType());
  }
}
