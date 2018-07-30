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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public class CandidateWriter {
  static final String CANDIDATE_DATA_FILE_NAME = "/candidates.data";

  final String         metricsFolder;
  List<AbstractSubAST> olderSubASTs;

  final Map<Node, Map<String, Long>> rawActivations = new HashMap<>();

  @SuppressWarnings("unchecked")
  public CandidateWriter(final String metricsFolder) {
    this.metricsFolder = metricsFolder;
    try (ObjectInputStream ois =
        new ObjectInputStream(new FileInputStream(metricsFolder + CANDIDATE_DATA_FILE_NAME))) {
      final Object readObject = ois.readObject();
      this.olderSubASTs = (List<AbstractSubAST>) readObject;
    } catch (ClassCastException | IOException | ClassNotFoundException e) {
      System.out.println("No old candidate data found.");
      olderSubASTs = new ArrayList<>();
    }
  }

  synchronized void countActivation(final Node node, final String resultTypeName) {
    if (node != null) {
      rawActivations.putIfAbsent(node, new HashMap<>());
      rawActivations.get(node).compute(resultTypeName, (n, v) -> v == null ? 1L : v + 1L);
    }
  }

  public void filesOut(final Set<RootNode> rootNodes) {
    List<AbstractSubAST> subASTs = extractAllSubASTsOfRootNodes(olderSubASTs, rootNodes);

    // make list items unique by folding all equal ASTs into compound ASTs
    // in the process we also extract virtual ASTs, which are children of ASTs that exist in
    // more than one one AST and may have a larger score when combined, thus yielding
    // superinstruction candidates we should consider
    // we do this by creating an object. Yay for OOP.
    final SubASTListDeduplicator myDeduplicator = new SubASTListDeduplicator(subASTs);

    serializeASTsForFurtherSOMnsInvocations(myDeduplicator.getDeduplicatedASTs());

    List<AbstractSubAST> uniqueVirtualASts = myDeduplicator.getVirtualSubASTs();

    writeHumanReadableReport(myDeduplicator.getDeduplicatedASTs(), uniqueVirtualASts);
  }

  public ExecutionEventNodeFactory getExecutionEventNodeFactory() {
    return (context) -> new ExecutionEventNode() {
      @Override
      protected void onReturnValue(final VirtualFrame frame, final Object result) {
        countActivation(context.getInstrumentedNode(),
            result == null ? "null" : result.getClass().getSimpleName());
      }
    };
  }

  private class SubASTListDeduplicator {
    private final List<AbstractSubAST> inputWithoutDuplicates;
    private final List<AbstractSubAST> putVirtualSubASTsHere = new ArrayList<>();

    SubASTListDeduplicator(final List<AbstractSubAST> input) {
      inputWithoutDuplicates = deduplicate(input, true);
    }

    private List<AbstractSubAST> deduplicate(final List<AbstractSubAST> input,
        final boolean collectVirtualASTs) {
      AbstractSubAST[] ra = new AbstractSubAST[input.size()];
      ra = input.toArray(ra);

      for (int i = 0; i < ra.length - 1; i++) {
        if (ra[i] != null) {
          for (int j = i + 1; j < ra.length; j++) {
            if (ra[j] != null) {
              if (collectVirtualASTs) {
                putVirtualSubASTsHere.addAll(ra[i].commonSubASTs(ra[j]));
              }
              if (ra[i].equals(ra[j])) {
                ra[i] = ra[i].add(ra[j]);
                ra[j] = null;
              }
            }
          }
        }
      }

      // remove null elements from unique array by adding all non-null items to a list
      List<AbstractSubAST> uniqueASTs = new ArrayList<>();
      for (AbstractSubAST ast : ra) {
        if (ast != null) {
          uniqueASTs.add(ast);
        }
      }
      return uniqueASTs;
    }

    List<AbstractSubAST> getDeduplicatedASTs() {
      return inputWithoutDuplicates;
    }

    List<AbstractSubAST> getVirtualSubASTs() {
      return deduplicate(putVirtualSubASTsHere, false);
    }
  }

  private List<AbstractSubAST> extractAllSubASTsOfRootNodes(
      final List<AbstractSubAST> preExistingSubASTs,
      final Set<RootNode> rootNodes) {
    final Set<Node> worklist = new HashSet<>(rootNodes);
    do { // Set::forEach while modifying the Set is undefined behaviour, so we need this
      final Set<Node> tempSet = new HashSet<>(worklist);
      worklist.removeAll(tempSet);
      tempSet.forEach((rootNode) -> {
        final SingleSubAST result =
            // will also add all Nodes we should also consider as root nodes to the worklist
            SingleSubAST.fromAST(rootNode, worklist, rawActivations);
        if (result != null && result.isRelevant()) {
          preExistingSubASTs.add(result);
        }
      });
    } while (!worklist.isEmpty());
    return preExistingSubASTs;
  }

  private void serializeASTsForFurtherSOMnsInvocations(final List<AbstractSubAST> uniqueASTs) {
    try (ObjectOutputStream oos =
        new ObjectOutputStream(
            new FileOutputStream(this.metricsFolder + CANDIDATE_DATA_FILE_NAME))) {
      oos.writeObject(uniqueASTs);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void writeHumanReadableReport(final List<AbstractSubAST> uniqueASTs,
      final List<AbstractSubAST> uniqueVirtualASts) {
    // sort _all_ ASTs (including virtual) using the natural ordering specified by the
    // compareTo method
    uniqueASTs.addAll(uniqueVirtualASts);
    uniqueASTs.sort(null);

    final StringBuilder report = new StringBuilder();

    for (AbstractSubAST ast : uniqueASTs) {
      report.append(
          "===============================================================================\n");
      if (ast instanceof SingleSubASTwithChildren) {
        report.append(ast.score()).append(" activations:\n");
      }
      report.append(ast).append('\n');
    }

    Path reportPath = Paths.get(this.metricsFolder, "superinstruction-candidates-stefan.txt");
    try {
      Files.write(reportPath, report.toString().getBytes());
    } catch (IOException e) {
      throw new RuntimeException("Could not write superinstruction candidate report: " + e);
    }
  }

}
