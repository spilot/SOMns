package tools.dym.superinstructions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import som.interpreter.nodes.SOMNode;


public class GuardEvaluationCounter {

  private static final Map<Class<? extends SOMNode>, Map<SOMNode, Long>> activations =
      new HashMap<>();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      // print overall sum
      final long overallSuperinstructionGuardActivations =
          activations.keySet().stream().mapToLong(
              (key) -> activations.get(key).values().stream().reduce(0L, Long::sum))
                     .reduce(0L, Long::sum);
      System.out.println("Superinstruction guards were activated "
          + overallSuperinstructionGuardActivations + " times.");

      // for each type of superinstruction
      activations.keySet().forEach((key) -> {
        // print mean over
        final Collection<Long> thisTypesValues = activations.get(key).values();
        final long mean =
            thisTypesValues.stream().reduce(0L, Long::sum) / thisTypesValues.size();
        System.out.format("%34s%7s%2d%2s", key.getSimpleName(), " mean: ", mean, ". ");

        // print histogram
        final HashMap<Long, Long> histogram = new HashMap<>();
        activations.get(key).entrySet()
                   .forEach((entry) -> histogram.compute(entry.getValue(),
                       (bucket, oldvalue) -> oldvalue == null ? 1 : oldvalue + 1));
        System.out.println(histogram.toString());
      });
    }));
  }

  public static synchronized void recordActivation(final Class<? extends SOMNode> type,
      final SOMNode instance) {
    activations.computeIfAbsent(type, (key) -> new HashMap<>())
               .compute(instance, (key, value) -> (value == null) ? 1L : value + 1L);
  }

}
