package tools.dym.superinstructions;

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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public class CandidateWriter {
  static final String  CANDIDATE_DATA_FILE_NAME = "/candidates.data";
  final String         metricsFolder;
  List<AbstractSubAST> olderSubASTs;

  final Map<Node, Long> rawActivations = new HashMap<>();

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

  public synchronized void countActivation(final Node node) {
    if (node != null) {
      rawActivations.compute(node, (n, v) -> v == null ? 1L : v + 1L);
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
                ra[i].commonSubASTs(ra[j]).forEach(putVirtualSubASTsHere::add);
              }
              if (ra[i].equals(ra[j])) {
                if (ra[i] instanceof SingleSubAST) {
                  if (ra[j] instanceof SingleSubAST) {
                    // case 1: both are SingleSubASTs
                    if (ra[i] != ra[j]) {
                      ra[i] = new CompoundSubAST((SingleSubAST) ra[i], (SingleSubAST) ra[j]);
                    }
                    ra[j] = null;
                  } else {
                    // case 2.1: ra[i] is Single, ra[j] is Compound
                    ((CompoundSubAST) ra[j]).add(ra[i]);
                    ra[i] = null;
                  }
                } else {
                  if (ra[j] instanceof SingleSubAST) {
                    // case 2.2: ra[j] is Single, ra[i] is Compound
                    assert ra[j] instanceof SingleSubAST;
                    ((CompoundSubAST) ra[i]).add(ra[j]);
                  } else {
                    // case 3: both are Compound
                    ((CompoundSubAST) ra[i]).add(ra[j]);
                  }
                  ra[j] = null;
                }
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
    final Set<Node> outerWorklist = new HashSet<>(rootNodes);
    do { // myList.forEach while modifying myList is undefined, so we need this
      final Set<Node> tempSet = new HashSet<>(outerWorklist);
      outerWorklist.removeAll(tempSet);
      tempSet.forEach((rootNode) -> {
        final List<Node> worklist = new ArrayList<>();
        final SingleSubAST result = SingleSubAST.fromAST(rootNode, worklist, rawActivations);
        outerWorklist.addAll(worklist);
        if (result != null && result.isRelevant()) {
          preExistingSubASTs.add(result);
        }
      });
    } while (!outerWorklist.isEmpty());
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
      if (ast instanceof SingleSubAST) {
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
