package tools.dym.superinstructions.improved;

import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class SingleSubASTLeaf extends SingleSubAST {
  SingleSubASTLeaf(final Node enclosedNode, final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
  }

  SingleSubASTLeaf(final SingleSubAST copyFrom) {
    super(copyFrom);
  }

  @Override
  public boolean equals(final Object o) {
    return (o instanceof SingleSubASTLeaf)
        && this.enclosedNodeType == ((SingleSubASTLeaf) o).enclosedNodeType;
  }

  @Override
  boolean isRelevant() {
    return false;
  }

  @Override
  public void forEachRelevantSubAST(final Consumer<SingleSubAST> action) {
    return;
  }

  @Override
  boolean isLeaf() {
    return true;
  }

  @Override
  int numberOfNodes() {
    return 1;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }
}
