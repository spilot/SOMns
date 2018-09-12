package tools.dym.superinstructions.improved;

import java.io.Serializable;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


abstract class AbstractSubAST implements Serializable {

  private double                 score;
  private transient ScoreVisitor lastScoreVisitor;

  /**
   * For SubASTs A, B:
   * A.congruent(B) means:
   * A and B have the same structure
   * and for all SubAST node pairs (a in A, b in B)
   * (a, b have the same place in the structure of A/B resp. => a instanceof b))
   *
   * congruent is symmetric, transitive and reflexive.
   */
  final boolean congruent(final AbstractSubAST arg) {
    if (arg instanceof CompoundSubAST) {
      return this.congruent((CompoundSubAST) arg);
    }
    if (arg instanceof SingleSubAST) {
      return this.congruent((SingleSubAST) arg);
    }
    assert false;
    return false;
  }

  abstract boolean congruent(final CompoundSubAST arg);

  abstract boolean congruent(final SingleSubAST arg);

  @Override
  public final String toString() {
    // TODO assert false;
    return toStringRecursive(new StringBuilder(), "").toString();
  }

  abstract StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix);

  abstract boolean isLeaf();

  /**
   * The higher a SubASTs score is, the more speedup should be achievable by replacing it with
   * a superinstruction.
   */
  final double score(final ScoreVisitor scoreVisitor) {
    if (!isScoreValid(scoreVisitor)) {
      score = computeScore(scoreVisitor);
      lastScoreVisitor = scoreVisitor;
    }
    return score;
  }

  final boolean isScoreValid(final ScoreVisitor scoreVisitor) {
    return lastScoreVisitor != null && lastScoreVisitor == scoreVisitor;
  }

  abstract double computeScore(ScoreVisitor scoreGiver);

  final void invalidateScore() {
    this.lastScoreVisitor = null;
  }

  final double getScore() {
    if (lastScoreVisitor == null) {
      return Double.MAX_VALUE;
    }
    return score;
  }

  abstract AbstractSubAST add(AbstractSubAST argument);

  // abstract AbstractSubAST addWithIncrementalMean(final AbstractSubAST arg);

  /**
   * Iterates over the elements of a compoundSubAST and executes action.
   * For SingleSubAST, calls action on this.
   */
  abstract void forEachDirectSubAST(Consumer<SingleSubAST> action);

  /**
   * Traverses the whole tree and executes for every subAST there is.
   */
  abstract void forEachTransitiveRelevantSubAST(Consumer<SingleSubAST> action);
}
