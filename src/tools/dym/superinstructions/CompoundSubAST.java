package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


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

  public void add(final AbstractSubAST arg) {
    if (arg instanceof SingleSubAST) {
      addIfNew((SingleSubAST) arg);
    } else if (arg instanceof CompoundSubAST || arg instanceof VirtualSubAST) {
      ((CompoundSubAST) arg).enclosedNodes.forEach(this::addIfNew);
    } else {
      assert false;
    }
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
  Stream<SingleSubAST> allSubASTs() {
    return this.enclosedNodes.stream().flatMap(SingleSubAST::allSubASTs);
  }

  @Override
  Stream<VirtualSubAST> commonSubASTs(final AbstractSubAST arg) {
    return this.allSubASTs().flatMap((mySubAST) -> mySubAST.commonSubASTs(arg));
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
}
