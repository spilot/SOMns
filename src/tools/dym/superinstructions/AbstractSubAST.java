package tools.dym.superinstructions;

import java.io.Serializable;

public abstract class AbstractSubAST implements Comparable<AbstractSubAST>, Serializable {
  public abstract int numberOfNodes();

  public abstract long score();

  public abstract long totalActivations();

  @Override
  public abstract String toString();

  @Override
  public abstract boolean equals(Object arg);

  @Override
  public final int compareTo(final AbstractSubAST arg) {
    return (int) (arg.score() - this.score());
  }
}
