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

  public List<AbstractSubAST> onDynamicMetricDispose(final Set<RootNode> rootNodes) {
    final List<AbstractSubAST> subASTs = new ArrayList<>();
    final Set<Node> outerWorklist = new HashSet<>(rootNodes);
    do { // myList.forEach while modifying myList is undefined, so we need this
      final Set<Node> tempSet = new HashSet<>(outerWorklist);
      outerWorklist.removeAll(tempSet);
      tempSet.forEach((rootNode) -> {
        final List<Node> worklist = new ArrayList<>();
        final SingleSubAST result = SingleSubAST.fromAST(rootNode, worklist, rawActivations);
        outerWorklist.addAll(worklist);
        if (result != null && result.isRelevant()) {
          subASTs.add(result);
        }
      });
    } while (!outerWorklist.isEmpty());

    // make list items unique by folding all equal ASTs into compound ASTs
    AbstractSubAST[] ra = new AbstractSubAST[subASTs.size()];
    ra = subASTs.toArray(ra);

    for (int i = 0; i < ra.length - 1; i++) {
      if (ra[i] != null) {
        for (int j = i + 1; j < ra.length; j++) {
          if (ra[j] != null) {
            if (ra[i].equals(ra[j])) {
              if (ra[i] instanceof CompoundSubAST) {
                ((CompoundSubAST) ra[i]).add((SingleSubAST) ra[j]);
                ra[j] = null;
              } else {
                CompoundSubAST newAST =
                    new CompoundSubAST((SingleSubAST) ra[i], (SingleSubAST) ra[j]);
                ra[i] = newAST;
                ra[j] = null;
              }
            }
          }
        }
      }
    }

    List<AbstractSubAST> uniqueASTs = new ArrayList<>();
    for (AbstractSubAST ast : ra) {
      if (ast != null) {
        uniqueASTs.add(ast);
      }
    }

    // sort using the natural ordering specified by the compareTo method
    uniqueASTs.sort(null);

    int i = 0;
    for (AbstractSubAST ast : uniqueASTs) {
      if (i >= 10) {
        break;
      }
      i++;
      System.out.println("=============================");
      System.out.println(ast.score());
      System.out.println(ast);
    }

    return uniqueASTs;
  }

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
}
