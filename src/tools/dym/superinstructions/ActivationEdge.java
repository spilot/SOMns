package tools.dym.superinstructions;

import java.util.Objects;

/**
 * Created by fred on 31/07/17.
 */
public class ActivationEdge {
  private ActivationNode parent, child;
  private int childIndex;
  private long activations;
  private String javaType;

  public ActivationEdge(ActivationNode parent, ActivationNode child, int childIndex,
                        String javaType, long activations) {
    this.parent = parent;
    this.child = child;
    this.childIndex = childIndex;
    this.activations = activations;
    this.javaType = javaType;
  }

  public ActivationNode getParent() {
    return parent;
  }

  public ActivationNode getChild() {
    return child;
  }

  public long getActivations() {
    return activations;
  }

  public void setActivations(long activations) {
    this.activations = activations;
  }

  public void addActivations(long activations) {
    this.activations += activations;
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
    return activations == that.activations &&
            Objects.equals(parent, that.parent) &&
            Objects.equals(child, that.child) &&
            Objects.equals(javaType, that.javaType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, child, activations, javaType);
  }
}
