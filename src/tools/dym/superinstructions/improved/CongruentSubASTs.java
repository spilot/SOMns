package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import som.vm.VmSettings;
import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public class CongruentSubASTs extends AbstractSubAST {
  List<SingleSubAST> enclosedNodes;

  CongruentSubASTs(final SingleSubAST firstNode) {
    super();
    this.enclosedNodes = new ArrayList<>();
    enclosedNodes.add(firstNode);
  }

  @Override
  CongruentSubASTs add(final AbstractSubAST arg) {
    assert this.congruent(arg);
    arg.forEachDirectSubAST(this::addIfNew);
    return this;
  }

  private void addIfNew(final SingleSubAST item) {
    for (final SingleSubAST enclosedNode : enclosedNodes) {
      assert enclosedNode.congruent(item) : enclosedNode + "\n" + item;
      if (enclosedNode.equals(item)) {
        // this will compute the incremental mean between the two
        enclosedNode.add(item);
        return;

      }
    }
    enclosedNodes.add(item);
  }

  @Override
  double computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  boolean congruent(final CongruentSubASTs arg) {
    // we don't need to check every node against arg because all our enclosedNodes are mutually
    // congruent
    return this.getFirstNode().congruent(arg.getFirstNode());
  }

  @Override
  boolean congruent(final SingleSubAST arg) {
    // we don't need to check every node against arg because all our enclosedNodes are mutually
    // congruent
    return this.getFirstNode().congruent(arg);
  }

  @Override
  void forEachDirectSubAST(final Consumer<SingleSubAST> action) {
    enclosedNodes.forEach(action);
  }

  @Override
  void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {
    for (final SingleSubAST child : enclosedNodes) {
      if (child.isRelevant()) {
        child.forEachTransitiveRelevantSubAST(action);
      }
    }
  }

  SingleSubAST getFirstNode() {
    return this.enclosedNodes.get(0);
  }

  @Override
  boolean isLeaf() {
    return false;
  }

  static SingleSubAST foldMaps(final SingleSubAST lhs, final SingleSubAST rhs) {
    Map<String, Long> activationsByTypeAccumulator = new HashMap<>();
    lhs.activationsByType.keySet().forEach(type -> {
      activationsByTypeAccumulator.put(type, lhs.activationsByType.get(type).get());
    });
    rhs.activationsByType.keySet().forEach(type -> {
      activationsByTypeAccumulator.merge(type,
          rhs.activationsByType.get(type).get(), Long::sum);
    });
    if (lhs.isLeaf()) {
      assert rhs.isLeaf();
      if (lhs instanceof CutSubAST) {
        return new CutSubAST(lhs, activationsByTypeAccumulator);
      }
      assert lhs instanceof SingleSubASTLeaf;
      return new SingleSubASTLeaf(lhs, activationsByTypeAccumulator);
    } else {
      assert !rhs.isLeaf();
      SingleSubASTwithChildren right, left;
      right = (SingleSubASTwithChildren) rhs;
      left = (SingleSubASTwithChildren) lhs;
      assert right.children.length == left.children.length;
      SingleSubAST[] resultChildren = new SingleSubAST[right.children.length];
      for (int i = 0; i < right.children.length; i++) {
        resultChildren[i] = foldMaps(left.children[i], right.children[i]);
      }

      return new SingleSubASTwithChildren(lhs, resultChildren, activationsByTypeAccumulator);
    }
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator, final String prefix) {
    if (VmSettings.SUPERINSTRUCTIONS_REPORT_VERBOSE) {
      enclosedNodes.forEach((subAST) -> {
        subAST.toStringRecursive(accumulator, prefix).append('\n');
      });
      return accumulator;
    } else {
      Optional<SingleSubAST> result = this.enclosedNodes.stream()
                                                        .reduce(CongruentSubASTs::foldMaps);
      assert result.isPresent() : enclosedNodes;
      result.get().toStringRecursive(accumulator, prefix).append('\n');
      return accumulator;
    }
  }
}
