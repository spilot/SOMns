package tools.dym.superinstructions;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;


abstract class AbstractSubAST implements Comparable<AbstractSubAST>, Serializable {

  @Override
  public final int compareTo(final AbstractSubAST arg) {
    return (int) (arg.score() - this.score());
  }

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public abstract String toString();

  List<SingleSubAST> allSubASTs() {
    return allSubASTs(new LinkedList<>());
  }

  abstract List<SingleSubAST> allSubASTs(List<SingleSubAST> accumulator);

  List<VirtualSubAST> commonSubASTs(final AbstractSubAST arg) {
    return commonSubASTs(arg, new LinkedList<>());
  }

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
  abstract long score();
}
