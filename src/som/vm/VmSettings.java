package som.vm;

import java.util.Arrays;
import java.util.List;

import bd.settings.Settings;


public class VmSettings implements Settings {
  public static final int NUM_THREADS;

  // TODO: revise naming of flags
  public static final boolean FAIL_ON_MISSING_OPTIMIZATIONS;
  public static final boolean DEBUG_MODE;
  public static final boolean ACTOR_TRACING;
  public static final boolean MEMORY_TRACING;
  public static final String  TRACE_FILE;
  public static final boolean DISABLE_TRACE_FILE;
  public static final boolean INSTRUMENTATION;
  public static final boolean DYNAMIC_METRICS;
  public static final boolean DNU_PRINT_STACK_TRACE;
  public static final boolean MESSAGE_PARAMETERS;
  public static final boolean PROMISE_CREATION;
  public static final boolean PROMISE_RESOLUTION;
  public static final boolean PROMISE_RESOLVED_WITH;
  public static final boolean REPLAY;
  public static final boolean SUPERINSTRUCTIONS;

  public static final boolean TRUFFLE_DEBUGGER_ENABLED;

  public static final boolean IGV_DUMP_AFTER_PARSING;

  public static final String INSTRUMENTATION_PROP = "som.instrumentation";

  static {
    String prop = System.getProperty("som.threads");
    if (prop == null) {
      NUM_THREADS = Runtime.getRuntime().availableProcessors();
    } else {
      NUM_THREADS = Integer.valueOf(prop);
    }

    FAIL_ON_MISSING_OPTIMIZATIONS = getBool("som.failOnMissingOptimization", false);
    DEBUG_MODE = getBool("som.debugMode", false);
    TRUFFLE_DEBUGGER_ENABLED = getBool("som.truffleDebugger", false);

    TRACE_FILE =
        System.getProperty("som.traceFile", System.getProperty("user.dir") + "/traces/trace");
    MEMORY_TRACING = getBool("som.memoryTracing", false);
    REPLAY = getBool("som.replay", false);
    DISABLE_TRACE_FILE = getBool("som.disableTraceFile", false) || REPLAY;

    String atConfig = System.getProperty("som.actorTracingCfg", "");
    List<String> al = Arrays.asList(atConfig.split(":"));
    boolean filter =
        (al.size() > 0 && !atConfig.isEmpty()) || getBool("som.actorTracing", false);

    MESSAGE_PARAMETERS = !al.contains("mp") && filter;
    PROMISE_CREATION = !al.contains("pc") && filter;
    PROMISE_RESOLUTION = PROMISE_CREATION && (!al.contains("pr")) && filter;
    PROMISE_RESOLVED_WITH = !al.contains("prw") && filter;

    ACTOR_TRACING = TRUFFLE_DEBUGGER_ENABLED || getBool("som.actorTracing", false) ||
        REPLAY || MESSAGE_PARAMETERS || PROMISE_CREATION;

    boolean dm = getBool("som.dynamicMetrics", false);
    DYNAMIC_METRICS = dm;
    INSTRUMENTATION = dm || getBool(INSTRUMENTATION_PROP, false);

    DNU_PRINT_STACK_TRACE = getBool("som.printStackTraceOnDNU", false);

    IGV_DUMP_AFTER_PARSING = getBool("som.igvDumpAfterParsing", false);
    SUPERINSTRUCTIONS = getBool("som.superinstructions", true);
  }

  private static boolean getBool(final String prop, final boolean defaultVal) {
    return Boolean.parseBoolean(System.getProperty(prop, defaultVal ? "true" : "false"));
  }

  @Override
  public boolean dynamicMetricsEnabled() {
    return DYNAMIC_METRICS;
  }
}
