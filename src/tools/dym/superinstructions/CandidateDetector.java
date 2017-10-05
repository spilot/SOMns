package tools.dym.superinstructions;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by fred on 04/10/17.
 */
public class CandidateDetector {
  static public int CONSIDER_CHILDREN = 50;
  private Map<ActivationContext, Long> contexts;

  public CandidateDetector(Map<ActivationContext, Long> contexts) {
    this.contexts = contexts;
  }

  public Stream<ActivationContext> contextsWithPrefix(Object[] prefix) {
    return contexts.keySet().stream()
            .filter(ctx -> ctx.traceStartsWith(prefix));
  }

  public String findAunt(String ancestor, int auntIndex) {
    // context: C_0 i_0 C_1 i_1 C_2
    // Find aunts, i.e.
    // (D_0 j_0) D_1 j_1 D_2
    //           =   !=
    //           anc idx
    Map<String, Long> possibleAunts = new HashMap<>();
    for(ActivationContext otherContext : contexts.keySet()) {
      int index = otherContext.getNumberOfClasses() - 2;
      if(otherContext.getClass(index).equals(ancestor)
              && otherContext.getChildIndex(index) == auntIndex) {
        String possibleAunt = otherContext.getClass(index + 1);
        if (!possibleAunts.containsKey(possibleAunt)) {
          possibleAunts.put(possibleAunt, 0L);
        }
        possibleAunts.put(possibleAunt, possibleAunts.get(possibleAunt) + contexts.get(otherContext));
      }
    }
    return possibleAunts.keySet().stream()
            .max(Comparator.comparingLong(aunt -> possibleAunts.get(aunt)))
            .orElse("?");
  }

  public Candidate constructCandidate(ActivationContext currentContext) {
    assert currentContext.getNumberOfClasses() == 3;
    // Trace of currentContext:
    // C_0  i_0   C_1   i_1   C_2
    // First, find all contexts with prefix C_0, i_0, C_1:
    Set<ActivationContext> sisterContexts = contextsWithPrefix(
            new Object[] {
                    currentContext.getClass(0),
                    currentContext.getChildIndex(0),
                    currentContext.getClass(1)
            }
    ).collect(Collectors.toSet());
    int childIndex = currentContext.getLeafChildIndex();
    // Attempt to find all possible sisters
    Map<Integer, Set<ActivationContext>> sisterAlternativesByIndex = new HashMap<>();
    for(ActivationContext otherContext : sisterContexts) {
      if(otherContext.getLeafChildIndex() != childIndex) {
        sisterAlternativesByIndex.computeIfAbsent(otherContext.getLeafChildIndex(), k -> new HashSet<>())
                .add(otherContext);
      }
    }
    // By index, choose the sister context with the highest number of activations
    Map<Integer, ActivationContext> sisters = new HashMap<>();
    // Also insert the currentContext here
    sisters.put(childIndex, currentContext);
    for(int sisterIndex : sisterAlternativesByIndex.keySet()) {
      ActivationContext topAlternative = sisterAlternativesByIndex.get(sisterIndex).stream()
              .max(Comparator.comparingLong(ctx -> contexts.get(ctx)))
              .orElseThrow(() -> new RuntimeException("No suitable alternative"));
      sisters.put(sisterIndex, topAlternative);
    }
    // Now we have determined the sisters.
    // Let's determine the aunts.
    Set<Integer> auntIndices = contextsWithPrefix(
            new Object[] {
                    currentContext.getClass(0)
            }
    ).map(ctx -> ctx.getChildIndex(0)).collect(Collectors.toSet());
    Map<Integer, String> aunts = new HashMap<>();
    for(int auntIndex : auntIndices) {
      if(auntIndex != currentContext.getChildIndex(0)) {
        aunts.put(auntIndex, findAunt(currentContext.getClass(0), auntIndex));
      }
    }

    Candidate candidate = new Candidate(currentContext.getClass(0));
    for(int auntSlot : auntIndices) {
      if(auntSlot == currentContext.getChildIndex(0)) {
        Candidate.Node child = candidate.getRoot().setChild(auntSlot, currentContext.getClass(1));
        for(int sisterSlot : sisters.keySet()) {
          if(sisterSlot == childIndex) {
            child.setChild(sisterSlot, currentContext.getLeafClass());
          } else {
            child.setChild(sisterSlot, sisters.get(sisterSlot).getLeafClass());
          }
        }
      } else {
        candidate.getRoot().setChild(auntSlot, aunts.get(auntSlot));
      }
    }
    candidate.setScore(contexts.get(currentContext));
    return candidate;
  }

  public void detect() {
    // Sort the traces
    List<ActivationContext> sorted = contexts.keySet().stream()
            .filter(context -> context.getNumberOfClasses() == 3)
            .sorted(Comparator.comparingLong(context -> contexts.get(context)).reversed())
            .collect(Collectors.toList());
    Set<Candidate> candidates = new HashSet<>();
    for(int i = 0; i < CONSIDER_CHILDREN; i++) {
      candidates.add(constructCandidate(sorted.get(i)));
    }
    List<Candidate> tops = candidates.stream()
            .sorted(Comparator.comparingLong(Candidate::getScore).reversed())
            .collect(Collectors.toList());
    for(Candidate top : tops) {
      System.out.println(top.prettyPrint());
      System.out.println(top.getScore());
    }
  }
}
