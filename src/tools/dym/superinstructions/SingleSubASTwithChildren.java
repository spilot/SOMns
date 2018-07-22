package tools.dym.superinstructions;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import com.oracle.truffle.api.nodes.Node;


class SingleSubASTwithChildren extends SingleSubAST {
  private Map<String, Long> activationsByType;
  private SingleSubAST[]    children;

  private String                enclosedNodeString;
  private Class<? extends Node> enclosedNodeType;

  SingleSubASTwithChildren(final Node enclosedNode,
      final SingleSubAST[] children,
      final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
    this.children = children;
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof SingleSubASTwithChildren)) {
      return false;
    }
    SingleSubASTwithChildren arg = (SingleSubASTwithChildren) obj;

    if (
    // obviously, the enclosed node types need to be equal
    this.enclosedNodeType != arg.enclosedNodeType
        // now we need to compare the children.
        // are there equal numbers of children?
        || this.children.length != arg.children.length) {
      return false;
    }
    // there are equal numbers of children.
    // lets compare them one by one
    for (int i = 0; i < children.length; i++) {
      if (!children[i].equals(arg.children[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public long score() {
    return totalActivations() / numberOfNodes();
  }

  /**
   * @return true if this tree contains nodes with activations and is not a leaf
   */
  @Override
  boolean isRelevant() {
    if (this.totalActivations() > 0) {
      return true;
    }
    return Arrays.stream(children).anyMatch((child) -> child.isRelevant());
  }

  @Override
  int numberOfNodes() {
    return 1 + Arrays.stream(children).mapToInt(SingleSubAST::numberOfNodes).sum();
  }

  @Override
  long totalActivations() {
    return super.totalActivations()
        + Arrays.stream(children).mapToLong((child) -> child.totalActivations()).sum();
  }

  @Override
  Stream<SingleSubAST> allSubASTs() {
    return Stream.concat(Arrays.stream(children).filter(AbstractSubAST::isNotLeaf),
        Arrays.stream(children).flatMap(AbstractSubAST::allSubASTs));
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    super.toStringRecursive(accumulator, prefix);
    for (SingleSubAST child : children) {
      child.toStringRecursive(accumulator, prefix + "  ");
    }
    return accumulator;
  }
}
