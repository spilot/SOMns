package tools.dym.superinstructions;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public class CandidateWriter {

  public static final String CANDIDATE_DATA_FILE_NAME = "/candidates.data";

  public final Map<Node, BigInteger> rawActivations = new HashMap<>();
  public List<AbstractSubAST>        olderSubASTs;

  public CandidateWriter() {
    olderSubASTs = null;
  }

  public CandidateWriter(final String metricsFolder) {
    try (ObjectInputStream ois =
        new ObjectInputStream(new FileInputStream(metricsFolder + CANDIDATE_DATA_FILE_NAME))) {
      this.olderSubASTs = (List<AbstractSubAST>) ois.readObject();
    } catch (ClassCastException | IOException | ClassNotFoundException e) {
      e.printStackTrace();
      olderSubASTs = null;
    }
  }

  public void fileOut(final Set<RootNode> rootNodes, final String path) {
    final List<AbstractSubAST> subASTs =
        olderSubASTs == null ? new ArrayList<>() : olderSubASTs;
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

    // remove null elements from unique array by adding all non-null items to a list
    List<AbstractSubAST> uniqueASTs = new ArrayList<>();
    for (AbstractSubAST ast : ra) {
      if (ast != null) {
        uniqueASTs.add(ast);
      }
    }

    // sort using the natural ordering specified by the compareTo method
    uniqueASTs.sort(null);

    try (ObjectOutputStream oos =
        new ObjectOutputStream(new FileOutputStream(path + CANDIDATE_DATA_FILE_NAME))) {
      oos.writeObject(uniqueASTs);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

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
  }

  public synchronized void countActivation(final Node node) {
    if (node != null) {
      rawActivations.compute(node,
          (n, v) -> v == null
              ? BigInteger.ONE
              : v.add(BigInteger.ONE));
    }
  }
}
