package tools.dym.superinstructions.improved;

import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


abstract class AbstractSubAST implements Serializable {

  private long                   score;
  private transient ScoreVisitor lastScoreVisitor;

  /**
   * For SubASTs A, B:
   * A.congruent(B)
   * <=> B.congruent(A)
   * <=> (A and B have the same structure
   * and for all SubAST node pairs (a in A, b in B)
   * (a, b have the same place in the structure of A/B resp. => a instanceof b))
   */
  final boolean congruent(final AbstractSubAST arg) {
    if (arg instanceof GroupedSubAST) {
      return this.congruent((GroupedSubAST) arg);
    }
    if (arg instanceof SingleSubAST) {
      return this.congruent((SingleSubAST) arg);
    }
    assert false;
    return false;
  }

  abstract boolean congruent(final GroupedSubAST arg);

  abstract boolean congruent(final SingleSubAST arg);

  @Override
  public final String toString() {
    assert false;
    return toStringRecursive(new StringBuilder(), "").toString();
  }

  abstract StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix);

  abstract List<AbstractSubAST> commonSubASTs(AbstractSubAST arg,
      List<AbstractSubAST> accumulator);

  abstract boolean isLeaf();

  /**
   * The higher a SubASTs score is, the more speedup should be achievable by replacing it with
   * a superinstruction.
   */
  final long score(final ScoreVisitor scoreVisitor) {
    if (!isScoreValid(scoreVisitor)) {
      score = computeScore(scoreVisitor);
      lastScoreVisitor = scoreVisitor;
    }
    return score;
  }

  final boolean isScoreValid(final ScoreVisitor scoreVisitor) {
    return lastScoreVisitor != null && lastScoreVisitor == scoreVisitor;
  }

  abstract long computeScore(ScoreVisitor scoreGiver);

  final void invalidateScore() {
    this.lastScoreVisitor = null;
  }

  final long getScore() {
    if (lastScoreVisitor == null) {
      return Long.MAX_VALUE;
    }
    return score;
  }

  abstract AbstractSubAST add(AbstractSubAST argument);

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
