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

  /** Given a prefix { C_0, i_0, ..., i_k, C_{k+1} } this returns a map
   * that maps each i_{k+1} to an activation context with a trace
   * { C_0, i_0, ..., i_k, C_{k+1}, i_{k+1}, C_{k+2} }
   * for which activations(ctx) is maximal.
   * @param prefix
   * @return
   */
  private Map<Integer, ActivationContext> findExtensions(Object[] prefix) {
    Map<Integer, ActivationContext> result = new HashMap<>();
    Set<ActivationContext> extensions = contextsWithPrefix(prefix)
            .filter(ctx -> ctx.getTrace().length == prefix.length + 2)
            .collect(Collectors.toSet());
    Set<Integer> childIndices = extensions.stream()
            .map(ActivationContext::getLeafChildIndex)
            .collect(Collectors.toSet());
    for(int childIndex : childIndices) {
      ActivationContext extension = extensions.stream()
              .filter(ctx -> ctx.getLeafChildIndex() == childIndex)
              .max(Comparator.comparingLong(ctx -> contexts.get(ctx)))
              .orElseThrow(() -> new RuntimeException("No suitable alternatives"));
      result.put(childIndex, extension);
    }
    return result;
  }

  public Candidate constructCandidate(ActivationContext currentContext) {
    assert currentContext.getNumberOfClasses() == 3;
    Candidate candidate = new Candidate(currentContext.getClass(0), "?");
    Map<Integer, ActivationContext> piblings = findExtensions(
            new Object[] {
                    currentContext.getClass(0)
            }
    );
    Map<Integer, ActivationContext> siblings = findExtensions(
            new Object[] {
                    currentContext.getClass(0),
                    currentContext.getChildIndex(0),
                    currentContext.getClass(1)
            }
    );
    for(int piblingSlot : piblings.keySet()) {
      if(piblingSlot == currentContext.getChildIndex(0)) {
        Candidate.Node child = candidate.getRoot().setChild(piblingSlot,
                currentContext.getClass(1),
                "?");
        for(int siblingSlot : siblings.keySet()) {
          if(siblingSlot == currentContext.getChildIndex(1)) {
            child.setChild(siblingSlot,
                    currentContext.getClass(2),
                    currentContext.getJavaType());
          } else {
            ActivationContext sibling = siblings.get(siblingSlot);
            child.setChild(siblingSlot,
                    sibling.getClass(2),
                    sibling.getJavaType());
          }
        }
      } else {
        ActivationContext pibling = piblings.get(piblingSlot);
        assert pibling.getNumberOfClasses() == 2;
        candidate.getRoot().setChild(piblingSlot,
                pibling.getClass(1),
                pibling.getJavaType());
      }
    }
    candidate.setScore(contexts.get(currentContext));
    return candidate;
  }

  public String detect() {
    // Sort the traces
    List<ActivationContext> sorted = contexts.keySet().stream()
            .filter(context -> context.getNumberOfClasses() == 3)
            .filter(context -> !context.getClass(0).equals("som.interpreter.nodes.SequenceNode"))
            .sorted(Comparator.comparingLong(context -> contexts.get(context)).reversed())
            .collect(Collectors.toList());
    Set<Candidate> candidates = new HashSet<>();
    for(int i = 0; i < CONSIDER_CHILDREN; i++) {
      candidates.add(constructCandidate(sorted.get(i)));
    }
    List<Candidate> tops = candidates.stream()
            .sorted(Comparator.comparingLong(Candidate::getScore).reversed())
            .collect(Collectors.toList());
    StringBuilder builder = new StringBuilder();
    for(Candidate top : tops) {
      builder.append(top.prettyPrint()).append('\n');
      builder.append(String.format("(%d activations)", top.getScore())).append("\n\n");
    }
    return builder.toString();
  }
}
