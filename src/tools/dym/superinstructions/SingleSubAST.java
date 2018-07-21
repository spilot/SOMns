package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.interpreter.nodes.SequenceNode;


class SingleSubAST extends AbstractSubAST {
  /**
   * Traverses the AST under rootNode. Adds every SequenceNode to the given worklist. Returns
   * a SubAST representing the given AST. Filters all AST nodes that have sourceSection ==
   * null. Returns null if the given AST node has sourceSection == null
   */
  static SingleSubAST fromAST(final Node n, final Set<Node> worklist,
      final Map<Node, Map<String, Long>> rawActivations) {
    final List<Node> children = NodeUtil.findNodeChildren(n);

    if (n.getSourceSection() == null) {
      children.forEach(worklist::add);
      return null;
    }

    if (n instanceof SequenceNode) {
      children.forEach(worklist::add);
    }

    final List<SingleSubAST> newChildren;

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
              newChildren.add(new SingleSubAST(n, null,
                  rawActivations.get(childNode) == null ? new HashMap<>()
                      : rawActivations.get(childNode)));
            } else {
              childNode.getChildren().forEach(innerWorklist::add);
            }
          } else {
            newChildren.add(fromAST(childNode, worklist, rawActivations));
          }
        });
      } while (!innerWorklist.isEmpty());
    }
    return new SingleSubAST(n, newChildren,
        rawActivations.get(n) == null ? new HashMap<>()
            : rawActivations.get(n));

  }

  private Map<String, Long>  activationsByType;
  private List<SingleSubAST> children;

  private String                enclosedNodeString;
  private Class<? extends Node> enclosedNodeType;

  private SingleSubAST(final Node enclosedNode,
      final List<SingleSubAST> children,
      final Map<String, Long> activationsByType) {
    this.children = children;
    this.enclosedNodeType = enclosedNode.getClass();
    this.enclosedNodeString = enclosedNode.toString();
    this.activationsByType = activationsByType;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof SingleSubAST)) {
      return false;
    }
    SingleSubAST n = (SingleSubAST) o;
    if ((this.isLeaf() && !n.isLeaf()) || (!this.isLeaf() && n.isLeaf())) {
      return false;
    }
    if (this.enclosedNodeType != n.enclosedNodeType) {
      return false;
    }
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
      SingleSubAST a;
      SingleSubAST b;

      public TreeNodePair(final SingleSubAST a, final SingleSubAST b) {
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

  @Override
  public long score() {
    return totalActivations() / numberOfNodes();
  }

  @Override
  public String toString() {
    return this.toStringRecursive(new StringBuilder(), "").toString();
  }

  /**
   * @return true if this tree contains nodes with activations and is not a leaf
   */
  boolean isRelevant() {
    if (this.isLeaf()) {
      return false;
    }
    if (this.totalActivations() > 0) {
      return true;
    }
    return children.stream().anyMatch((child) -> child.isRelevant());
  }

  private int numberOfNodes() {
    if (isLeaf()) {
      return 1;
    } else {
      return 1 + children.stream().mapToInt(SingleSubAST::numberOfNodes).sum();
    }
  }

  private long totalActivations() {
    final long localActivations = activationsByType.values().stream().reduce(0L, Long::sum);
    if (isLeaf()) {
      return localActivations;
    } else {
      return localActivations
          + children.stream().mapToLong((child) -> child.totalActivations()).sum();
    }
  }

  @Override
  Stream<SingleSubAST> allSubASTs() {
    if (isLeaf()) {
      return Stream.empty();
    } else {
      return Stream.concat(this.children.stream().filter(AbstractSubAST::isNotLeaf),
          this.children.stream().flatMap(AbstractSubAST::allSubASTs));
    }
  }

  @Override
  Stream<VirtualSubAST> commonSubASTs(final AbstractSubAST arg) {
    if (arg instanceof CompoundSubAST) {
      return arg.commonSubASTs(this);
    }
    assert arg instanceof SingleSubAST;
    return this.allSubASTs().flatMap((mySubAST) -> {
      SingleSubAST[] theirMatchingSubASTs =
          arg.allSubASTs().filter((theirSubAST) -> theirSubAST.equals(mySubAST))
             .toArray(SingleSubAST[]::new);
      if (theirMatchingSubASTs.length > 0) {
        return Stream.of(new VirtualSubAST(mySubAST, theirMatchingSubASTs));
      } else {
        return Stream.empty();
      }
    });
  }

  @Override
  boolean isLeaf() {
    return this.children == null;
  }

  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    accumulator.append(prefix)
               .append(enclosedNodeString)
               .append(": ")
               .append(activationsByType)
               .append('\n');
    if (!isLeaf()) {
      children.forEach((child) -> child.toStringRecursive(accumulator, prefix + "  "));
    }
    return accumulator;
  }
}
