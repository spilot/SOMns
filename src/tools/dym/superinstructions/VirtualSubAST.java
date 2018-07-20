package tools.dym.superinstructions;

public class VirtualSubAST extends CompoundSubAST {
  protected final String ACTIVATIONS_STRING = " sum of mean activations (VIRTUAL):\n";

  public VirtualSubAST(final SingleSubAST head, final SingleSubAST... tail) {
    super(tail);
    this.enclosedNodes.add(head);
  }

  @Override
  public boolean equals(final Object o) {
    return (o instanceof VirtualSubAST)
        && this.getFirstNode().equals(((VirtualSubAST) o).getFirstNode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(this.score()).append(ACTIVATIONS_STRING);
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(sb.append("--\n"), "  ");
    });
    return sb.toString();
  }
}
