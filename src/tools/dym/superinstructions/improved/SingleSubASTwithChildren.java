package tools.dym.superinstructions.improved;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;

import som.interpreter.nodes.SequenceNode;


class SingleSubASTwithChildren extends SingleSubAST {
  private SingleSubAST[] children;

  SingleSubASTwithChildren(final Node enclosedNode,
      final SingleSubAST[] children,
      final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
    this.children = children;
  }

  SingleSubASTwithChildren(final SingleSubAST copyFrom,
      final SingleSubAST[] children) {
    super(copyFrom);
    this.children = children;
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof SingleSubASTwithChildren)) {
      return false;
    }
    SingleSubASTwithChildren arg = (SingleSubASTwithChildren) obj;

    if (
    // obviously, the enclosed node types need to be equal
    this.enclosedNodeType != arg.enclosedNodeType
        // now we need to compare the children.
        // are there equal numbers of children?
        || this.children.length != arg.children.length) {
      return false;
    }
    // there are equal numbers of children.
    // lets compare them one by one
    for (int i = 0; i < children.length; i++) {
      if (!children[i].equals(arg.children[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  void computeScore() {
    score = totalActivations() / numberOfNodes();
  }

  /**
   * @return true if this tree contains nodes with activations and is not a leaf
   */
  @Override
  boolean isRelevant() {
    if (this.totalActivations() > 0) {
      return true;
    }
    return Arrays.stream(children).anyMatch((child) -> child.isRelevant());
  }

  @Override
  int numberOfNodes() {
    int result = 1;
    for (SingleSubAST child : children) {
      result += child.numberOfNodes();
    }
    return result;
  }

  @Override
  long totalActivations() {
    long result = super.totalActivations();
    for (SingleSubAST child : children) {
      result += child.totalActivations();
    }
    return result;
  }

  @Override
  List<SingleSubAST> allSubASTs(final List<SingleSubAST> accumulator) {
    for (SingleSubAST child : children) {
      if (child.isNotLeaf()) {
        accumulator.add(child);
        child.allSubASTs(accumulator);
      }
    }
    addPowerSetRecursively(accumulator);
    return accumulator;
  }

  private void addPowerSetRecursively(final List<SingleSubAST> accumulator) {
    boolean allChildrenAreLeaves = true;
    for (SingleSubAST child : children) {
      if (!child.isLeaf() && !(child.enclosedNodeType == SequenceNode.class)) {
        allChildrenAreLeaves = false;
        break;
      }
    }
    if (children.length == 1 || this.totalActivations() == 0 || allChildrenAreLeaves) {
      // this greatly reduces complexity
      // and I hope eliminates no usable candidates
      accumulator.add(this);
    } else {
      addPowerSetRecursively(children.length - 1, new SingleSubAST[children.length],
          accumulator);
    }
  }

  private void addPowerSetRecursively(final int i, final SingleSubAST[] currentCombination,
      final List<SingleSubAST> accumulator) {
    currentCombination[i] = children[i];
    if (i == 0) {
      if (!children[i].isLeaf() && children[i].enclosedNodeType != SequenceNode.class) {
        accumulator.add(new SingleSubASTwithChildren(SingleSubASTwithChildren.this,
            combinationWithoutSubtree(i, currentCombination)));
      }
      for (int j = 0; j < children.length; j++) {
        if (children[j] != currentCombination[j]) {
          accumulator.add(
              new SingleSubASTwithChildren(SingleSubASTwithChildren.this, currentCombination));
          break;
        }
      }

    } else {
      addPowerSetRecursively(i - 1, currentCombination, accumulator);
      if (!children[i].isLeaf() && children[i].enclosedNodeType != SequenceNode.class) {
        SingleSubAST[] combinationWithoutSubtree =
            Arrays.copyOf(currentCombination, currentCombination.length);
        combinationWithoutSubtree[i] = new VirtualSingleSubASTLeaf(children[i]);
        addPowerSetRecursively(i - 1, combinationWithoutSubtree(i, currentCombination),
            accumulator);
      }
    }
  }

  private SingleSubAST[] combinationWithoutSubtree(final int i,
      final SingleSubAST[] currentCombination) {
    SingleSubAST[] combinationWithoutSubtree =
        Arrays.copyOf(currentCombination, currentCombination.length);
    combinationWithoutSubtree[i] = new VirtualSingleSubASTLeaf(children[i]);
    return combinationWithoutSubtree;
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    super.toStringRecursive(accumulator, prefix);
    for (SingleSubAST child : children) {
      child.toStringRecursive(accumulator, prefix + "  ");
    }
    return accumulator;
  }
}
