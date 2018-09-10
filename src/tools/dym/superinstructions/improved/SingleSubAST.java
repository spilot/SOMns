package tools.dym.superinstructions.improved;

import java.io.Serializable;
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
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNode;


abstract class SingleSubAST extends AbstractSubAST {

  private static boolean isControlFlowDividingNode(final Node n) {
    // TODO un-hardcode this somehow? Use tags?
    // TODO extend list of nodes?
    return n instanceof SequenceNode
        || n instanceof IfInlinedLiteralNode
        || n instanceof IfMessageNode
        || n instanceof IfTrueIfFalseInlinedLiteralsNode
        || n instanceof IfTrueIfFalseMessageNode;
  }

  /**
   * Recursively traverses the AST under rootNode and constructs a new SubAST.
   * If this method encounters SequenceNodes, they will be added to the SubAST under
   * construction, but their children will not. Instead, they will be added to the given
   * worklist to make the caller consider them as further root nodes.
   * If the first Node, n, has no source section (SourceSection == null), we add its children
   * to the aforementioned worklist and we return null, thus skipping the null node.
   * If we encounter a node with no source section in another place, we skip it by continuing
   * recursion on its first child node with a source section.
   */
  static SingleSubAST fromAST(final Node n, final Set<Node> worklist,
      final Map<Node, Map<String, Long>> rawActivations) {
    final List<Node> children = NodeUtil.findNodeChildren(n);

    if (n.getSourceSection() == null) {
      assert false; // TODO
      children.forEach(worklist::add);
      return null;
    }
    final Map<String, Long> activationsByType =
        rawActivations.getOrDefault(n, new HashMap<>());

    if (isControlFlowDividingNode(n)) {
      children.forEach(worklist::add);
      return new SingleSubASTLeaf(n, activationsByType);
    }
    if (children.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }

    // now we do the actual work: decide what our result subASTs children will be
    final List<SingleSubAST> newChildren = new ArrayList<>();

    while (!children.isEmpty()) {
      final Node childNode = children.remove(children.size() - 1); // removing last element
                                                                   // takes constant time in
                                                                   // ArrayList
      if (isControlFlowDividingNode(childNode)) {
        // consider all children of control flow divider as new root nodes
        childNode.getChildren().forEach(worklist::add);
        // add a leaf containing the control flow divider node to indicate that we pruned it
        newChildren.add(new CutSubAST(childNode,
            rawActivations.getOrDefault(childNode, new HashMap<>())));
      } else if (childNode.getSourceSection() == null) {
        // skip tree nodes with null SourceSections. Removes WrapperNodes etc. from result
        childNode.getChildren().forEach(children::add);
      } else {
        // this is the "normal case"
        newChildren.add(fromAST(childNode, worklist, rawActivations));
      }
    }

    if (newChildren.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType);
    }
    return new SingleSubASTwithChildren(n,
        // TODO PMD says this call to Collection::toArray may be optimizable
        newChildren.toArray(new SingleSubAST[newChildren.size()]),
        activationsByType);
  }

  final static class IncrementalAverage implements Serializable {
    private long avg;
    private int  N;

    IncrementalAverage(final long init) {
      avg = init;
      N = 1;
    }

    IncrementalAverage(final IncrementalAverage copyFrom) {
      this.avg = copyFrom.avg;
      this.N = copyFrom.N;
    }

    long get() {
      return avg;
    }

    void add(final long x) {
      System.out.print("AVG(" + avg + "," + x + ")=");
      avg += (x - avg) / ++N;
      System.out.println(avg);
    }

    void merge(final IncrementalAverage other) {
      if (other.avg == avg) {
        N += other.N;
      } else {
        do {
          add(other.avg);
          other.N--;
        } while (other.N >= 0);
      }
    }

    @Override
    public String toString() {
      return (Long.valueOf(get())).toString();
    }
  }

  Class<? extends Node>           enclosedNodeType;
  String                          sourceFileName;
  int                             sourceFileIndex;
  int                             sourceSectionLength;
  transient SourceSection         sourceSection;
  Map<String, IncrementalAverage> activationsByType;
  long                            totalLocalActivations;

  SingleSubAST(final Node enclosedNode,
      final Map<String, Long> activationsByType) {
    super();
    this.enclosedNodeType = enclosedNode.getClass();
    this.activationsByType = new HashMap<>();
    activationsByType.entrySet()
                     .forEach((entry) -> this.activationsByType.put(entry.getKey(),
                         new IncrementalAverage(entry.getValue())));
    this.sourceFileName = enclosedNode.getSourceSection().getSource().getName();
    this.sourceFileIndex = enclosedNode.getSourceSection().getCharIndex();
    this.sourceSectionLength = enclosedNode.getSourceSection().getCharLength();
    this.sourceSection = enclosedNode.getSourceSection();
    updateLocalActivationsCache();
  }

  SingleSubAST(final SingleSubAST copyFrom) {
    super();
    this.activationsByType = new HashMap<>();
    copyFrom.activationsByType.entrySet()
                              .forEach((entry) -> this.activationsByType.put(
                                  entry.getKey(),
                                  new IncrementalAverage(entry.getValue())));
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
            // TODO do we actually ever enter this branch?
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
        // TODO this line often adds SingleSubASTs to GroupedSubASTs which already contain them
        // (contain identical subasts)

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
    assert arg instanceof SingleSubAST;
    if (arg.equals(this)) {
      addWithIncrementalMean((SingleSubAST) arg);
      return this;
    }
    return new CompoundSubAST(this).add(arg);
  }

  @Override
  void forEachDirectSubAST(final Consumer<SingleSubAST> action) {
    action.accept(this);
  }

  @Override
  final boolean congruent(final GroupedSubAST arg) {
    // leave unpacking their first node to them
    return arg.congruent(this);
  }

  @Override
  public abstract boolean equals(Object that);

  final void incrementalMeanUnion(final SingleSubAST that) {
    for (final String type : this.activationsByType.keySet()) {
      if (that.activationsByType.containsKey(type)) {
        this.activationsByType.get(type).merge(that.activationsByType.get(type));
      }
    }
    for (final String type : that.activationsByType.keySet()) {
      if (!this.activationsByType.containsKey(type)) {
        activationsByType.put(type, that.activationsByType.get(type));
      }
    }
    updateLocalActivationsCache();
  }

  /**
   * Calling this method implies that *this* and arg are equal.
   */
  abstract void addWithIncrementalMean(final SingleSubAST that);

  private void updateLocalActivationsCache() {
    this.totalLocalActivations =
        activationsByType.values()
                         .stream()
                         .mapToLong(IncrementalAverage::get)
                         .reduce(0L, Long::sum);
  }

}
