package tools.dym.superinstructions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by fred on 31/07/17.
 */
public class ActivationGraph {
  private Set<ActivationNode> nodes;
  private Set<ActivationEdge> edges;
  private Map<ActivationEdge, Long> activations;

  public ActivationGraph() {
    this.nodes = new HashSet<>();
    this.edges = new HashSet<>();
    this.activations = new HashMap<>();
  }

  public void addNode(ActivationNode node) {
    nodes.add(node);
  }

  public boolean removeNode(ActivationNode node) {
    if(allEdges(node).count() != 0) {
      throw new RuntimeException(String.format("Node %s has non-zero number of edges", node));
    }
    return nodes.remove(node);
  }

  public boolean removeNodes(Collection<ActivationNode> nodes) {
    boolean changed = false;
    for(ActivationNode node : nodes) {
      changed |= removeNode(node);
    }
    return changed;
  }

  public void addEdge(ActivationEdge edge) {
    edges.add(edge);
  }

  public boolean removeEdge(ActivationEdge edge) {
    activations.remove(edge);
    return edges.remove(edge);
  }

  public boolean removeEdges(Collection<ActivationEdge> edges) {
    boolean changed = false;
    for(ActivationEdge edge : edges) {
      changed |= removeEdge(edge);
    }
    return changed;
  }

  public ActivationNode getOrCreateNode(String className) {
    ActivationNode node = new ActivationNode(className);
    if(!nodes.contains(node)) {
      nodes.add(node);
    }
    return node;
  }

  public ActivationEdge getOrCreateEdge(ActivationNode parent, ActivationNode child, int childIndex,
                                        String javaType) {
    ActivationEdge edge = new ActivationEdge(parent, child, childIndex, javaType);
    if(!edges.contains(edge)) {
      edges.add(edge);
    }
    return edge;
  }

  public Set<ActivationNode> getNodes() {
    return nodes;
  }

  public Set<ActivationEdge> getEdges() {
    return edges;
  }

  public void printGraph() {
    for(ActivationNode node : nodes) {
      Set<ActivationEdge> outgoing = edges.stream()
                                          .filter(e -> e.getParent().equals(node))
                                          .collect(Collectors.toSet());
      System.out.println(node.getClassName());
      for(ActivationEdge edge : outgoing) {
        System.out.println("  -> " + edge.getChild().getClassName());
      }
      System.out.println();
    }
  }

  public Stream<ActivationEdge> outgoingEdges(ActivationNode node) {
    return edges.stream().filter(e -> e.getParent().equals(node));
  }

  public Map<Integer, Set<ActivationEdge>> outgoingEdgesByChildIndex(ActivationNode node) {
    HashMap<Integer, Set<ActivationEdge>> outgoingByIndex = new HashMap<>();
    outgoingEdges(node).forEach(edge ->
      outgoingByIndex.computeIfAbsent(edge.getChildIndex(), (idx) -> new HashSet<>()).add(edge)
    );
    return outgoingByIndex;
  }

  public Stream<ActivationEdge> incomingEdges(ActivationNode node) {
    return edges.stream().filter(e -> e.getChild().equals(node));
  }

  public Stream<ActivationEdge> allEdges(ActivationNode node) {
    return Stream.concat(incomingEdges(node), outgoingEdges(node));
  }

  public void addActivations(ActivationEdge edge, long count) {
    if(!activations.containsKey(edge)) {
      activations.put(edge, count);
    } else {
      activations.put(edge, activations.get(edge) + count);
    }
  }

  public long getActivations(ActivationEdge edge) {
    return activations.getOrDefault(edge, 0L);
  }

  public Stream<ActivationEdge> sortByActivations(Stream<ActivationEdge> stream) {
    return stream.sorted(Comparator.comparingLong(e -> getActivations((ActivationEdge)e)).reversed());
  }

  /*
  public ActivationGraph mergeSuperinstruction(ActivationEdge edge, String className) {
    ActivationGraph newGraph = new ActivationGraph();
    // first, add all old nodes
    for(ActivationNode oldNode : getNodes()) {
      newGraph.addNode(oldNode);
    }
    // then, add the edges
    for(ActivationEdge oldEdge : getEdges()) {
      newGraph.addEdge(oldEdge);
    }
    ActivationNode superNode = newGraph.getOrCreateNode(className);
    Set<ActivationEdge> incoming = incomingEdges(edge.getParent()).collect(Collectors.toSet());
    Set<ActivationEdge> outgoing = outgoingEdges(edge.getChild()).collect(Collectors.toSet());
    for(ActivationEdge inc : incoming) {
      //newGraph.getOrCreateEdge(inc.gtParent(), )
      // TODO: What to do?
    }
    return newGraph;
  }*/
}
