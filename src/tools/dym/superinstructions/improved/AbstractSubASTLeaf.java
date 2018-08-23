package tools.dym.superinstructions.improved;

import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;


abstract class AbstractSubASTLeaf extends SingleSubAST {

  AbstractSubASTLeaf(final Node enclosedNode,
      final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
    this.totalLocalActivations =
        this.activationsByType.values().stream().reduce(0L, Long::sum);
  }

  AbstractSubASTLeaf(final SingleSubAST copyFrom) {
    super(copyFrom);
  }

  @Override
  boolean isRelevant() {
    return false;
  }

  @Override
  public void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {}

  @Override
  boolean isLeaf() {
    return true;
  }

  @Override
  int numberOfNodes() {
    return 1;
  }

  @Override
  long totalActivations() {
    return totalLocalActivations;
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (that instanceof AbstractSubASTLeaf) {
      final SingleSubAST thatAST = (SingleSubAST) that;
      return this.enclosedNodeType == thatAST.enclosedNodeType
          && this.sourceFileIndex == thatAST.sourceFileIndex
          && this.sourceSectionLength == thatAST.sourceSectionLength
          && this.sourceFileName.equals(thatAST.sourceFileName);
    }
    return false;
  }

}
