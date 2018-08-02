package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.interpreter.nodes.SequenceNode;


abstract class SingleSubAST extends AbstractSubAST {
  /**
   * Recursively traverses the AST under rootNode and constructs a new SubAST.
   * If this method encounters SequenceNodes, it will add them to the SubAST under
   * construction, but not their children. Instead, they will be added to the given worklist to
   * make the caller consider them as further root nodes.
   * If the first Node, n, has no source section (SourceSection == null), we add its children
   * are to
   * the worklist and we return null.
   * If we encounter a node with no source section in another place, we skip it by continuing
   * recursion on
   * its first child node with a source section.
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

    Map<String, Long> activationsByType = rawActivations.getOrDefault(n, new HashMap<>());

    if (n instanceof SequenceNode || children.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }

    // now we do the actual work: decide what our result subASTs children will be.
    final List<SingleSubAST> newChildren = new ArrayList<>();
    do {
      // creating this copy is necessary because there is no better way to iterate over a
      // list while appending elements. ListIterator adds them behind his back.
      final List<Node> templist = new ArrayList<>(children);
      children.removeAll(templist);
      templist.forEach((childNode) -> {
        if (childNode.getSourceSection() == null) {
          if (childNode instanceof SequenceNode) {
            children.forEach(worklist::add);
            newChildren.add(new SingleSubASTLeaf(n,
                rawActivations.getOrDefault(childNode, new HashMap<>())));
          } else {
            childNode.getChildren().forEach(children::add);
          }
        } else {
          // this is the "normal" case
          newChildren.add(fromAST(childNode, worklist, rawActivations));
        }
      });
    } while (!children.isEmpty());
    if (newChildren.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }
    return new SingleSubASTwithChildren(n,
        newChildren.toArray(new SingleSubAST[newChildren.size()]), activationsByType);
  }

  Map<String, Long>     activationsByType;
  String                enclosedNodeString;
  Class<? extends Node> enclosedNodeType;

  SingleSubAST(final Node enclosedNode,
      final Map<String, Long> activationsByType) {
    this.enclosedNodeType = enclosedNode.getClass();
    this.enclosedNodeString = enclosedNode.toString();
    this.activationsByType = activationsByType;
  }

  SingleSubAST(final SingleSubAST copyFrom) {
    enclosedNodeString = copyFrom.enclosedNodeString;
    enclosedNodeType = copyFrom.enclosedNodeType;
    activationsByType = copyFrom.activationsByType;
  }

  @Override
  public String toString() {
    return this.toStringRecursive(new StringBuilder(), "").toString();
  }

  @Override
  List<AbstractSubAST> commonSubASTs(final AbstractSubAST arg,
      final List<AbstractSubAST> accumulator) {
    this.forEachRelevantSubAST((mySubAST) -> {
      List<SingleSubAST> theirMatchingSubASTs = new ArrayList<>();
      arg.forEachRelevantSubAST((sAST) -> {
        if (sAST.equals(mySubAST)) {
          theirMatchingSubASTs.add(sAST);
        }
      });
      if (theirMatchingSubASTs.size() > 0) {
        accumulator.add(new VirtualSubAST(mySubAST, theirMatchingSubASTs));
      }
    });
    return accumulator;
  }

  /**
   * @return true if this tree contains nodes with activations and is not a leaf
   */
  abstract boolean isRelevant();

  abstract int numberOfNodes();

  long totalActivations() {
    return activationsByType.values().stream().reduce(0L, Long::sum);
  }

  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    accumulator.append(prefix)
               .append(enclosedNodeString)
               .append(": ")
               .append(activationsByType)
               .append('\n');
    return accumulator;
  }

  @Override
  public AbstractSubAST add(final AbstractSubAST arg) {
    if (arg instanceof CompoundSubAST) {
      return ((CompoundSubAST) arg).add(this);
    }
    return new CompoundSubAST(this).add(arg);
  }

  @Override
  public void forEach(final Consumer<SingleSubAST> action) {
    action.accept(this);
  }
}
