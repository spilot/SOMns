package tools.dym.nodes;

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
    private Map<SourceSection, Counter> activationProfiles;
    Map<Pattern, Integer> patterns = new HashMap<>();

    public SuperinstructionCandidateDetector(Map<SourceSection, Counter> activationProfiles) {
        this.activationProfiles = activationProfiles;
    }

    public boolean visit(Node node) {
        Counter activationCounter = activationProfiles.get(node.getSourceSection());
        if(activationCounter == null
                || node instanceof InstrumentableFactory.WrapperNode
                || node instanceof RootNode) {
            return true;
        } else {
            Node childNode = node;
            assert !(node instanceof InstrumentableFactory.WrapperNode);
            Node parent = node.getParent();
            //assert !(parent instanceof EagerPrimitive);
            if(parent instanceof InstrumentableFactory.WrapperNode) {
                childNode = parent;
                parent = parent.getParent();
            }
            assert !(parent instanceof InstrumentableFactory.WrapperNode);
            int i = 0, childIndex = -1;
            ArrayList<String> childClassNames = new ArrayList<>();
            for(Node child : NodeUtil.findNodeChildren(parent)) {
                if(child == childNode) {
                    childIndex = i;
                }
                if(child instanceof InstrumentableFactory.WrapperNode) {
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
            countPattern(parent, childClassNames, childIndex);
            return true;
        }
    }

    private void countPattern(Node parent, List<String> childClasses, int childIndex) {
        Pattern pattern = new Pattern(parent.getClass().getName(),
                childIndex,
                childClasses);
        patterns.computeIfAbsent(pattern, k -> 0);
        patterns.put(pattern, patterns.get(pattern) + 1);
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
        Set<Candidate> candidates = new HashSet<>();
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
                // if there is enough variability in the class of this child:
                if(patternsWithIndex.size() > 2) {
                    for(Pattern patternWithIndex : patternsWithIndex) {
                        Candidate candidate = new Candidate(parentClass,
                                                            patternWithIndex.getChildClasses().get(index),
                                                            index,
                                                            patterns.get(patternWithIndex));
                        candidates.add(candidate);
                    }
                }
            }
        }
        List<Candidate> topCandidates = candidates.stream()
                                        .filter(c -> !c.getParentClass().endsWith("SequenceNode"))
                                        .sorted((Candidate c1, Candidate c2) ->
                                                ((Integer)c1.getActivations()).compareTo(c2.getActivations()))
                                        .limit(10)
                                        .collect(Collectors.toList());
        for(Candidate candidate : topCandidates) {
            System.out.println(String.format("%s -(%d)> %s: %d",
                    abbreviate(candidate.getParentClass()),
                    candidate.getChildIndex(),
                    abbreviate(candidate.getChildClass()),
                    candidate.getActivations()));
        }
    }

    static private class Candidate {
        private final String parentClass, childClass;
        private final int childIndex, activations;

        public String getParentClass() {
            return parentClass;
        }

        public String getChildClass() {
            return childClass;
        }

        public int getChildIndex() {
            return childIndex;
        }

        public int getActivations() {
            return activations;
        }

        public Candidate(String parentClass, String childClass, int childIndex, int activations) {
            this.parentClass = parentClass;
            this.childClass = childClass;
            this.childIndex = childIndex;
            this.activations = activations;
        }
    }

    static public class Pattern {
        private final String parentClass;
        private final int childIndex;

        public int getChildIndex() {
            return childIndex;
        }

        public List<String> getChildClasses() {
            return childClasses;
        }

        public String getParentClass() {
            return parentClass;
        }

        private List<String> childClasses;

        public Pattern(String parentClass, int childIndex, List<String> childClasses) {
            this.parentClass = parentClass;
            this.childIndex = childIndex;
            this.childClasses = childClasses;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + parentClass.hashCode();
            result = 37 * result + ((Integer)childIndex).hashCode();
            result = 37 * result + childClasses.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof Pattern))
                return false;
            Pattern otherPattern = (Pattern)other;
            return parentClass.equals(otherPattern.getParentClass())
                    && childIndex == otherPattern.getChildIndex()
                    && childClasses.equals(otherPattern.getChildClasses());
        }
    }
}
