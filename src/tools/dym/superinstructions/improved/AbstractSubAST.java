package tools.dym.superinstructions.improved;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


abstract class AbstractSubAST implements Comparable<AbstractSubAST>, Serializable {

  long            score;
  private boolean isScoreValid = false;

  @Override
  public final int compareTo(final AbstractSubAST arg) {
    return (int) (arg.score() - this.score());
  }

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public abstract String toString();

  List<SingleSubAST> allSubASTs() {
    return allSubASTs(new ArrayList<>());
  }

  abstract List<SingleSubAST> allSubASTs(List<SingleSubAST> accumulator);

  // List<VirtualSubAST> commonSubASTs(final AbstractSubAST arg) {
  // return commonSubASTs(arg, new ArrayList<>());
  // }

  abstract List<VirtualSubAST> commonSubASTs(AbstractSubAST arg,
      List<VirtualSubAST> accumulator);

  abstract boolean isLeaf();

  final boolean isNotLeaf() {
    return !isLeaf();
  }

  /**
   * The higher a SubASTs score is, the more speedup should be achievable by replacing it with
   * a superinstruction.
   */
  final long score() {
    if (!isScoreValid) {
      computeScore();
      isScoreValid = true;
    }
    return score;
  }

  final void invalidateScore() {
    this.isScoreValid = false;
  }

  abstract void computeScore();

  public abstract AbstractSubAST add(AbstractSubAST argument);

  abstract void forEach(Consumer<SingleSubAST> action);
}
