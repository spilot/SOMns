package tools.dym;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import som.interpreter.Method;
import som.interpreter.nodes.nary.EagerPrimitive;
import tools.dym.profiles.Counter;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by fred on 09/07/17.
 */
public class SuperinstructionCandidateDetector implements NodeVisitor {
    private Map<Node, Counter> activations;
    Map<Pattern, Long> patterns = new HashMap<>();

    public SuperinstructionCandidateDetector(Map<Node, Counter> activations) {
        this.activations = activations;
    }

    public boolean visit(Node node) {
        if(node instanceof InstrumentableFactory.WrapperNode
                || node instanceof RootNode)
            return true;
        Counter activationCounter = activations.get(node);
        if(activationCounter != null) {
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
            countPattern(parent, childIndex, childClassNames.get(childIndex), activationCounter.getValue());
            return true;
        } else {
            return true;
        }
    }

    private void countPattern(Node parent, int childIndex, String childClass, long increment) {
        Pattern pattern = new Pattern(parent.getClass().getName(),
                childIndex,
                childClass);
        patterns.computeIfAbsent(pattern, k -> 0L);
        patterns.put(pattern, patterns.get(pattern) + increment);
    }

    private Set<String> getParentClasses() {
        return patterns.keySet().stream()
                .map(Pattern::getParentClass)
                .collect(Collectors.toSet());
    }

    private Set<Pattern> getPatternsMatching(Predicate<? super Pattern> predicate) {
        return patterns.keySet().stream()
                .filter(predicate)
                .collect(Collectors.toSet());
    }

    static public String abbreviate(String className) {
        String[] parts = className.split(java.util.regex.Pattern.quote("."));
        return parts[parts.length - 1];
    }

    public void finish() {
        WitnessCounter witnesses = new WitnessCounter();
        for(String parentClass : getParentClasses()) {
            // for each parent class, find all patterns with this parent class
            Set<Pattern> matching = getPatternsMatching(pattern -> pattern.getParentClass().equals(parentClass));
            // out of these, get the set of all occurring child indices
            Set<Integer> childIndices = matching.stream()
                                        .map(Pattern::getChildIndex)
                                        .collect(Collectors.toSet());
            // do the following for each child index:
            for(Integer index : childIndices) {
                Set<Pattern> patternsWithIndex = matching.stream()
                                                 .filter(p -> p.getChildIndex() == index)
                                                 .collect(Collectors.toSet());
                ParentChildRelation relation = new ParentChildRelation(parentClass, index);
                HashMap<String, Long> childClasses = new HashMap<>();
                for(Pattern pattern : patternsWithIndex) {
                    childClasses.put(pattern.getChildClass(), patterns.get(pattern));
                }
                witnesses.put(relation, childClasses);
            }
        }
        List<ParentChildRelation> niceRelations = witnesses.keySet().stream()
                            .filter(rel -> witnesses.get(rel).size() > 2)
                            .sorted(Comparator.comparingLong(rel -> witnesses.totalActivations((ParentChildRelation)rel)).reversed())
                            .collect(Collectors.toList());
        for(ParentChildRelation rel : niceRelations) {
            System.out.println(String.format(
                    "%s child #%d witnesses %d different types (%d total activations)",
                    abbreviate(rel.getParentClass()),
                    rel.getChildIndex(),
                    witnesses.get(rel).size(),
                    witnesses.totalActivations(rel)));
            for(Map.Entry<String, Long> entry : witnesses.get(rel).entrySet().stream()
                                                .sorted(Comparator.comparingLong(e -> ((Map.Entry<String, Long>)e).getValue()).reversed())
                                                .collect(Collectors.toList())) {
                System.out.println(String.format("\t->%s (%d activations)", abbreviate(entry.getKey()), entry.getValue()));
            }
            System.out.println("");
        }
    }

    static private class ParentChildRelation {
        private final String parentClass;
        private final int childIndex;

        public String getParentClass() {
            return parentClass;
        }

        public int getChildIndex() {
            return childIndex;
        }

        public ParentChildRelation(String parentClass, int childIndex) {
            this.parentClass = parentClass;
            this.childIndex = childIndex;
        }

        public String toString() {
            return String.format("%s(%d)", abbreviate(parentClass), childIndex);
        }
    }

    static private class WitnessCounter extends HashMap<ParentChildRelation, HashMap<String, Long>> {
        public WitnessCounter() {
            super();
        }

        public long totalActivations(ParentChildRelation rel) {
            return this.get(rel).values().stream().mapToLong(Long::longValue).sum();
        }
    }

    static public class Pattern {
        private final String parentClass, childClass;
        private final int childIndex;

        public int getChildIndex() {
            return childIndex;
        }

        public String getChildClass() {
            return childClass;
        }

        public String getParentClass() {
            return parentClass;
        }

        public Pattern(String parentClass, int childIndex, String childClass) {
            this.parentClass = parentClass;
            this.childIndex = childIndex;
            this.childClass = childClass;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + parentClass.hashCode();
            result = 37 * result + ((Integer)childIndex).hashCode();
            result = 37 * result + childClass.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof Pattern))
                return false;
            Pattern otherPattern = (Pattern)other;
            return parentClass.equals(otherPattern.getParentClass())
                    && childIndex == otherPattern.getChildIndex()
                    && childClass.equals(otherPattern.getChildClass());
        }
    }
}
