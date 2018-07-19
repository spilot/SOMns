package tools.dym.superinstructions;

import java.io.Serializable;


public abstract class AbstractSubAST implements Comparable<AbstractSubAST>, Serializable {
  public abstract int numberOfNodes();

  public abstract long totalActivations();

  /**
   * The higher a SubASTs score is, the more speedup should be achievable by replacing it with
   * a superinstruction.
   */
  public abstract long score();

  @Override
  public abstract String toString();

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public final int compareTo(final AbstractSubAST arg) {
    return (int) (arg.score() - this.score());
  }
}
