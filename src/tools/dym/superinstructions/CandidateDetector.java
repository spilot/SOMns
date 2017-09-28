package tools.dym.superinstructions;

import bd.nodes.EagerPrimitive;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerPrimitiveNode;
import tools.dym.profiles.TypeCounter;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by fred on 09/07/17.
 */
public class CandidateDetector implements NodeVisitor {
    private Map<Node, TypeCounter> activations;
    private ActivationGraph graph;

    public CandidateDetector(Map<Node, TypeCounter> activations) {
        this.activations = activations;
        this.graph = new ActivationGraph();
    }

    public boolean visit(Node node) {
        if(node instanceof InstrumentableFactory.WrapperNode
                || node instanceof RootNode)
            return true;
        TypeCounter activationCounter = activations.get(node);
        if(activationCounter != null) {
            Map<Class<?>, Long> activat = activationCounter.getActivations();
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
            ArrayList<String> childClassNames = new ArrayList<>();
            for (Node child : NodeUtil.findNodeChildren(parent)) {
                if (child == childNode) {
                    childIndex = i;
                }
                if (child instanceof InstrumentableFactory.WrapperNode) {
                    child = ((InstrumentableFactory.WrapperNode) child).getDelegateNode();
                }
            /*if(child instanceof EagerPrimitive) {
                child = getPrimitive((EagerPrimitive)child);
            }
            */
                childClassNames.add(child.getClass().getName());
                i++;
            }
            assert childIndex != -1;
        /*if(parent instanceof EagerPrimitive) {
            parent = getPrimitive((EagerPrimitive)parent);
            childClassNames.remove(childClassNames.size() - 1); // TODO: because that's the primitive argument??
        }*/
            String parentClass = parent.getClass().getName();
            if(parent instanceof EagerPrimitive) {
                String operation = ((EagerPrimitive) parent).getOperation();
                parentClass = "PrimitiveOperation:" + operation;
            }
            String childClass = childClassNames.get(childIndex);
            if(SOMNode.unwrapIfNecessary(childNode) instanceof EagerPrimitive) {
                String operation = ((EagerPrimitive) SOMNode.unwrapIfNecessary(childNode)).getOperation();
                childClass = "PrimitiveOperation:" + operation;
            }
            //countPattern(parent, childIndex, childClassNames.get(childI ndex), activationCounter.getTotalActivations());
            ActivationNode nParent = graph.getOrCreateNode(parentClass);
            ActivationNode nChild = graph.getOrCreateNode(childClass);
            for(Class<?> javaType : activat.keySet()) {
                ActivationEdge edge = graph.getOrCreateEdge(nParent, nChild, childIndex, javaType.getName());
                graph.addActivations(edge, activat.get(javaType));
            }
            return true;
        } else {
            return true;
        }
    }


    static public String abbreviate(String className) {
        String[] parts = className.split(java.util.regex.Pattern.quote("."));
        return parts[parts.length - 1];
    }

  public void finish() {
    ActivationEdge edge = findMaximumEdge();
    removeBimorphic();
    System.out.println(edge);
    graph.getEdges().stream()
        .sorted(Comparator.comparingLong(e -> graph.getActivations((ActivationEdge)e)).reversed())
            .limit(10)
            .forEach(e -> System.out.println(e));
  }

  public ActivationEdge findMaximumEdge() {
    Optional<ActivationEdge> edge = graph.getEdges().stream()
            .sorted(Comparator.comparingLong(e -> graph.getActivations((ActivationEdge)e)).reversed())
            .findFirst();
    return edge.orElseThrow(() -> new RuntimeException("No edges at all"));
  }

    public void removeBimorphic() {
      Set<ActivationEdge> edgesToRemove = new HashSet<>();
      for(ActivationNode node : graph.getNodes()) {
        // for each child index, get the outgoing edges
        HashMap<Integer, Set<ActivationEdge>> outgoingByIndex = new HashMap<>();
        graph.outgoingEdges(node).forEach(edge ->
          outgoingByIndex.computeIfAbsent(edge.getChildIndex(), (idx) -> new HashSet<>()).add(edge)
        );
        for(Integer childIndex : outgoingByIndex.keySet()) {
          if(outgoingByIndex.get(childIndex).size() <= 2) {
            edgesToRemove.addAll(outgoingByIndex.get(childIndex));
          }
        }
      }
      graph.removeEdges(edgesToRemove);
      Set<ActivationNode> nodesToRemove = graph.getNodes().stream()
              .filter(node -> graph.allEdges(node).count() == 0)
              .collect(Collectors.toSet());
      graph.removeNodes(nodesToRemove);
      System.out.println(String.format("Removed %d nodes and %d edges.", nodesToRemove.size(), edgesToRemove.size()));
    }
}
