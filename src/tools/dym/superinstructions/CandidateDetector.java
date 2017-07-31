package tools.dym.superinstructions;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerPrimitive;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import tools.dym.profiles.TypeCounter;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
                String operation = ((OperationNode)parent).getOperation();
                parentClass = "Operation_" + operation;
            }
            String childClass = childClassNames.get(childIndex);
            if(SOMNode.unwrapIfNecessary(childNode) instanceof EagerPrimitive) {
                String operation = ((OperationNode)SOMNode.unwrapIfNecessary(childNode)).getOperation();
                childClass = "Operation_" + operation;
            }
            //countPattern(parent, childIndex, childClassNames.get(childI ndex), activationCounter.getTotalActivations());
            ActivationNode nParent = graph.getOrCreateNode(parentClass);
            ActivationNode nChild = graph.getOrCreateNode(childClass);
            for(Class<?> javaType : activat.keySet()) {
                ActivationEdge edge = graph.getOrCreateEdge(nParent, nChild, childIndex, javaType.getName());
                edge.addActivations(activat.get(javaType));
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
        graph.writeToGraph();
        /*ActivationEdge edge = findMaximumEdge();
        Map<ActivationNode, Integer> variations = new HashMap<>();
        for(ActivationNode node : graph.getNodes()) {
            Set<Integer> childIndices = graph.outgoingEdges(node)
                    .map(ActivationEdge::getChildIndex)
                    .distinct()
                    .collect(Collectors.toSet());
            System.out.println(node.getClassName());
            for(Integer childIndex : childIndices) {
                List<ActivationEdge> matchingOutgoing = graph.outgoingEdges(node)
                        .filter(e -> e.getChildIndex() == childIndex)
                        .collect(Collectors.toList());
                List<String> childClassNames = matchingOutgoing.stream()
                        .map(e -> e.getChild().getClassName())
                        .collect(Collectors.toList());
                System.out.println(childClassNames);
            }
        }
        System.out.println(edge.getParent().getClassName() + "->" + edge.getChild().getClassName() + ": " + edge.getActivations());
        */
    }

    public ActivationEdge findMaximumEdge() {
        Optional<ActivationEdge> edge = graph.getEdges().stream()
                .sorted(Comparator.comparingLong(ActivationEdge::getActivations).reversed())
                .findFirst();
        return edge.orElseThrow(() -> new RuntimeException("No edges at all"));
    }
}
