package tools.dym.superinstructions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by fred on 04/10/17.
 */
public class Candidate {
  private Node rootNode;

  public Candidate(String rootClass) {
    this.rootNode = new Node(rootClass);
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

  public static class Node {
    private String nodeClass;
    private List<Node> children;

    public Node(String nodeClass) {
      this.nodeClass = nodeClass;
      this.children = new ArrayList<>();
    }

    public Node addChild(String childClass) {
      Node node = new Node(childClass);
      this.children.add(node);
      return node;
    }

    public String getNodeClass() {
      return nodeClass;
    }

    public List<Node> getChildren() {
      return children;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Node node = (Node) o;
      return Objects.equals(nodeClass, node.nodeClass) &&
              Objects.equals(children, node.children);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeClass, children);
    }

    public void prettyPrint(StringBuilder builder, int level) {
      for (int i = 0; i < level; i++) {
        builder.append("  ");
      }
      /*if (level > 0) {
        builder.append("| ");
      }*/
      builder.append(nodeClass).append('\n');
      for (Node child : children) {
        child.prettyPrint(builder, level + 1);
      }
    }
  }
}
