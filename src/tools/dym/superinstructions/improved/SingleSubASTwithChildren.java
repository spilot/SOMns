package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class SingleSubASTwithChildren extends SingleSubAST {
  private final SingleSubAST[]   children;
  List<SingleSubASTwithChildren> powerSetCache;

  SingleSubASTwithChildren(final Node enclosedNode,
      final SingleSubAST[] children,
      final Map<String, Long> activationsByType) {
    super(enclosedNode, activationsByType);
    this.children = children;
    this.totalLocalActivations = activationsByType.values().stream().reduce(0L, Long::sum);
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
      final SingleSubAST child = copyFrom.children[i];
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
  public boolean congruent(final SingleSubAST obj) {
    if (obj instanceof SingleSubASTwithChildren) {
      final SingleSubASTwithChildren arg = (SingleSubASTwithChildren) obj;

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
        if (!children[i].congruent(arg.children[i])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @return false if we can already be sure this subtree will never make a good
   *         superinstruction, true otherwise.
   */
  @Override
  boolean isRelevant() {
    return this.totalActivations() > this.numberOfNodes();
  }

  @Override
  int numberOfNodes() {
    int result = 1;
    for (final SingleSubAST child : children) {
      result += child.numberOfNodes();
    }
    return result;
  }

  @Override
  long totalActivations() {
    long result = totalLocalActivations;
    for (final SingleSubAST child : children) {
      result += child.totalActivations();
    }
    return result;
  }

  @Override
  public void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {
    if (this.isRelevant()) {
      action.accept(this);

      if (powerSetCache == null) {
        powerSetCache = createRelevantPowerset();
      }
      powerSetCache.forEach(action);

      for (final SingleSubAST child : children) {
        child.forEachTransitiveRelevantSubAST(action);
      }
    }
  }

  private List<SingleSubASTwithChildren> createRelevantPowerset() {
    final List<SingleSubASTwithChildren> input =
        recursivelyAddAllChildrenWithChildrenToAccumulator(new ArrayList<>());
    final List<SingleSubASTwithChildren> resultList = new ArrayList<>();
    outer: for (long i = 1; i < input.size() * input.size(); i++) {
      final List<SingleSubASTwithChildren> elementOfPowerSet = new ArrayList<>();
      for (int index = 0; index < input.size(); index++) {
        if (((i >>> index) & 1) == 1) {
          elementOfPowerSet.add(input.get(index));
        }
      }
      final SingleSubASTwithChildren result =
          new SingleSubASTwithChildren(this, elementOfPowerSet);

      // we only add the current result to the resultList only if it's relevant
      if (result.isRelevant()) {

        for (final SingleSubASTwithChildren item : resultList) {
          // this last check is made because the above method of creating the power set yields
          // some
          // duplicate combinations. Consider the tree:
          // a
          // / \
          // b c
          // Now, we generate all 2^n combinations, including !a, b, c; !a, b, !c etc., but all
          // combinations where a is pruned that differ only in the tree below a are congruent.
          // Therefore, we continue the outer loop (thus not adding the current result) if a
          // result we already have equals the current result
          if (item.congruent(result)) {
            continue outer;
          }
        }
        resultList.add(result);
      }

      // if (result.isRelevant() &&
      //
      // // the last check is made because the above method of creating the power set yields
      // some
      // // duplicate combinations. Consider the tree:
      // // a
      // // / \
      // // b c
      // // Now, we generate all 2^n combinations, including !a, b, c; !a, b, !c etc., but all
      // // combinations where a is pruned that differ only in the tree below a are equal.
      // // Therefore, we eliminate them with this check.
      // resultList.stream().noneMatch((item) -> item.congruent(result))) {
      //
      // resultList.add(result);
      // }
    }

    return resultList;

  }

  private List<SingleSubASTwithChildren> recursivelyAddAllChildrenWithChildrenToAccumulator(
      final List<SingleSubASTwithChildren> accumulator) {
    for (final SingleSubAST child : children) {
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
    if (this.sourceSection != null) {
      accumulator.append(prefix)
                 .append(this.enclosedNodeType.getSimpleName())
                 .append('(')
                 .append(this.sourceSection)
                 .append("): ")
                 .append(activationsByType)
                 .append('\n');
    } else {
      accumulator.append(prefix)
                 .append(this.enclosedNodeType.getSimpleName())
                 .append('(')
                 .append(this.sourceFileName)
                 .append(' ')
                 .append(this.sourceFileIndex)
                 .append('-')
                 .append(sourceFileIndex + sourceSectionLength)
                 .append("): ")
                 .append(activationsByType)
                 .append('\n');
    }
    for (final SingleSubAST child : children) {
      child.toStringRecursive(accumulator, prefix + "  ");
    }
    return accumulator;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  ArrayList<SingleSubAST> getChildren() {
    final ArrayList<SingleSubAST> result = new ArrayList<>();
    for (final SingleSubAST child : children) {
      result.add(child);
    }
    return result;
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if ((that instanceof SingleSubASTwithChildren)) {
      final SingleSubASTwithChildren thatAST = (SingleSubASTwithChildren) that;
      if (!(this.enclosedNodeType == thatAST.enclosedNodeType
          && this.sourceFileIndex == thatAST.sourceFileIndex
          && this.sourceSectionLength == thatAST.sourceSectionLength
          && this.children.length == thatAST.children.length
          && this.sourceFileName.equals(thatAST.sourceFileName))) {
        return false;
      }
      for (int i = 0; i < children.length; i++) {
        if (!(this.children[i].equals(thatAST.children[i]))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
