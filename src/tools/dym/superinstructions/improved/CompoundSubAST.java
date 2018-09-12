package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public class CompoundSubAST extends AbstractSubAST {
  List<SingleSubAST> enclosedNodes;

  CompoundSubAST(final SingleSubAST firstNode) {
    super();
    this.enclosedNodes = new ArrayList<>();
    enclosedNodes.add(firstNode);
  }

  @Override
  AbstractSubAST add(final AbstractSubAST arg) {
    arg.forEachDirectSubAST(this::addIfNew);
    return this;
  }

  private void addIfNew(final SingleSubAST item) {
    for (final SingleSubAST enclosedNode : enclosedNodes) {
      assert enclosedNode.congruent(item) : enclosedNode + "\n" + item;
      if (enclosedNode.equals(item)) {
        // this will compute the incremental mean between the two
        enclosedNode.add(item);
        return;

      }
    }
    enclosedNodes.add(item);
  }

  @Override
  double computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  boolean congruent(final CompoundSubAST arg) {
    // we don't need to check every node against arg because all our enclosedNodes are mutually
    // congruent
    return this.getFirstNode().congruent(arg.getFirstNode());
  }

  @Override
  boolean congruent(final SingleSubAST arg) {
    // we don't need to check every node against arg because all our enclosedNodes are mutually
    // congruent
    return this.getFirstNode().congruent(arg);
  }

  @Override
  void forEachDirectSubAST(final Consumer<SingleSubAST> action) {
    enclosedNodes.forEach(action);
  }

  @Override
  void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {
    for (final SingleSubAST child : enclosedNodes) {
      if (child.isRelevant()) {
        child.forEachTransitiveRelevantSubAST(action);
      }
    }
  }

  SingleSubAST getFirstNode() {
    return this.enclosedNodes.get(0);
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator, final String prefix) {
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(accumulator, prefix).append('\n');
    });
    return accumulator;
  }
}
