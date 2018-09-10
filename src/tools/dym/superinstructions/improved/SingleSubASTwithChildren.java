package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class SingleSubASTwithChildren extends SingleSubAST {
  final SingleSubAST[] children;

  SingleSubASTwithChildren(final Node enclosedNode,
      final SingleSubAST[] children,
      final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
    this.children = children;
  }

  SingleSubASTwithChildren(final SingleSubAST copyFrom,
      final SingleSubAST[] children) {
    super(copyFrom);
    this.children = children;
  }

  @Override
  boolean congruent(final SingleSubAST obj) {
    if (obj instanceof SingleSubASTwithChildren) {
      final SingleSubASTwithChildren arg = (SingleSubASTwithChildren) obj;

      if (
      // obviously, the enclosed node types need to be equal
      this.enclosedNodeType != arg.enclosedNodeType
          // now we need to compare the children.
          // are there equal numbers of children?
          || this.children.length != arg.children.length) {
        return false;
      }
      // there are equal numbers of children.
      // lets compare them one by one
      for (int i = 0; i < children.length; i++) {
        if (!children[i].congruent(arg.children[i])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @return false if we can already be sure this subtree will never make a good
   *         superinstruction, true otherwise.
   */
  @Override
  boolean isRelevant() {
    boolean atLeastOneNonCutChild = false;
    for (final SingleSubAST child : children) {
      if (!(child instanceof CutSubAST)) {
        atLeastOneNonCutChild = true;
        break;
      }
    }
    return atLeastOneNonCutChild && (this.totalActivations() > this.numberOfNodes());
  }

  @Override
  int numberOfNodes() {
    int result = 1;
    for (final SingleSubAST child : children) {
      result += child.numberOfNodes();
    }
    return result;
  }

  @Override
  long totalActivations() {
    long result = totalLocalActivations;
    for (final SingleSubAST child : children) {
      result += child.totalActivations();
    }
    return result;
  }

  @Override
  void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {
    if (this.isRelevant() && totalLocalActivations > 0) {
      action.accept(this);
    }
    for (final SingleSubAST child : children) {
      child.forEachTransitiveRelevantSubAST(action);
    }
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    if (this.sourceSection != null) {
      accumulator.append(prefix)
                 .append(this.enclosedNodeType.getSimpleName())
                 .append('(')
                 .append(this.sourceSection)
                 .append("): ")
                 .append(activationsByType)
                 .append('\n');
    } else {
      accumulator.append(prefix)
                 .append(this.enclosedNodeType.getSimpleName())
                 .append('(')
                 .append(this.sourceFileName)
                 .append(' ')
                 .append(this.sourceFileIndex)
                 .append('-')
                 .append(sourceFileIndex + sourceSectionLength)
                 .append("): ")
                 .append(activationsByType)
                 .append('\n');
    }
    for (final SingleSubAST child : children) {
      child.toStringRecursive(accumulator, prefix + "  ");
    }
    return accumulator;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  ArrayList<SingleSubAST> getChildren() {
    final ArrayList<SingleSubAST> result = new ArrayList<>();
    for (final SingleSubAST child : children) {
      result.add(child);
    }
    return result;
  }

  @Override
  void addWithIncrementalMean(final SingleSubAST arg) {
    assert arg instanceof SingleSubASTwithChildren;
    assert arg.equals(this);
    incrementalMeanUnion(arg);

    final SingleSubASTwithChildren that = (SingleSubASTwithChildren) arg;
    assert this.children.length == that.children.length;
    for (int i = 0; i < children.length; i++) {
      children[i].addWithIncrementalMean(that.children[i]);
    }
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if ((that instanceof SingleSubASTwithChildren)) {
      final SingleSubASTwithChildren thatAST = (SingleSubASTwithChildren) that;
      if (!(this.enclosedNodeType == thatAST.enclosedNodeType
          && this.sourceFileIndex == thatAST.sourceFileIndex
          && this.sourceSectionLength == thatAST.sourceSectionLength
          && this.children.length == thatAST.children.length
          && this.sourceFileName.equals(thatAST.sourceFileName))) {
        return false;
      }
      for (int i = 0; i < children.length; i++) {
        if (!(this.children[i].equals(thatAST.children[i]))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
