package tools.dym.superinstructions.improved;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNode;


abstract class SingleSubAST extends AbstractSubAST {

  private static boolean isControlflowDividingNode(final Node n) {
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
  static SingleSubAST fromAST(final Node maybeWrappedNode,
      final Consumer<Node> considerAsNewRoot,
      final Map<Node, Map<String, Long>> rawActivations,
      final long totalBenchmarkActivations) {

    final Node n = SOMNode.unwrapIfNecessary(maybeWrappedNode);

    final List<Node> children =
        NodeUtil.findNodeChildren(n)
                .stream()
                .map(node -> SOMNode.unwrapIfNecessary(node))
                .collect(Collectors.toList());

    assert n.getSourceSection() != null;

    final Map<String, Long> activationsByType =
        rawActivations.getOrDefault(n, new HashMap<>());

    if (isControlflowDividingNode(n)) {
      children.forEach(considerAsNewRoot);
      return new CutSubAST(n, activationsByType, totalBenchmarkActivations);
    }
    if (children.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType, totalBenchmarkActivations);
    }

    // now we do the actual work: decide what our result subASTs children will be
    final List<SingleSubAST> newChildren = new ArrayList<>();

    while (!children.isEmpty()) {
      final Node childNode = children.remove(children.size() - 1); // removing last element
                                                                   // takes constant time in
                                                                   // ArrayList
      if (isControlflowDividingNode(childNode)) {
        // consider all children of control flow dividers as new root nodes
        childNode.getChildren().forEach(considerAsNewRoot);
        // add a leaf containing the control flow divider node to indicate that we pruned it
        newChildren.add(new CutSubAST(childNode,
            rawActivations.getOrDefault(childNode, new HashMap<>()),
            totalBenchmarkActivations));
      } else if (childNode.getSourceSection() == null) {
        // skip tree nodes with null SourceSections. Removes WrapperNodes etc. from result
        childNode.getChildren().forEach(children::add);
      } else {
        // this is the "normal case"
        newChildren.add(
            fromAST(childNode, considerAsNewRoot, rawActivations, totalBenchmarkActivations));
      }
    }

    if (newChildren.isEmpty()) {
      return new SingleSubASTLeaf(n, activationsByType, totalBenchmarkActivations);
    }
    final SingleSubASTwithChildren result = new SingleSubASTwithChildren(n,
        // TODO PMD says this call to Collection::toArray may be optimizable
        newChildren.toArray(new SingleSubAST[newChildren.size()]),
        activationsByType,
        totalBenchmarkActivations);
    if (result.totalActivations() == 0) { // TODO keep this?
      return new CutSubAST(result);
    }
    return result;
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
      avg += (x - avg) / ++N;
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
  IncrementalAverage              totalBenchmarkActivations;

  SingleSubAST(final Node enclosedNode,
      final Map<String, Long> activationsByType, final long totalBenchmarkActivations) {
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
    this.totalBenchmarkActivations = new IncrementalAverage(totalBenchmarkActivations);
    updateLocalActivationsCache();
  }

  SingleSubAST(final SingleSubAST copyFrom) {
    super(copyFrom);
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
    this.totalBenchmarkActivations = copyFrom.totalBenchmarkActivations;
  }

  SingleSubAST(final SingleSubAST copyFrom, final Map<String, Long> activationsByType2) {
    super(copyFrom);
    this.sourceFileName = copyFrom.sourceFileName;
    this.sourceFileIndex = copyFrom.sourceFileIndex;
    this.sourceSectionLength = copyFrom.sourceSectionLength;
    this.enclosedNodeType = copyFrom.enclosedNodeType;
    this.sourceSection = copyFrom.sourceSection;
    this.totalLocalActivations = copyFrom.totalLocalActivations;
    this.totalBenchmarkActivations = copyFrom.totalBenchmarkActivations;
    this.activationsByType = new HashMap<>();
    activationsByType2.entrySet()
                      .forEach((entry) -> this.activationsByType.put(
                          entry.getKey(), new IncrementalAverage(entry.getValue())));
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
    assert this.congruent(arg);
    if (arg instanceof CongruentSubASTs) {
      return ((CongruentSubASTs) arg).add(this);
    }
    assert arg instanceof SingleSubAST;
    if (arg.equals(this)) {
      addWithIncrementalMean((SingleSubAST) arg);
      return this;
    }
    return new CongruentSubASTs(this).add(arg);
  }

  @Override
  void forEachDirectSubAST(final Consumer<SingleSubAST> action) {
    action.accept(this);
  }

  @Override
  final boolean congruent(final CongruentSubASTs arg) {
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
    totalBenchmarkActivations.merge(that.totalBenchmarkActivations);
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
