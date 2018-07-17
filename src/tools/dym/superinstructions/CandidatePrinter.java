package tools.dym.superinstructions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import tools.dym.Tags.AnyNode;


public class CandidatePrinter {

  public final Map<Node, BigInteger> rawActivations;

  public CandidatePrinter() {
    this.rawActivations = new HashMap<>();
  }

  public void onDynamicMetricDisposeNew(final Set<RootNode> rootNodes) {
    final List<SubAST> subASTs = new ArrayList<>();
    final Set<Node> outerWorklist = new HashSet<>(rootNodes);
    do { // myList.forEach while modifying myList is undefined, so we need this
      final Set<Node> tempSet = new HashSet<>(outerWorklist);
      outerWorklist.removeAll(tempSet);
      tempSet.forEach((rootNode) -> {
        final List<Node> worklist = new ArrayList<>();
        final SubAST result = SubAST.fromAST(rootNode, worklist, rawActivations);
        outerWorklist.addAll(worklist);
        if (result != null && result.isRelevant()) {
          subASTs.add(result);
        }
      });
    } while (!outerWorklist.isEmpty());

    // sort using the natural ordering specified by the compareTo method of
    // ASTStringWithActivations
    subASTs.sort(null);

    int i = 0;
    for (SubAST ast : subASTs) {
      if (i >= 5) {
        break;
      }
      i++;
      System.out.println("=============================");
      System.out.println(ast);
    }

    System.out.println(subASTs.get(2).equals(subASTs.get(1)));

  }

  // public void onDynamicMetricDispose(final Set<RootNode> rootNodes) {
  // final List<DecoratedSubASTString> subASTs = new ArrayList<>();
  // final Set<Node> outerWorklist = new HashSet<>(rootNodes);
  // do { // myList.forEach while modifying myList is undefined, so we need this
  // final Set<Node> tempSet = new HashSet<>(outerWorklist);
  // outerWorklist.removeAll(tempSet);
  // tempSet.forEach((rootNode) -> {
  // final StringBuilder accumulator = new StringBuilder();
  // final List<Node> worklist = new ArrayList<>();
  // final SubASTData result =
  // recursivelyWalkSubtreeCountActivationsAndBuildOutputString(rootNode, "",
  // accumulator, worklist);
  // outerWorklist.addAll(worklist);
  // if (result.subtreeContainedImportantNodes()) {
  // subASTs.add(new DecoratedSubASTString(accumulator.toString(), result));
  // }
  // });
  // } while (!outerWorklist.isEmpty());
  //
  // // sort using the natural ordering specified by the compareTo method of
  // // ASTStringWithActivations
  // subASTs.sort(null);
  //
  // int i = 0;
  // for (DecoratedSubASTString tree : subASTs) {
  // if (i >= 5) {
  // break;
  // }
  // i++;
  // System.out.println("=============================");
  // System.out.println(tree);
  // }
  // }

  public synchronized void countActivation(final Node node) {
    if (node != null) {
      rawActivations.compute(node,
          (n, v) -> v == null
              ? BigInteger.ONE
              : v.add(BigInteger.ONE));
    }
  }

  public void addBranchProfilingInstrumentation(final Instrumenter instrumenter) {
    final SourceSectionFilter filter =
        SourceSectionFilter.newBuilder().tagIs(AnyNode.class).build();
    ExecutionEventNodeFactory factory = (context) -> new ExecutionEventNode() {
      @Override
      protected void onEnter(final VirtualFrame frame) {
        countActivation(context.getInstrumentedNode());
      }
    };
    instrumenter.attachFactory(filter, factory);
  }

  // private class SubASTData
  // implements Comparable<SubASTData> {
  // public long activations;
  // public long numberOfNodes;
  // public long numberOfNodesThatHaveActivations;
  //
  // public SubASTData(final long activations,
  // final long numberOfNodesThatHaveActivations) {
  // this.numberOfNodes = 1L;
  // this.activations = activations;
  // this.numberOfNodesThatHaveActivations = numberOfNodesThatHaveActivations;
  // }
  //
  // public void add(final SubASTData summand) {
  // this.activations += summand.activations;
  // this.numberOfNodes += summand.numberOfNodes;
  // this.numberOfNodesThatHaveActivations += summand.numberOfNodesThatHaveActivations;
  // }
  //
  // public double score() {
  // return ((double) activations) / ((double) numberOfNodes);
  // }
  //
  // public boolean subtreeContainedImportantNodes() {
  // return numberOfNodesThatHaveActivations > 0;
  // }
  //
  // @Override
  // public int compareTo(final SubASTData arg) {
  // return (int) (arg.score() - this.score());
  // }
  //
  // }
  //
  // private class DecoratedSubASTString implements Comparable<DecoratedSubASTString> {
  // public final String tree;
  // public final SubASTData data;
  //
  // public DecoratedSubASTString(final String tree,
  // final SubASTData activations) {
  // this.tree = tree;
  // this.data = activations;
  // }
  //
  // /**
  // * @return How often this sub AST got activated relative to its size.
  // */
  // public long score() {
  // return (long) data.score();
  // }
  //
  // @Override
  // public int compareTo(final DecoratedSubASTString arg) {
  // return this.data.compareTo(arg.data);
  // }
  //
  // @Override
  // public String toString() {
  // return (new StringBuilder()).append(this.score())
  // .append('\n').append(tree)
  // .toString();
  // }
  // }
  //
  // private SubASTData recursivelyWalkSubtreeCountActivationsAndBuildOutputString(
  // final Node n,
  // final String prefix,
  // final StringBuilder accumulator,
  // final List<Node> worklist) {
  //
  // final String newPrefix;
  //
  // if (n.getSourceSection() != null) {
  // accumulator.append(prefix)
  // .append(n)
  // .append(": ")
  // .append(rawActivations.get(n))
  // .append('\n');
  // newPrefix = prefix + " ";
  // } else {
  // newPrefix = prefix;
  // }
  //
  // final List<Node> children = NodeUtil.findNodeChildren(n);
  //
  // if (n instanceof SequenceNode /*
  // * Maybe extend this with other types of nodes that are block
  // * headers?
  // */) {
  // children.forEach(worklist::add);
  // return new SubASTData(0L, 0L);
  // }
  //
  // final SubASTData result = new SubASTData(
  // rawActivations.get(n) == null ? 0L : rawActivations.get(n).longValue(),
  // n.getSourceSection() == null ? 0L : 1L);
  //
  // children.forEach((child) -> {
  // result.add(recursivelyWalkSubtreeCountActivationsAndBuildOutputString(child,
  // newPrefix, accumulator, worklist));
  // });
  // return result;
  // }
  //
}
