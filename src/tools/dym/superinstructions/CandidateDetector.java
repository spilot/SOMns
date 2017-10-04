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

  public Map<Integer, ActivationContext> findAunts(ActivationContext context) {
    // context: C_0 i_0 C_1 i_1 C_2
    // Find aunts, i.e.
    // D_0 j_0 D_1 j_1 D_2
    //         =   !=
    //         C_0 i_0 C_1 i_1 C_2
    Map<Integer, Map<ActivationContext, Long>> auntsByIndex = new HashMap<>();
    for(ActivationContext otherContext : contexts.keySet()) {
      if(otherContext.getNumberOfClasses() == 3
              && otherContext.getClass(1).equals(context.getClass(0))
              && otherContext.getChildIndex(1) != context.getChildIndex(0)) {
        ActivationContext auntContext = new ActivationContext(
                new Object[]{ otherContext.getClass(1), otherContext.getChildIndex(1), otherContext.getClass(2) },
                otherContext.getJavaType()
        );
        Map<ActivationContext, Long> aunts = auntsByIndex.computeIfAbsent(otherContext.getChildIndex(1),
                k -> new HashMap<>());
        if (!aunts.containsKey(auntContext)) {
          aunts.put(auntContext, 0L);
        }
        aunts.put(auntContext, aunts.get(auntContext) + contexts.get(otherContext));
      }
    }
    Map<Integer, ActivationContext> aunts = new HashMap<>();
    for(int childIndex : auntsByIndex.keySet()) {
      ActivationContext topAunt = auntsByIndex.get(childIndex).keySet().stream()
              .max(Comparator.comparingLong(ctx -> auntsByIndex.get(childIndex).get(ctx)).reversed())
              .orElseThrow(() -> new RuntimeException("No suitable alternative"));
      aunts.put(childIndex, topAunt);
    }
    return aunts;
  }

  public Candidate constructCandidate(ActivationContext context) {
    assert context.getNumberOfClasses() == 3;
    int childIndex = context.getLeafChildIndex();
    System.out.println(context.toPrettyString());
    System.out.println("=======================");
    Map<Integer, Set<ActivationContext>> sisterAlternativesByIndex = new HashMap<>();
    for(ActivationContext otherContext : contexts.keySet()) {
      if(ActivationContext.subtraceEquals(context.getTrace(), otherContext.getTrace(), 3)
              && otherContext.getLeafChildIndex() != childIndex) {
        sisterAlternativesByIndex.computeIfAbsent(otherContext.getLeafChildIndex(), k -> new HashSet<>())
                .add(otherContext);
      }
    }
    int maxSister = childIndex;
    Map<Integer, ActivationContext> sisters = new HashMap<>();
    for(int sisterIndex : sisterAlternativesByIndex.keySet()) {
      ActivationContext topAlternative = sisterAlternativesByIndex.get(sisterIndex).stream()
              .max(Comparator.comparingLong(ctx -> contexts.get(ctx)).reversed())
              .orElseThrow(() -> new RuntimeException("No suitable alternative"));
      sisters.put(sisterIndex, topAlternative);
      maxSister = Integer.max(maxSister, sisterIndex);
    }
    Map<Integer, ActivationContext> aunts = findAunts(context);
    int maxAunt = aunts.keySet().stream()
            .max(Comparator.comparingInt(e -> (Integer)e).reversed())
            .orElse(context.getChildIndex(0));
    Candidate candidate = new Candidate(context.getClass(0));
    for(int i = 0; i <= maxAunt; i++) {
      if(i == context.getChildIndex(0)) {
        Candidate.Node child = candidate.getRoot().addChild(context.getClass(1));
        for(int j = 0; j <= maxSister; j++) {
          if(sisters.containsKey(j)) {
            child.addChild(sisters.get(j).getLeafClass());
          } else if(j == context.getChildIndex(1)) {
            child.addChild(context.getLeafClass());
          } else {
            child.addChild("?");
          }
        }
      } else if(aunts.containsKey(i)) {
        candidate.getRoot().addChild(aunts.get(i).getLeafClass());
      } else {
        candidate.getRoot().addChild("?");
      }
    }
    System.out.println(candidate.prettyPrint());
    //return new HashSet<>();
    return null;
  }

  public void detect() {
    // Sort the traces
    List<ActivationContext> sorted = contexts.keySet().stream()
            .filter(context -> context.getNumberOfClasses() == 3)
            .sorted(Comparator.comparingLong(context -> contexts.get(context)).reversed())
            .collect(Collectors.toList());
    for(int i = 0; i < CONSIDER_CHILDREN; i++) {
      constructCandidate(sorted.get(i));
    }
  }
}
