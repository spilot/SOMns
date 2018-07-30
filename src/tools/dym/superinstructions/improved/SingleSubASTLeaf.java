package tools.dym.superinstructions.improved;

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;


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
  void computeScore() {
    score = totalActivations();
  }

  @Override
  boolean isRelevant() {
    return false;
  }

  @Override
  List<SingleSubAST> allSubASTs(final List<SingleSubAST> accumulator) {
    return accumulator;
  }

  @Override
  boolean isLeaf() {
    return true;
  }

  @Override
  int numberOfNodes() {
    return 1;
  }
}
