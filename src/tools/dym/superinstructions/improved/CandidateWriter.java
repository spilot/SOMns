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
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

import som.vm.VmSettings;


public class CandidateWriter {
  static final String CANDIDATE_DATA_FILE_NAME = "/candidates.data";

  final String         metricsFolder;
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
      // TODO replace with logger call?
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
    };
  }

  synchronized void countActivation(final Node node, final String resultTypeName) {
    if (node != null) {
      rawActivations.putIfAbsent(node, new HashMap<>());
      rawActivations.get(node).compute(resultTypeName, (n, v) -> v == null ? 1L : v + 1L);
    }
  }

  public void filesOut(final Set<RootNode> rootNodes) {
    System.out.println("rootNodes.size()=" + rootNodes.size());
    final List<AbstractSubAST> subASTs =
        extractAllSubASTsOfRootNodes(olderSubASTs, rootNodes);
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
          SubASTComparator.HIGHEST_STATIC_FREQUENCY_FIRST);

      System.out.println(collectPrunedSubASTs(virtualSubASTs));
      System.out.println(similarHistogram);
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
    final List<AbstractSubAST> result = new ArrayList<>();
    AbstractSubAST[] ra = input.toArray(new AbstractSubAST[input.size()]);
    for (int i = 0; i < ra.length - 1/*
                                      * -1 because we don't need to compare the last element
                                      * to itself
                                      */; i++) {
      assert ra[i] != null;
      for (int j = i + 1 /*
                          * starting at i+1 is correct because AbstractSubAST::commonSubASTs
                          * is commutative
                          */; j < ra.length; j++) {
        assert ra[j] != null;
        ra[i].commonSubASTs(ra[j], result);
      }
    }
    result.addAll(input);
    return result;
  }

  static Map<Integer, Integer> similarHistogram = new HashMap<>();

  private static List<AbstractSubAST> collectPrunedSubASTs(
      final List<AbstractSubAST> input) {
    List<AbstractSubAST> result = new ArrayList<>();
    // make sure similar is a transitive, reflexive and symmetric relation
    // take head
    // collect list of all items similar to head
    AbstractSubAST[] ra = input.toArray(new AbstractSubAST[input.size()]);
    for (int i = 0; i < ra.length - 1; i++) {
      if (ra[i] != null) {
        final List<AbstractSubAST> similarToI = new ArrayList<>();
        for (int j = i + 1; j < ra.length; j++) {
          if (ra[j] != null
              && ((ra[i] instanceof GroupedSubAST
                  && ((GroupedSubAST) ra[i]).similar(ra[j]))
                  || (ra[i] instanceof SingleSubASTwithChildren
                      && ((SingleSubASTwithChildren) ra[i]).similar(ra[j])))) {
            similarToI.add(ra[j]);
            ra[j] = null;
          }
        }
        if (!similarToI.isEmpty()) {
          similarToI.add(ra[i]);
          similarHistogram.merge(similarToI.size(), 1, Integer::sum);
          if (similarToI.size() > 31) {
            System.out.println("similarToI.size()=" + similarToI.size());
          } else {
            gospersHack(2, similarToI.size(), 0, combination -> {

              Optional<VirtualSubAST> maybe =
                  similarToI.get(Integer.numberOfLeadingZeros(combination))
                            .commonPart(similarToI.get(similarToI.size()
                                - (Integer.numberOfTrailingZeros(combination) + 1)));
              if (maybe.isPresent()) {
                findLocalMaxima(similarToI, maybe.get(), combination, 2, result);
              }
            });
          }
        }
      }
    }
    return result;

  }

  static void findLocalMaxima(final List<AbstractSubAST> set,
      final VirtualSubAST currentCombination,
      final int head, final int popCount, final List<AbstractSubAST> localMaxima) {
    // Can we add an element to currentCombination so that the number of saved activations
    // increases?
    // if yes, recurse for each such element
    // if not, current combination is a local maximum.
    int previousSize = localMaxima.size();

    // iterate over all ways to add one element to the current combination
    for (int bit = 1; bit <= (1 << set.size() - 1); bit <<= 1) {
      if ((head | bit) != head) {
        final Optional<VirtualSubAST> maybeCommonPart =
            currentCombination.commonPart(set.get(Integer.numberOfLeadingZeros(bit)));
        if (maybeCommonPart.isPresent()
            && SubASTComparator.HIGHEST_ACTIVATIONS_SAVED_FIRST.compare(
                currentCombination,
                maybeCommonPart.get()) >= 0) {
          findLocalMaxima(set, maybeCommonPart.get(), head | bit, popCount + 1,
              localMaxima);
        }
      }
    }

    if (localMaxima.size() == previousSize && popCount > 1) {
      localMaxima.add(currentCombination);
    }
  }

  /**
   * Call a for every n-bit value that has k bits set to 1, starting at s (if s != 0).
   *
   * Source http://programmingforinsomniacs.blogspot.com/2018/03/gospers-hack-explained.html
   */
  private static void gospersHack(final int k, final int n, final int s,
      final IntConsumer a) {
    assert s == 0 || popCount(s) == k;
    assert k < 31;
    int set = s == 0 ? (1 << k) - 1 : s;
    int limit = (1 << n);
    while (set < limit) {
      a.accept(set);

      // Gosper's hack:
      int c = set & -set;
      int r = set + c;
      set = (((r ^ set) >> 2) / c) | r;
    }
  }

  /**
   * Counts the number of set bits in an int.
   * Source https://codingforspeed.com/a-faster-approach-to-count-set-bits-in-a-32-bit-integer/
   */
  private static int popCount(int i) {
    i = i - ((i >> 1) & 0x55555555);
    i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
    i = (i + (i >> 4)) & 0x0f0f0f0f;
    i = i + (i >> 8);
    i = i + (i >> 16);
    return i & 0x3f;
  }

  private List<AbstractSubAST> extractAllSubASTsOfRootNodes(
      final List<AbstractSubAST> preExistingSubASTs,
      final Set<RootNode> rootNodes) {
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
              SingleSubAST.fromAST(rootNode, worklist, rawActivations);
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
    final Formatter formatter = new Formatter(report);

    for (final AbstractSubAST ast : uniqueASTs) {
      report.append(
          "===============================================================================\n")
            .append(scoringMethod.getDescription());
      formatter.format("%,d", ast.getScore());
      // .append(ast.getScore());
      if (ast instanceof VirtualSubAST) {
        report.append(" (virtual)\n\n");
      } else {
        report.append("\n\n");
      }

      ast.toStringRecursive(report, "");
      if (ast instanceof SingleSubAST) {
        report.append('\n');
      }
    }

    formatter.close();

    final Path reportPath = Paths.get(this.metricsFolder,
        "superinstruction-candidates-stefan-" + scoringMethod.getSimpleName() + ".txt");
    try {
      Files.write(reportPath, report.toString().getBytes());
    } catch (IOException e) {
      throw new RuntimeException("Could not write superinstruction candidate report.", e);
    }
  }

}
