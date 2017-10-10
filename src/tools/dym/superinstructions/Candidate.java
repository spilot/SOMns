package tools.dym.superinstructions;

import java.util.*;

/**
 * Created by fred on 04/10/17.
 */
public class Candidate {
  private Node rootNode;
  private long score;

  public Candidate(String rootClass, String javaType) {
    this.rootNode = new Node(rootClass, javaType);
  }

  public Node getRoot() {
    return rootNode;
  }

  public String prettyPrint() {
    StringBuilder builder = new StringBuilder();
    rootNode.prettyPrint(builder, 0);
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Candidate candidate = (Candidate) o;
    return Objects.equals(rootNode, candidate.rootNode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootNode);
  }

  public long getScore() {
    return score;
  }

  public void setScore(long score) {
    this.score = score;
  }

  public static class Node {
    private String nodeClass;
    private String javaType;
    private Map<Integer, Node> children;

    public Node(String nodeClass, String javaType) {
      this.nodeClass = nodeClass;
      this.javaType = javaType;
      this.children = new HashMap<>();
    }

    public Node setChild(int index, String childClass, String javaType) {
      Node node = new Node(childClass, javaType);
      this.children.put(index, node);
      return node;
    }

    public String getNodeClass() {
      return nodeClass;
    }

    public Map<Integer, Node> getChildren() {
      return children;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Node node = (Node) o;
      return Objects.equals(nodeClass, node.nodeClass) &&
              Objects.equals(javaType, node.javaType) &&
              Objects.equals(children, node.children);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeClass, javaType, children);
    }

    public void prettyPrint(StringBuilder builder, int level) {
      for (int i = 0; i < level; i++) {
        builder.append("  ");
      }
      builder.append(ActivationContext.abbreviateClass(nodeClass))
              .append('[')
              .append(ActivationContext.abbreviateClass(javaType))
              .append(']')
              .append('\n');
      int maxKey = children.keySet().stream()
              .max(Comparator.comparingInt(e -> e))
              .orElse(-1);
      for (int slot = 0; slot <= maxKey; slot++) {
        if(children.containsKey(slot)) {
          children.get(slot).prettyPrint(builder, level + 1);
        } else {
          Node dummy = new Node("?", "?");
          dummy.prettyPrint(builder, level + 1);
        }
      }
    }
  }
}
