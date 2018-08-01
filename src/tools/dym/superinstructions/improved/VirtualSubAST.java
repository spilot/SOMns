package tools.dym.superinstructions.improved;

import java.util.List;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public class VirtualSubAST extends CompoundSubAST {
  protected final String ACTIVATIONS_STRING = " sum of mean activations (VIRTUAL):\n";

  public VirtualSubAST(final SingleSubAST head, final List<SingleSubAST> tail) {
    super(tail);
    this.enclosedNodes.add(head);
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof CompoundSubAST) {
      return this.getFirstNode().equals(((CompoundSubAST) o).getFirstNode());
    }
    assert o instanceof SingleSubAST;
    return this.getFirstNode().equals(o);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(this.getScore()).append(ACTIVATIONS_STRING);
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(sb.append("--\n"), "  ");
    });
    return sb.toString();
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }
}
