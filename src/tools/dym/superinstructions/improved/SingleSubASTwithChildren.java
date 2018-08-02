package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


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

  /**
   * Only used for power set creation.
   *
   * @param copyFrom The SingleSubAST to copy the data from.
   * @param cutHere Insert CutSubASTs when encountering these, instead of copying.
   */
  SingleSubASTwithChildren(final SingleSubASTwithChildren copyFrom,
      final List<SingleSubASTwithChildren> cutHere) {
    super(copyFrom);
    // copy children
    this.children = new SingleSubAST[copyFrom.children.length];
    for (int i = 0; i < copyFrom.children.length; i++) {
      SingleSubAST child = copyFrom.children[i];
      if (cutHere.contains(child)) {
        this.children[i] = new CutSubAST(child);
      } else {
        if (child.isLeaf()) {
          this.children[i] = child;
        } else {
          assert child instanceof SingleSubASTwithChildren;
          this.children[i] =
              new SingleSubASTwithChildren((SingleSubASTwithChildren) child, cutHere);
        }
      }
    }
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

  /**
   * @return true if this tree has more nodes than it has activations
   */
  @Override
  boolean isRelevant() {
    return this.totalActivations() > this.numberOfNodes();
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
  public void forEachRelevantSubAST(final Consumer<SingleSubAST> action) {
    if (this.isRelevant()) {
      action.accept(this);

      for (SingleSubAST child : children) {
        child.forEachRelevantSubAST(action);
      }
      if (powerSetCache == null) {
        powerSetCache = createRelevantPowerset();
      }

      powerSetCache.forEach(action);
    }
  }

  List<SingleSubASTwithChildren> powerSetCache;

  private List<SingleSubASTwithChildren> createRelevantPowerset() {
    List<SingleSubASTwithChildren> input =
        recursivelyAddAllChildrenWithChildrenToAccumulator(new ArrayList<>());
    List<SingleSubASTwithChildren> resultList = new ArrayList<>();
    for (long i = 1; i < input.size() * input.size(); i++) {
      final List<SingleSubASTwithChildren> elementOfPowerSet = new ArrayList<>();
      for (int index = 0; index < input.size(); index++) {
        if (((i >>> index) & 1) == 1) {
          elementOfPowerSet.add(input.get(index));
        }
      }
      SingleSubASTwithChildren result = new SingleSubASTwithChildren(this, elementOfPowerSet);
      if (result.isRelevant()) {
        resultList.add(result);
      }
    }
    return resultList;
  }

  private List<SingleSubASTwithChildren> recursivelyAddAllChildrenWithChildrenToAccumulator(
      final List<SingleSubASTwithChildren> accumulator) {
    for (SingleSubAST child : children) {
      if (child instanceof SingleSubASTwithChildren) {
        accumulator.add((SingleSubASTwithChildren) child);
        ((SingleSubASTwithChildren) child).recursivelyAddAllChildrenWithChildrenToAccumulator(
            accumulator);
      }
    }
    return accumulator;
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

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }
}
