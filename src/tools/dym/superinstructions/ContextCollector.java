package tools.dym.superinstructions;

import bd.nodes.EagerPrimitive;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import tools.dym.profiles.TypeCounter;

import java.util.*;

public class ContextCollector implements NodeVisitor {
  private Map<Node, TypeCounter> activationCounters;
  private Map<ActivationContext, Long> contexts;

  public static final int CONTEXT_LEVEL = 2;

  public ContextCollector(Map<Node, TypeCounter> activationCounters) {
    this.activationCounters = activationCounters;
    this.contexts = new HashMap<>();
  }

  public List<Object> constructTrace(Node node, int contextLevel) {
    if(contextLevel == 0 || node.getParent() == null) {
      assert !(node instanceof InstrumentableFactory.WrapperNode);
      return Arrays.asList(getNodeClass(node));
    } else {
      Node childNode = node;
      assert !(node instanceof InstrumentableFactory.WrapperNode);
      Node parent = node.getParent();
      //assert !(parent instanceof EagerPrimitive);
      if (parent instanceof InstrumentableFactory.WrapperNode) {
        childNode = parent;
        parent = parent.getParent();
      }
      assert !(parent instanceof InstrumentableFactory.WrapperNode);
      int i = 0, childIndex = -1;
      for (Node child : NodeUtil.findNodeChildren(parent)) {
        if (child == childNode) {
          childIndex = i;
        }
        i++;
      }
      assert childIndex != -1;
      String childClass = getNodeClass(node);
      ArrayList<Object> trace = new ArrayList<>();
      List<Object> parentTrace = constructTrace(parent, contextLevel - 1);
      trace.addAll(parentTrace);
      trace.add(childIndex);
      trace.add(childClass);
      return trace;
    }
  }

  public ActivationContext makeActivationContext(Node node, Class<?> javaType, int contextLevel) {
    return new ActivationContext(
            constructTrace(node, contextLevel).toArray(),
            javaType.getName());
  }

  public String getNodeClass(Node node) {
    if(node instanceof EagerPrimitive) {
      return "PrimitiveOperation:" + ((EagerPrimitive) node).getOperation();
    } else {
      return node.getClass().getName();
    }
  }

  public boolean visit(Node node) {
    if(node instanceof InstrumentableFactory.WrapperNode
            || node instanceof RootNode)
      return true;
    TypeCounter activationCounter = activationCounters.get(node);
    if(activationCounter != null) {
      Map<Class<?>, Long> activationsByType = activationCounter.getActivations();
      for(Class<?> javaType : activationsByType.keySet()) {
        long typeActivations = activationsByType.get(javaType);
        for(int level = 0; level <= CONTEXT_LEVEL; level++) {
          ActivationContext context = makeActivationContext(node, javaType, level);
          if (!contexts.containsKey(context)) {
            contexts.put(context, typeActivations);
          } else {
            contexts.put(context, contexts.get(context) + typeActivations);
          }
        }
      }
      return true;
    } else {
      return true;
    }
  }

  public Map<ActivationContext, Long> getContexts() {
    return contexts;
  }
}
