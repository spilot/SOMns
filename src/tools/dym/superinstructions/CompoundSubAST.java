package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.List;


public class CompoundSubAST extends AbstractSubAST {

  private List<SingleSubAST> enclosedNodes;
  private long               activations;

  public CompoundSubAST(final SingleSubAST... nodes) {
    if (nodes.length == 0) {
      throw new IllegalArgumentException();
    }
    enclosedNodes = new ArrayList<>();
    activations = 0L;
    for (SingleSubAST n : nodes) {
      if (n == null) {
        throw new NullPointerException();
      }
      if (!n.equals(nodes[0])) {
        throw new IllegalArgumentException();
      }
      enclosedNodes.add(n);
      activations += n.activations;
    }
  }

  public void add(final SingleSubAST node) {
    if (!node.equals(enclosedNodes.get(0))) {
      throw new IllegalArgumentException();
    }
    this.enclosedNodes.add(node);
    this.activations += node.activations;
  }

  @Override
  public long score() {
    return enclosedNodes.stream().mapToLong(SingleSubAST::score).sum();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    enclosedNodes.forEach((subast) -> {
      subast.toStringRecursive(sb.append("--\n"), "");
    });
    return sb.toString();
  }

  @Override
  public int numberOfNodes() {
    return enclosedNodes.stream().mapToInt(SingleSubAST::numberOfNodes).sum();
  }

  @Override
  public long totalActivations() {
    return activations;
  }

  @Override
  public boolean equals(final Object arg) {
    return this.enclosedNodes.get(0).equals(arg);
  }

}
