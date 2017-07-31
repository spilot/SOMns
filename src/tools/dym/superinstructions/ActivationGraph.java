package tools.dym.superinstructions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by fred on 31/07/17.
 */
public class ActivationGraph {
  private Set<ActivationNode> nodes;
  private Set<ActivationEdge> edges;

  public ActivationGraph() {
    this.nodes = new HashSet<>();
    this.edges = new HashSet<>();
  }

  public void addNode(ActivationNode node) {
    nodes.add(node);
  }

  public void addEdge(ActivationEdge edge) {
    edges.add(edge);
  }

  public ActivationNode getOrCreateNode(String className) {
    List<ActivationNode> matching = nodes.stream()
            .filter(n -> n.getClassName().equals(className))
            .collect(Collectors.toList());
    assert matching.size() <= 1;
    if(matching.isEmpty()) {
      ActivationNode node = new ActivationNode(className);
      nodes.add(node);
      return node;
    } else {
      return matching.get(0);
    }
  }

  public ActivationEdge getOrCreateEdge(ActivationNode parent, ActivationNode child, int childIndex,
                                        String javaType) {
    List<ActivationEdge> matching = edges.stream()
            .filter(e -> e.getParent().equals(parent)
                      && e.getChild().equals(child)
                      && e.getChildIndex() == childIndex
                      && e.getJavaType().equals(javaType))
            .collect(Collectors.toList());
    assert matching.size() <= 1;
    if(matching.isEmpty()) {
      ActivationEdge edge = new ActivationEdge(parent, child, childIndex, javaType, 0);
      edges.add(edge);
      return edge;
    } else {
      return matching.get(0);
    }
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

  public void writeToGraph() {
    Path path = Paths.get("graph.dot");
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write("digraph activations {\n");
      for(ActivationNode node : nodes) {
        writer.write(String.format("\"%s\";\n", node.getClassName()));
      }
      for(ActivationEdge edge : edges) {
        writer.write(String.format("\"%s\" -> \"%s\" [childindex=%d,javatype=\"%s\",activations=%d];\n",
                edge.getParent().getClassName(),
                edge.getChild().getClassName(),
                edge.getChildIndex(),
                edge.getJavaType(),
                edge.getActivations()));
      }
      writer.write("}");
    } catch (IOException e) {
      System.out.println("Could not write graph.dot:");
      e.printStackTrace();
    }
  }
}
