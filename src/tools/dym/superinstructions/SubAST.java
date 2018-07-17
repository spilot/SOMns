package tools.dym.superinstructions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.interpreter.nodes.SequenceNode;


class SubAST implements Comparable<SubAST> {
  /**
   * Traverses the AST under rootNode. Adds every SequenceNode to the given worklist. Returns
   * a SubAST representing the given AST. Filters all AST nodes that have sourceSection ==
   * null.
   */
  public static SubAST fromAST(final Node n, final List<Node> worklist,
      final Map<Node, BigInteger> rawActivations) {
    final List<Node> children = NodeUtil.findNodeChildren(n);

    if (n.getSourceSection() == null) {
      children.forEach(worklist::add);
      return null;
    }

    if (n instanceof SequenceNode) {
      children.forEach(worklist::add);
    }

    final List<SubAST> newChildren;

    if (n instanceof SequenceNode || children.isEmpty()) {
      newChildren = null;
    } else {
      newChildren = new ArrayList<>();
      final List<Node> innerWorklist = new ArrayList<>(children);
      do {
        final List<Node> templist = new ArrayList<>(innerWorklist);
        innerWorklist.removeAll(templist);
        templist.forEach((childNode) -> {
          if (childNode.getSourceSection() == null) {
            if (childNode instanceof SequenceNode) {
              children.forEach(worklist::add);
              newChildren.add(new SubAST(n, null,
                  rawActivations.get(childNode) == null ? 0L
                      : rawActivations.get(childNode).longValue()));
            } else {
              childNode.getChildren().forEach(innerWorklist::add);
            }
          } else {
            newChildren.add(fromAST(childNode, worklist, rawActivations));
          }
        });
      } while (!innerWorklist.isEmpty());
    }
    return new SubAST(n, newChildren,
        rawActivations.get(n) == null ? 0L
            : rawActivations.get(n).longValue());

  }

  public long         activations;
  public List<SubAST> children;

  public Node enclosedNode;

  public SubAST(final Node enclosedNode,
      final List<SubAST> children,
      final long activations) {
    this.children = children;
    this.enclosedNode = enclosedNode;
    this.activations = activations;
  }

  @Override
  public int compareTo(final SubAST arg0) {
    return (int) (arg0.score() - this.score());
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof SubAST)) {
      return false;
    }
    SubAST n = (SubAST) o;
    if ((this.isLeaf() && !n.isLeaf()) || (!this.isLeaf() && n.isLeaf())) {
      return false;
    }
    if (this.enclosedNode.getClass() != n.enclosedNode.getClass()) {
      return false;
    }

    // are both leaf nodes?
    if (((n.isLeaf() && this.isLeaf()))) {
      return true;
    }

    // now we need to compare the children.
    // are there equal numbers of children?
    if (this.children.size() != n.children.size()) {
      return false;
    }

    // there are equal numbers of children.
    // lets zip both lists to a list of pairs and compare the pairs

    class TreeNodePair {
      SubAST a;
      SubAST b;

      public TreeNodePair(final SubAST a, final SubAST b) {
        this.a = a;
        this.b = b;
      }

      public boolean areEqual() {
        return this.a.equals(this.b);
      }
    }

    return IntStream.range(0, this.children.size())
                    .mapToObj(
                        i -> new TreeNodePair(this.children.get(i),
                            n.children.get(i)))
                    .allMatch((pair) -> pair.areEqual());

  }

  public boolean isLeaf() {
    return this.children == null;
  }

  /**
   * @return true if this tree contains nodes with activations
   */
  public boolean isRelevant() {
    if (this.activations > 0) {
      return true;
    }
    if (isLeaf()) {
      return false;
    }
    return children.stream().anyMatch((child) -> child.isRelevant());
  }

  public int numberOfNodes() {
    if (isLeaf()) {
      return 1;
    } else {
      return 1 + children.stream().mapToInt((child) -> child.numberOfNodes()).sum();
    }
  }

  public long score() {
    return totalActivations() / numberOfNodes();
  }

  @Override
  public String toString() {
    return this.toStringRecursive(new StringBuilder(), "").toString();
  }

  public StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    accumulator.append(prefix)
               .append(enclosedNode)
               .append(": ")
               .append(activations)
               .append('\n');
    if (!isLeaf()) {
      children.forEach((child) -> child.toStringRecursive(accumulator, prefix + "  "));
    }
    return accumulator;
  }

  public long totalActivations() {
    if (isLeaf()) {
      return activations;
    } else {
      return activations
          + children.stream().mapToLong((child) -> child.totalActivations()).sum();
    }
  }

}
