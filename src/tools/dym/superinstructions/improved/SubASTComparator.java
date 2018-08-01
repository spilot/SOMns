package tools.dym.superinstructions.improved;

import java.util.Comparator;


public class SubASTComparator implements Comparator<AbstractSubAST> {

  public final static SubASTComparator DEFAULT_SCORING =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public long score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.stream()
                                     .mapToLong((singleSubAST) -> singleSubAST.score(this))
                                     .sum();
        }

        @Override
        public long score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public long score(final SingleSubASTLeaf subAST) {
          return subAST.totalActivations();
        }

        @Override
        public long score(final SingleSubASTwithChildren subAST) {
          return subAST.totalActivations() / subAST.numberOfNodes();
        }
      });

  public final static SubASTComparator HIGHEST_STATIC_FREQUENCY_FIRST =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public long score(final SingleSubASTwithChildren subAST) {
          return 1;
        }

        @Override
        public long score(final SingleSubASTLeaf subAST) {
          return 1;
        }

        @Override
        public long score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public long score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.size();
        }
      });

  public final static SubASTComparator HIGHEST_ACTIVATIONS_SAVED_FIRST =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public long score(final SingleSubASTwithChildren subAST) {
          final long sum = subAST.totalActivations();
          final long mean = (sum / subAST.numberOfNodes());
          return sum - mean;
        }

        @Override
        public long score(final SingleSubASTLeaf subAST) {
          return subAST.totalActivations();
        }

        @Override
        public long score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public long score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.stream()
                                     .mapToLong((singleSubAST) -> singleSubAST.score(this))
                                     .sum();

        }
      });

  private final ScoreVisitor scoreVisitor;

  private SubASTComparator(final ScoreVisitor scoreVisitor) {
    this.scoreVisitor = scoreVisitor;
  }

  @Override
  public int compare(final AbstractSubAST arg0, final AbstractSubAST arg1) {
    return (int) (arg1.score(this.scoreVisitor) - arg0.score(this.scoreVisitor));
  }

  static interface ScoreVisitor {
    public long score(CompoundSubAST subAST);

    public long score(CutSubAST subAST);

    public long score(SingleSubASTLeaf subAST);

    public long score(SingleSubASTwithChildren subAST);
  }
}
