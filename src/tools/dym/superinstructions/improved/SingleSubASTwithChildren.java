package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class SingleSubASTwithChildren extends SingleSubAST {
  private final SingleSubAST[]          children;
  private Set<SingleSubASTwithChildren> powerSetCache, relevantPowerSetCache;

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
  boolean congruent(final SingleSubAST obj) {
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
    boolean atLeastOneNonCutChild = false;
    for (final SingleSubAST child : children) {
      if (!(child instanceof CutSubAST)) {
        atLeastOneNonCutChild = true;
        break;
      }
    }
    return atLeastOneNonCutChild && (this.totalActivations() > this.numberOfNodes());
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
  void forEachTransitiveRelevantSubAST(final Consumer<SingleSubAST> action) {
    if (this.isRelevant()) {
      // we don't need to call action.accept(this), because *this* is a member of its own
      // power set and we call action.accept on each member of the power set
      // System.out.println("iterating powerset size=" + getRelevantPowerset().size());
      // if (getRelevantPowerset().size() > 70000) {
      // System.out.println(this);
      // }
      // getRelevantPowerset().forEach(action);
      action.accept(this); // TODO

      for (final SingleSubAST child : children) {
        child.forEachTransitiveRelevantSubAST(action);
      }
    }
  }

  Set<SingleSubASTwithChildren> getRelevantPowerset() {
    if (relevantPowerSetCache == null) {
      relevantPowerSetCache = getPowerset().stream()
                                           .filter(SingleSubAST::isRelevant)
                                           .collect(Collectors.toSet());
      assert (!this.isRelevant()) || relevantPowerSetCache.size() > 0;
    }
    return relevantPowerSetCache;
  }

  private Set<SingleSubASTwithChildren> getPowerset() {
    if (powerSetCache == null) {
      powerSetCache = computePowerset();
      assert powerSetCache.size() > 0;
    }
    return powerSetCache;
  }

  private Set<SingleSubASTwithChildren> computePowerset() {
    int n = 0;
    int[] childrenWithChildrenIndices = new int[children.length];
    for (int i = 0; i < children.length; i++) {
      final SingleSubAST child = children[i];
      if (child instanceof SingleSubASTwithChildren) {
        childrenWithChildrenIndices[n] = i;
        n++;
      }
    }
    // assert n <= 31;
    if (n > 31) {
      System.out.println(this);
    }
    Set<SingleSubASTwithChildren> result = new HashSet<>();
    for (int configuration = 0; configuration < (1 << n); configuration++) {
      // TODO find a way to avoid creating this object
      List<SingleSubASTwithChildren> clones = new ArrayList<>();
      clones.add(new SingleSubASTwithChildren(this,
          Arrays.copyOf(children, children.length)));

      // for each immediate child with children
      for (int bitIndex = 0; bitIndex < n; bitIndex++) {
        final int currentChildwithChildrenIndex =
            childrenWithChildrenIndices[bitIndex];
        assert children[currentChildwithChildrenIndex] instanceof SingleSubASTwithChildren;

        if (((configuration >>> bitIndex) & 1) == 1) {
          List<SingleSubASTwithChildren> childsPowerset = new ArrayList<>();
          childsPowerset.addAll(
              ((SingleSubASTwithChildren) children[currentChildwithChildrenIndex]).getPowerset());

          assert childsPowerset.size() > 0;

          // for the head of the powerset change the clones in-place
          clones.forEach((clone) -> {
            clone.children[currentChildwithChildrenIndex] = childsPowerset.get(0);
          });

          // if there are more, clone the clones for the tail
          if (childsPowerset.size() > 1) {
            for (final ListIterator<SingleSubASTwithChildren> clonesItor =
                clones.listIterator(); clonesItor.hasNext();) {
              final SingleSubASTwithChildren clone = clonesItor.next();
              for (final ListIterator<SingleSubASTwithChildren> powerSetItor =
                  childsPowerset.listIterator(1); powerSetItor.hasNext();) {
                final SingleSubAST childsPowersetItem = powerSetItor.next();

                final SingleSubAST[] newChildren =
                    Arrays.copyOf(clone.children, clone.children.length);
                newChildren[currentChildwithChildrenIndex] = childsPowersetItem;
                clonesItor.add(new SingleSubASTwithChildren(clone, newChildren));
              }
            }
          }
        } else {
          assert ((configuration >>> bitIndex) & 1) == 0;
          for (final SingleSubASTwithChildren clone : clones) {
            clone.children[currentChildwithChildrenIndex] =
                new CutSubAST(children[currentChildwithChildrenIndex]);
          }
        }
      }
      result.addAll(clones);
      // System.out.println(result.size());
      // if (result.size() > 1000) {
      // System.out.println(this);
      // }
    }
    assert result.size() > 0;
    return result;
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
  void addWithIncrementalMean(final SingleSubAST arg) {
    assert arg instanceof SingleSubASTwithChildren;
    assert arg.equals(this);
    incrementalMeanUnion(arg);

    final SingleSubASTwithChildren that = (SingleSubASTwithChildren) arg;
    assert this.children.length == that.children.length;
    for (int i = 0; i < children.length; i++) {
      children[i].addWithIncrementalMean(that.children[i]);
    }
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

  @Override
  public int hashCode() {
    return ((1 << 31) - 1) * Objects.hash(this.enclosedNodeType, this.sourceFileName,
        Integer.valueOf(this.sourceFileIndex),
        Integer.valueOf(this.sourceSectionLength))
        + Arrays.hashCode(children);
  }

  /**
   * Head node types and number of children are equal, a maximum of two children have different
   * types.
   *
   * The common part of two non-similar Sub-ASTs cannot be a superinstruction, because it would
   * be just one node with only cut children
   *
   * Is it symmetric? transitive? reflexive?
   */
  boolean similarInternal(final SingleSubASTwithChildren that) {
    if (this.enclosedNodeType != that.enclosedNodeType) {
      return false;
    }
    if (this.children.length != that.children.length) {
      return false;
    }
    // this and that are similar if there is at least
    // 1. a child with children that is of equal type in both this and that
    // or
    // 2. more than one child that is of equal type in this and that (count them as "count")
    //
    int count = 0;
    for (int i = 0; i < this.children.length; i++) {
      if (this.children[i].enclosedNodeType == that.children[i].enclosedNodeType) {
        if (++count > 1 || (!this.children[i].isLeaf() && !that.children[i].isLeaf())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Integer.MAX_VALUE means very similar, Integer.MIN_VALUE means very dissimilar
   *
   * TODO Symmetry? Reflexivity? Transitivity?
   */
  int similarity(final SingleSubASTwithChildren that) {
    if (this.equals(that) || this.congruent(that)) {
      return Integer.MAX_VALUE;
    }
    if (!this.similarInternal(that)) {
      return Integer.MIN_VALUE;
    }
    final SingleSubASTwithChildren clone = new SingleSubASTwithChildren(that,
        Arrays.copyOf(that.children, that.children.length));
    final List<PotentialPruningSite> worklist = new ArrayList<>();
    int result = this.numberOfNodes() + that.numberOfNodes();
    for (int i = 0; i < children.length; i++) {
      worklist.add(this.new PotentialPruningSite(clone.children, i));
    }
    while (!worklist.isEmpty()) {
      final PotentialPruningSite head = worklist.remove(worklist.size() - 1);
      if (head.isPruningSite()) {
        result -= head.numberOfNodes();
        assert result <= this.numberOfNodes() + that.numberOfNodes();
      } else {
        head.yield(worklist);
      }
    }
    return result;
  }

  /**
   * Remember to filter for relevancy.
   */
  SingleSubASTwithChildren commonPart(final SingleSubASTwithChildren that) {
    assert this.similarInternal(that);
    final SingleSubASTwithChildren clone = new SingleSubASTwithChildren(that,
        Arrays.copyOf(that.children, that.children.length));
    List<PotentialPruningSite> worklist = new ArrayList<>();
    for (int i = 0; i < children.length; i++) {
      worklist.add(this.new PotentialPruningSite(clone.children, i));
    }
    while (!worklist.isEmpty()) {
      final PotentialPruningSite head = worklist.remove(worklist.size() - 1);
      if (head.isPruningSite()) {
        head.prune();
      } else {
        head.yield(worklist);
      }
    }
    // assert clone.congruent(this);
    return clone;
  }

  class PotentialPruningSite {
    final SingleSubAST[] clone;
    final int            index;

    PotentialPruningSite(final SingleSubAST[] clone, final int index) {
      this.clone = clone;
      this.index = index;
    }

    boolean isPruningSite() {
      if (clone[index].enclosedNodeType != children[index].enclosedNodeType) {
        return true;
      }
      if (clone[index].isLeaf() != children[index].isLeaf()) {
        return true;
      }
      if (!clone[index].isLeaf() && !children[index].isLeaf()) {
        return ((SingleSubASTwithChildren) children[index]).children.length != ((SingleSubASTwithChildren) clone[index]).children.length;
      }
      return false;
    }

    void prune() {
      assert isPruningSite();
      clone[index] = new CutSubAST(clone[index]);
    }

    void yield(final List<PotentialPruningSite> accumulator) {
      assert !isPruningSite();
      if (!clone[index].isLeaf() && !children[index].isLeaf()) {
        final SingleSubASTwithChildren placeInClone, placeInThis;
        placeInClone = (SingleSubASTwithChildren) clone[index];
        placeInThis = (SingleSubASTwithChildren) children[index];
        for (int i = 0; i < placeInClone.children.length; i++) {
          accumulator.add(
              placeInThis.new PotentialPruningSite(placeInClone.children, i));
        }
      }
    }

    int numberOfNodes() {
      return children[index].numberOfNodes() + clone[index].numberOfNodes();
    }
  }

  boolean similar(final AbstractSubAST that) {
    if (that instanceof SingleSubAST) {
      if (that.isLeaf()) {
        return false;
      }
      return similarInternal((SingleSubASTwithChildren) that);
    }
    assert that instanceof GroupedSubAST;
    return ((GroupedSubAST) that).similar(this);
  }

}
