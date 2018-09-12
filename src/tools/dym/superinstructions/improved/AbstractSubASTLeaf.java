package tools.dym.superinstructions.improved;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;


abstract class AbstractSubASTLeaf extends SingleSubAST {

  AbstractSubASTLeaf(final Node enclosedNode,
      final Map<String, Long> activationsByType,
      final long totalBenchmarkActivations) {
    super(enclosedNode, activationsByType, totalBenchmarkActivations);

  }

  AbstractSubASTLeaf(final SingleSubAST copyFrom) {
    super(copyFrom);
  }

  @Override
  boolean isRelevant() {
    return false;
  }

  @Override
  void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {}

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

  @Override
  public int hashCode() {
    return Objects.hash(this.enclosedNodeType, this.sourceFileName,
        Integer.valueOf(this.sourceFileIndex), Integer.valueOf(this.sourceSectionLength));
  }

  @Override
  void addWithIncrementalMean(final SingleSubAST arg) {
    assert arg.equals(this);
    incrementalMeanUnion(arg);
  }
}
