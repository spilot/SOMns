package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.SequenceNode;


abstract class SingleSubAST extends AbstractSubAST {
  /**
   * Recursively traverses the AST under rootNode and constructs a new SubAST.
   * If this method encounters SequenceNodes, it will add them to the SubAST under
   * construction, but not their children. Instead, they will be added to the given worklist to
   * make the caller consider them as further root nodes.
   * If the first Node, n, has no source section (SourceSection == null), we add its children
   * to the aforementioned worklist and we return null, thus skipping the null node.
   * If we encounter a node with no source section in another place, we skip it by continuing
   * recursion on its first child node with a source section.
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

    final Map<String, Long> activationsByType =
        rawActivations.getOrDefault(n, new HashMap<>());

    if (n instanceof SequenceNode || children.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }

    // now we do the actual work: decide what our result subASTs children will be.
    final List<SingleSubAST> newChildren = new ArrayList<>();

    while (!children.isEmpty()) {
      final Node childNode = children.remove(children.size() - 1); // removing last element
                                                                   // takes
      // constant time in ArrayList
      if (childNode instanceof SequenceNode /*
                                             * TODO define more nodes as
                                             * "control flow dividers"?
                                             */) {
        // consider all SequenceNode children as new root nodes
        childNode.getChildren().forEach(worklist::add);
        // add a leaf containing the SequenceNode to indicate that we pruned it
        newChildren.add(new CutSubAST(childNode,
            rawActivations.getOrDefault(childNode, new HashMap<>())));
      } else if (childNode.getSourceSection() == null) {
        // skip tree nodes with null SourceSections. This removes WrapperNodes etc. from our
        // superinstruction candidates
        childNode.getChildren().forEach(children::add);
      } else {
        newChildren.add(fromAST(childNode, worklist, rawActivations));
      }
    }
    if (newChildren.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }
    return new SingleSubASTwithChildren(n,
        // TODO this call to Collection::toArray may be optimizable
        newChildren.toArray(new SingleSubAST[newChildren.size()]), activationsByType);
  }

  Map<String, Long>       activationsByType;
  String                  sourceFileName;
  int                     sourceFileIndex;
  int                     sourceSectionLength;
  Class<? extends Node>   enclosedNodeType;
  transient SourceSection sourceSection;
  long                    totalLocalActivations;

  SingleSubAST(final Node enclosedNode,
      final Map<String, Long> activationsByType) {
    super();
    this.enclosedNodeType = enclosedNode.getClass();
    this.activationsByType = activationsByType;
    this.sourceFileName = enclosedNode.getSourceSection().getSource().getName();
    this.sourceFileIndex = enclosedNode.getSourceSection().getCharIndex();
    this.sourceSectionLength = enclosedNode.getSourceSection().getCharLength();
    this.sourceSection = enclosedNode.getSourceSection();
  }

  SingleSubAST(final SingleSubAST copyFrom) {
    super();
    this.activationsByType = copyFrom.activationsByType;
    this.sourceFileName = copyFrom.sourceFileName;
    this.sourceFileIndex = copyFrom.sourceFileIndex;
    this.sourceSectionLength = copyFrom.sourceSectionLength;
    this.enclosedNodeType = copyFrom.enclosedNodeType;
    this.sourceSection = copyFrom.sourceSection;
    this.totalLocalActivations = copyFrom.totalLocalActivations;
  }

  @Override
  List<AbstractSubAST> commonSubASTs(final AbstractSubAST arg,
      final List<AbstractSubAST> accumulator) {
    this.forEachTransitiveRelevantSubAST((mySubAST) -> {
      // search for item congruent to the result in accumulator to avoid allocating a new list
      // that will be merged later
      VirtualSubAST match = null; // TODO DD-anomaly
      for (final AbstractSubAST a : accumulator) {
        if (mySubAST.congruent(a)) {
          if (a instanceof VirtualSubAST) {
            match = (VirtualSubAST) a;
          } else if (a instanceof CompoundSubAST) {
            // TODO can we actually ever enter this branch?
            assert false;
            match = new VirtualSubAST((CompoundSubAST) a);
          } else {
            assert a instanceof SingleSubAST;
            assert false;
            match = new VirtualSubAST((SingleSubAST) a);
          }
          accumulator.remove(a);
          break;
        }
      }
      final VirtualSubAST result;
      if (match == null) {
        result = new VirtualSubAST(mySubAST);
      } else {
        result = (VirtualSubAST) match.add(mySubAST);
      }

      arg.forEachTransitiveRelevantSubAST((sAST) -> {
        if (sAST.congruent(mySubAST)) {
          result.add(sAST);
        }
      });
      if (result.enclosedNodes.size() > 1) {
        accumulator.add(result);
      }
    });
    return accumulator;
  }

  /**
   * @return false if we can be sure this tree will under no circumstances make a good
   *         superinstruction, otherwise true
   */
  abstract boolean isRelevant();

  abstract int numberOfNodes();

  abstract long totalActivations();

  @Override
  AbstractSubAST add(final AbstractSubAST arg) {
    if (arg instanceof CompoundSubAST) {
      return ((CompoundSubAST) arg).add(this);
    }
    return new CompoundSubAST(this).add(arg);
  }

  @Override
  public void forEachDirectSubAST(final Consumer<SingleSubAST> action) {
    action.accept(this);
  }

  @Override
  final boolean congruent(final GroupedSubAST arg) {
    // leave unpacking their first node to them
    return arg.congruent(this);
  }

  @Override
  public abstract boolean equals(Object that);
}
