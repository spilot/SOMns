package tools.dym.superinstructions.improved;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import som.interpreter.ReturnException;
import som.vm.VmSettings;


public class CandidateWriter {
  static final String CANDIDATE_DATA_FILE_NAME = "/candidates.data";

  final String metricsFolder;

  List<AbstractSubAST> olderSubASTs;

  final Map<Node, Map<String, Long>> rawActivations = new HashMap<>();

  @SuppressWarnings("unchecked")
  public CandidateWriter(final String metricsFolder) {
    this.metricsFolder = metricsFolder;
    try (ObjectInputStream ois =
        new ObjectInputStream(
            new FileInputStream(metricsFolder + CANDIDATE_DATA_FILE_NAME))) {
      final Object readObject = ois.readObject();
      this.olderSubASTs = (List<AbstractSubAST>) readObject;
    } catch (ClassCastException | IOException | ClassNotFoundException e) {
      // TODO replace sysouts with logger calls?
      System.out.println("No old candidate data found.");
      olderSubASTs = new ArrayList<>();
    }
  }

  public ExecutionEventNodeFactory getExecutionEventNodeFactory() {
    return (context) -> new ExecutionEventNode() {
      @Override
      protected void onReturnValue(final VirtualFrame frame, final Object result) {
        countActivation(context.getInstrumentedNode(),
            result == null ? "null" : result.getClass().getSimpleName());
      }

      @Override
      protected void onReturnExceptional(final VirtualFrame frame, final Throwable t) {
        if (t instanceof ReturnException) {
          countActivation(context.getInstrumentedNode(),
              String.valueOf(((ReturnException) t).result()));
        } else if (t instanceof UnexpectedResultException) {
          countActivation(context.getInstrumentedNode(),
              String.valueOf(((UnexpectedResultException) t).getResult()));
        } else {
          countActivation(context.getInstrumentedNode(), t.getClass().getSimpleName());
        }
      }
    };
  }

  synchronized void countActivation(final Node node, final String resultTypeName) {
    if (node != null) {
      rawActivations.putIfAbsent(node, new HashMap<>());
      rawActivations.get(node).compute(resultTypeName, (n, v) -> v == null ? 1L : v + 1L);
    }
  }

  public long getTotalActivations() {
    return rawActivations.values()
                         .stream()
                         .mapToLong(hm -> hm.values()
                                            .stream()
                                            .reduce(0L, Long::sum))
                         .reduce(0L, Long::sum);
  }

  public void filesOut(final Set<RootNode> rootNodes) {
    System.out.println("rootNodes.size()=" + rootNodes.size());

    final long total = getTotalActivations();

    final List<AbstractSubAST> subASTs =
        extractAllSubASTsOfRootNodes(olderSubASTs, rootNodes, total);

    System.out.println(subASTs.size() + " subASTs found.");

    // make list items unique by folding all congruent single ASTs into grouped ASTs.
    final List<AbstractSubAST> deduplicatedSubASTs = stackAllCongruentSubASTs(subASTs);

    System.out.println("Serializing " + deduplicatedSubASTs.size() + " grouped subASTs.");

    serializeASTsForFurtherSOMnsInvocations(deduplicatedSubASTs);

    if (VmSettings.WRITE_HUMAN_FRIENDLY_SUPERINSTRUCTION_REPORT) {
      // Extract virtual ASTs, which are children of ASTs that exist in
      // more than one one AST and may have a larger score when combined, thus yielding
      // possibly good superinstruction candidates.
      System.out.println("Writing report...");

      final List<AbstractSubAST> virtualSubASTs =
          stackAllCongruentSubASTs(collectVirtualSubASTs(deduplicatedSubASTs));

      System.out.println("collected " + virtualSubASTs.size() + " virtual subASTs");

      writeHumanReadableReport(virtualSubASTs,
          SubASTComparator.HIGHEST_ACTIVATIONS_SAVED_FIRST);

      writeHumanReadableReport(virtualSubASTs,
          SubASTComparator.HIGHEST_SHARE_OF_TOTAL_ACTIVATIONS_FIRST);

      writeHumanReadableReport(virtualSubASTs,
          SubASTComparator.HIGHEST_STATIC_FREQUENCY_FIRST);

    }
  }

  private static List<AbstractSubAST> stackAllCongruentSubASTs(
      final List<AbstractSubAST> input) {
    AbstractSubAST[] ra = input.toArray(new AbstractSubAST[input.size()]);
    for (int i = 0; i < ra.length - 1; i++) {
      if (ra[i] != null) {
        for (int j = /* we can start at i+1 because congruent is commutative */
            i + 1; j < ra.length; j++) {
          if (ra[j] != null) {
            if (ra[i].congruent(ra[j])) {
              ra[i] = ra[i].add(ra[j]);
              ra[j] = null;
            }
          }
        }
      }
    }
    // remove null elements from unique array by adding all non-null items to a list
    final List<AbstractSubAST> uniqueASTs = new ArrayList<>();
    for (final AbstractSubAST ast : ra) {
      if (ast != null) {
        uniqueASTs.add(ast);
      }
    }
    return uniqueASTs;
  }

  private static List<AbstractSubAST> collectVirtualSubASTs(
      final List<AbstractSubAST> input) {
    List<AbstractSubAST> result = new ArrayList<AbstractSubAST>();
    input.forEach(item -> item.forEachTransitiveRelevantSubAST(result::add));
    return result;
  }

  private List<AbstractSubAST> extractAllSubASTsOfRootNodes(
      final List<AbstractSubAST> preExistingSubASTs,
      final Set<RootNode> rootNodes,
      final long totalBenchmarkActivations) {
    final Set<Node> worklist = new HashSet<>(rootNodes);
    do { // Set::forEach while modifying the Set is undefined behaviour, so we need this
      final Set<Node> tempSet = new HashSet<>(worklist);
      worklist.removeAll(tempSet);
      tempSet.forEach((rootNode) -> {
        if (rawActivations.getOrDefault(rootNode, new HashMap<>())
                          .values().stream()
                          .reduce(0L, Long::sum) == 0
            || rootNode.getSourceSection() == null) {
          NodeUtil.findNodeChildren(rootNode).forEach(worklist::add);
        } else {
          final SingleSubAST result =
              // will also add all Nodes we should also consider as root nodes to the worklist
              SingleSubAST.fromAST(rootNode, worklist, rawActivations,
                  totalBenchmarkActivations);
          if (result != null && /*
                                 * calling isRelevant here drastically reduces complexity and
                                 * we
                                 * seem to not lose any good candidates
                                 */result.isRelevant()) {
            preExistingSubASTs.add(result);
          }
        }
      });
    } while (!worklist.isEmpty());
    return preExistingSubASTs;
  }

  private void serializeASTsForFurtherSOMnsInvocations(
      final List<AbstractSubAST> uniqueASTs) {
    try (ObjectOutputStream oos =
        new ObjectOutputStream(
            new FileOutputStream(this.metricsFolder + CANDIDATE_DATA_FILE_NAME))) {
      oos.writeObject(uniqueASTs);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void writeHumanReadableReport(final List<AbstractSubAST> uniqueASTs,
      final SubASTComparator scoringMethod) {

    uniqueASTs.sort(scoringMethod);

    final StringBuilder report = new StringBuilder();
    try (final Formatter formatter = new Formatter(report)) {
      for (final AbstractSubAST ast : uniqueASTs) {
        report.append(
            "===============================================================================\n")
              .append(scoringMethod.getDescription());

        formatter.format("%,f", ast.getScore());
        report.append("\n\n");

        ast.toStringRecursive(report, "");
        if (ast instanceof SingleSubAST) {
          report.append('\n');
        }
      }
    }

    final Path reportPath = Paths.get(this.metricsFolder,
        "superinstruction-candidates-stefan-" + scoringMethod.getSimpleName() + ".txt");
    try {
      Files.write(reportPath, report.toString().getBytes());
    } catch (IOException e) {
      throw new RuntimeException("Could not write superinstruction candidate report.", e);
    }
  }

}
