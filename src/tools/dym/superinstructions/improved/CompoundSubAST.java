package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class CompoundSubAST extends AbstractSubAST {

  final String ACTIVATIONS_STRING = " sum of mean activations:\n";

  List<SingleSubAST> enclosedNodes;

  CompoundSubAST(final SingleSubAST... nodes) {
    if (nodes.length == 0) {
      throw new IllegalArgumentException();
    }
    enclosedNodes = new ArrayList<>();
    for (SingleSubAST n : nodes) {
      assert n != null;
      assert n.equals(nodes[0]);
      enclosedNodes.add(n);
    }
  }

  CompoundSubAST(final List<SingleSubAST> children) {
    children.forEach((child) -> {
      assert child != null;
      assert children.get(0).equals(child);
    });
    enclosedNodes = new ArrayList<>();
    children.forEach(this::add);
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
    StringBuilder sb = new StringBuilder().append(this.getScore()).append(ACTIVATIONS_STRING);
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(sb.append("--\n"), "  ");
    });
    return sb.toString();
  }

  private void addIfNew(final SingleSubAST item) {
    for (SingleSubAST enclosedNode : enclosedNodes) {
      if (enclosedNode == item) { // || enclosedNode.toString().equals(item.toString())) {
        return;
      }
    }
    enclosedNodes.add(item);
  }

  @Override
  List<AbstractSubAST> commonSubASTs(final AbstractSubAST arg,
      final List<AbstractSubAST> accumulator) {
    this.forEachRelevantSubAST((mySubAST) -> mySubAST.commonSubASTs(arg, accumulator));
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
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  void forEach(final Consumer<SingleSubAST> action) {
    enclosedNodes.forEach(action);
  }

  @Override
  void forEachRelevantSubAST(final Consumer<SingleSubAST> action) {
    for (SingleSubAST child : enclosedNodes) {
      if (child.isRelevant()) {
        child.forEachRelevantSubAST(action);
      }
    }
  }
}
