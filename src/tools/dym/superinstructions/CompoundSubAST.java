package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


class CompoundSubAST extends AbstractSubAST {

  protected final String ACTIVATIONS_STRING = " sum of mean activations:\n";

  protected List<SingleSubAST> enclosedNodes;

  CompoundSubAST(final SingleSubAST... nodes) {
    if (nodes.length == 0) {
      throw new IllegalArgumentException();
    }
    enclosedNodes = new ArrayList<>();
    for (SingleSubAST n : nodes) {
      if (n == null) {
        throw new NullPointerException();
      }
      if (!n.equals(nodes[0])) {
        throw new IllegalArgumentException();
      }
      enclosedNodes.add(n);
    }
  }

  CompoundSubAST(final List<SingleSubAST> children) {
    children.forEach((child) -> {
      assert child != null;
      assert children.get(0).equals(child);
    });
    enclosedNodes = children;
  }

  @Override
  public AbstractSubAST add(final AbstractSubAST arg) {
    arg.forEach(this::addIfNew);
    return this;
  }

  @Override
  public boolean equals(final Object arg) {
    return this.enclosedNodes.get(0).equals(arg);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(this.score()).append(ACTIVATIONS_STRING);
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(sb.append("--\n"), "  ");
    });
    return sb.toString();
  }

  private void addIfNew(final SingleSubAST item) {
    if (this.enclosedNodes.stream().noneMatch((existingNode) -> (existingNode == item))) {
      this.enclosedNodes.add(item);
    }
  }

  @Override
  List<SingleSubAST> allSubASTs(final List<SingleSubAST> accumulator) {
    for (SingleSubAST child : enclosedNodes) {
      child.allSubASTs(accumulator);
    }
    return accumulator;
  }

  @Override
  List<VirtualSubAST> commonSubASTs(final AbstractSubAST arg,
      final List<VirtualSubAST> accumulator) {
    this.allSubASTs().forEach((mySubAST) -> mySubAST.commonSubASTs(arg, accumulator));
    return accumulator;
  }

  SingleSubAST getFirstNode() {
    return this.enclosedNodes.get(0);
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  @Override
  long score() {
    return enclosedNodes.stream().mapToLong(SingleSubAST::score).sum();
  }

  @Override
  void forEach(final Consumer<SingleSubAST> action) {
    enclosedNodes.forEach(action);
  }
}
