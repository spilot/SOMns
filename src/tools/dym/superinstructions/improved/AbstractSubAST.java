package tools.dym.superinstructions.improved;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


abstract class AbstractSubAST implements Serializable {

  private long         score;
  private ScoreVisitor lastScoreVisitor = null;

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public abstract String toString();

  List<SingleSubAST> allSubASTs() {
    return allSubASTs(new ArrayList<>());
  }

  abstract List<SingleSubAST> allSubASTs(List<SingleSubAST> accumulator);

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

  public abstract AbstractSubAST add(AbstractSubAST argument);

  abstract void forEach(Consumer<SingleSubAST> action);
}
