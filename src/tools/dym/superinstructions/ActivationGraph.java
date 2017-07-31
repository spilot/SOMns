package tools.dym.superinstructions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

  private String nodeName(ActivationNode node) {
    return String.format("n%d", node.hashCode()).replace('-', '_');
  }

  private String childColor(int childIndex) {
    String[] colors = { "blue", "green", "cyan", "yellow",
            "brown", "red", "hotpink", "springgreen", "peru",
            "peachpuff", "rosybrown"};
    if(childIndex >= colors.length)
      return "black";
    return colors[childIndex];
  }

  public void writeToGraph() {
    System.out.println("digraph activations {");
    for(ActivationNode node : nodes) {
      System.out.println(String.format("%s [label=\"%s\"];", nodeName(node), node.getClassName()));
    }
    for(ActivationEdge edge : edges) {
      System.out.println(String.format("%s -> %s [color=%s];",
              nodeName(edge.getParent()),
              nodeName(edge.getChild()),
              childColor(edge.getChildIndex())));
    }
    System.out.println("}");
  }
}
