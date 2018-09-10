package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.Consumer;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public abstract class GroupedSubAST extends AbstractSubAST {

  List<SingleSubAST> enclosedNodes;

  GroupedSubAST(final SingleSubAST firstNode) {
    super();
    this.enclosedNodes = new ArrayList<>();
    enclosedNodes.add(firstNode);
  }

  @Override
  AbstractSubAST add(final AbstractSubAST arg) {
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
  List<AbstractSubAST> commonSubASTs(final AbstractSubAST arg,
      final List<AbstractSubAST> accumulator) {
    this.forEachTransitiveRelevantSubAST(
        (mySubAST) -> mySubAST.commonSubASTs(arg, accumulator));
    return accumulator;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  boolean congruent(final GroupedSubAST arg) {
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

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator, final String prefix) {
    enclosedNodes.forEach((subAST) -> {
      subAST.toStringRecursive(accumulator, prefix).append('\n');
    });
    return accumulator;
  }

  boolean similar(final AbstractSubAST abstractSubAST) {
    if (this.getFirstNode().isLeaf()) {
      return false;
    }
    return ((SingleSubASTwithChildren) getFirstNode()).similar(abstractSubAST);
  }

  @Override
  Optional<VirtualSubAST> commonPart(final AbstractSubAST that) {
    if (that instanceof SingleSubASTwithChildren) {
      return commonPart((SingleSubASTwithChildren) that);
    }
    return commonPart((GroupedSubAST) that);
  }

  Optional<VirtualSubAST> commonPart(final SingleSubASTwithChildren that) {
    assert this.similar(that);
    if (getFirstNode().isLeaf()) {
      return Optional.empty();
    }
    List<PotentialPruningSite> worklist = new ArrayList<>();
    SingleSubAST[][] ra =
        new SingleSubAST[enclosedNodes.size() + 1][that.children.length];
    ra[0] = Arrays.copyOf(that.children, that.children.length);
    for (int i = 0; i < enclosedNodes.size(); i++) {
      ra[i + 1] =
          Arrays.copyOf(((SingleSubASTwithChildren) enclosedNodes.get(i)).children,
              ((SingleSubASTwithChildren) enclosedNodes.get(i)).children.length);
    }
    for (int i = 0; i < ra[0].length; i++) {
      worklist.add(new PotentialPruningSite(ra, i));
    }
    while (!worklist.isEmpty()) {
      final PotentialPruningSite head = worklist.remove(worklist.size() - 1);
      if (head.isPruningSite()) {
        head.prune();
      } else {
        head.yield(worklist);
      }
    }
    VirtualSubAST result = new VirtualSubAST(new SingleSubASTwithChildren(that, ra[0]));
  }

  class PotentialPruningSite {
    SingleSubAST[][] ra;   // must be copied once!
    int              index;

    PotentialPruningSite(final SingleSubAST[][] ra, final int index) {
      this.ra = ra;
      this.index = index;
    }

    boolean isPruningSite() {
      if (ra[0][index].isLeaf()) {
        for (int i = 1; i < ra.length; i++) {
          if (!ra[i][index].isLeaf()
              || ra[i][index].enclosedNodeType != ra[0][index].enclosedNodeType) {
            return true;
          }
        }
        return false;
      }
      for (int i = 1; i < ra.length; i++) {
        if (ra[i][index].enclosedNodeType != ra[0][index].enclosedNodeType
            || ((SingleSubASTwithChildren) ra[i][index]).children.length != ((SingleSubASTwithChildren) ra[0][index]).children.length) {
          return true;
        }
      }
      return false;
    }

    void prune() {
      assert isPruningSite();
      for (int i = 0; i < ra.length; i++) {
        ra[i][index] = new CutSubAST(ra[i][index]);
      }
    }

    void yield(final List<PotentialPruningSite> accumulator) {
      assert !isPruningSite();
      if (!ra[0][index].isLeaf()) {
        for (int i =
            0; i < ((SingleSubASTwithChildren) ra[0][index]).children.length; i++) {
          SingleSubAST[][] newRA =
              new SingleSubAST[ra.length][((SingleSubASTwithChildren) ra[0][index]).children.length];
          for (int j = 0; j < newRA.length; j++) {
            newRA[j] = ((SingleSubASTwithChildren) ra[j][index]).children;
          }
          accumulator.add(new PotentialPruningSite(newRA, i));
        }
      }
    }

  }

  Optional<VirtualSubAST> commonPart(final GroupedSubAST that) {
    assert !that.getFirstNode().isLeaf();
    Optional<VirtualSubAST> maybeClone =
        commonPart((SingleSubASTwithChildren) that.getFirstNode());
    if (!maybeClone.isPresent()) {
      return Optional.empty();
    }
    VirtualSubAST clone = maybeClone.get();
    ListIterator<SingleSubAST> iterator =
        that.enclosedNodes.listIterator(1);
    while (iterator.hasNext()) {
      AbstractSubAST commonPart =
          ((SingleSubASTwithChildren) clone.getFirstNode()).commonPart(
              (SingleSubASTwithChildren) iterator.next());
      assert commonPart.congruent(that);
      assert commonPart.congruent(clone.getFirstNode());
      clone.add(commonPart);
    }
    return Optional.of(clone);
  }
}
