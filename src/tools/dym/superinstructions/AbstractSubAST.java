package tools.dym.superinstructions;

import java.io.Serializable;
import java.util.stream.Stream;


abstract class AbstractSubAST implements Comparable<AbstractSubAST>, Serializable {

  @Override
  public final int compareTo(final AbstractSubAST arg) {
    return (int) (arg.score() - this.score());
  }

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public abstract String toString();

  abstract Stream<SingleSubAST> allSubASTs();

  abstract Stream<VirtualSubAST> commonSubASTs(AbstractSubAST arg);

  abstract boolean isLeaf();

  final boolean isNotLeaf() {
    return !isLeaf();
  }

  /**
   * The higher a SubASTs score is, the more speedup should be achievable by replacing it with
   * a superinstruction.
   */
  abstract long score();
}
