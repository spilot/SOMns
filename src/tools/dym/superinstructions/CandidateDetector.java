package tools.dym.superinstructions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by fred on 04/10/17.
 */
public class CandidateDetector {
  static public int CONSIDER_CHILDREN = 20;
  private Map<ActivationContext, Long> contexts;

  public CandidateDetector(Map<ActivationContext, Long> contexts) {
    this.contexts = contexts;
  }

  public Set<Candidate> constructCandidates(ActivationContext context) {
    assert context.getNumberOfClasses() == 3;
    String childClass = context.getLeafClass();
    int auntIndex = context.getChildIndex(1);
    int childIndex = context.getLeafChildIndex();
    System.out.println(context.toPrettyString());
    System.out.println("=======================");
    Map<Integer, Set<ActivationContext>> sisterAlternativesByIndex = new HashMap<>();
    Map<Integer, Map<ActivationContext, Long>> auntAlternativesByIndex = new HashMap<>();
    for(ActivationContext otherContext : contexts.keySet()) {
      if(ActivationContext.subtraceEquals(context.getTrace(), otherContext.getTrace(), 0, 3)
              && otherContext.getLeafChildIndex() != childIndex) {
        System.out.println("SISTER " + otherContext.toPrettyString());
        sisterAlternativesByIndex.computeIfAbsent(otherContext.getLeafChildIndex(), k -> new HashSet<>())
                .add(otherContext);
      }
      if(otherContext.getNumberOfClasses() == 3
              && otherContext.getClass(1).equals(context.getClass(0))
              && otherContext.getChildIndex(1) != auntIndex) {
        ActivationContext auntContext = new ActivationContext(new Object[] {
                otherContext.getClass(1),
                otherContext.getChildIndex(1),
                otherContext.getClass(2)
        }, otherContext.getJavaType());
        Map<ActivationContext, Long> alternatives = auntAlternativesByIndex.computeIfAbsent(
                otherContext.getChildIndex(1), k -> new HashMap<>());
        if(!alternatives.containsKey(auntContext)) {
          alternatives.put(auntContext, 0L);
        }
        alternatives.put(auntContext, alternatives.get(auntContext) + contexts.get(otherContext));
      }
    }

    Set<Candidate> candidates = new HashSet<>();
    for(int otherAuntIndex : auntAlternativesByIndex.keySet()) {
      for(Map<ActivationContext, Long> otherAuntContexts : auntAlternativesByIndex.get(otherAuntIndex)) {
        otherAuntContext.get
      }
    }

    System.out.println("");
    return new HashSet<>();
  }

  public void detect() {
    // Sort the traces
    List<ActivationContext> sorted = contexts.keySet().stream()
            .filter(context -> context.getNumberOfClasses() == 3)
            .sorted(Comparator.comparingLong(context -> contexts.get(context)).reversed())
            .collect(Collectors.toList());
    for(int i = 0; i < CONSIDER_CHILDREN; i++) {
      constructCandidates(sorted.get(i));
    }
  }
}
