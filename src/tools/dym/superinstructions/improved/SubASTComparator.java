package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.Comparator;

import tools.dym.superinstructions.improved.SingleSubAST.IncrementalAverage;


class SubASTComparator implements Comparator<AbstractSubAST> {
  static interface ScoreVisitor {
    double score(CompoundSubAST subAST);

    double score(CutSubAST subAST);

    double score(SingleSubASTLeaf subAST);

    double score(SingleSubASTwithChildren subAST);

    String getScoreDescription();

    String getSimpleName();
  }

  private final ScoreVisitor scoreVisitor;

  private SubASTComparator(final ScoreVisitor scoreVisitor) {
    this.scoreVisitor = scoreVisitor;
  }

  final static SubASTComparator HIGHEST_STATIC_FREQUENCY_FIRST =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public double score(final SingleSubASTwithChildren subAST) {
          return 1;
        }

        @Override
        public double score(final SingleSubASTLeaf subAST) {
          return 1;
        }

        @Override
        public double score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public double score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.size();
        }

        @Override
        public String getScoreDescription() {
          return "Number of occurences: ";
        }

        @Override
        public String getSimpleName() {
          return "static";
        }
      });

  final static SubASTComparator HIGHEST_ACTIVATIONS_SAVED_FIRST =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public double score(final SingleSubASTwithChildren subAST) {
          long childrenActi = 0;
          final ArrayList<SingleSubAST> worklist = subAST.getChildren();
          while (!worklist.isEmpty()) {
            final SingleSubAST current = worklist.remove(worklist.size() - 1);
            if (!(current instanceof CutSubAST)) {
              childrenActi +=
                  current.activationsByType.values()
                                           .stream()
                                           .mapToLong(IncrementalAverage::get)
                                           .reduce(0L, Long::sum);
            }
            if (current instanceof SingleSubASTwithChildren) {
              ((SingleSubASTwithChildren) current).getChildren()
                                                  .forEach(worklist::add);
            }
          }
          return childrenActi;
        }

        @Override
        public double score(final SingleSubASTLeaf subAST) {
          return subAST.activationsByType.values()
                                         .stream()
                                         .mapToLong(IncrementalAverage::get)
                                         .reduce(0L, Long::sum);
        }

        @Override
        public double score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public double score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.stream()
                                     .mapToDouble((singleSubAST) -> singleSubAST.score(this))
                                     .sum();
        }

        @Override
        public String getScoreDescription() {
          return "Number of saved activations: ";
        }

        @Override
        public String getSimpleName() {
          return "dynamic";
        }
      });

  final static SubASTComparator HIGHEST_SHARE_OF_TOTAL_ACTIVATIONS_FIRST =
      new SubASTComparator(new ScoreVisitor() {

        @Override
        public double score(final CompoundSubAST subAST) {
          return subAST.enclosedNodes.stream()
                                     .mapToDouble((singleSubAST) -> singleSubAST.score(this))
                                     .sum();
        }

        @Override
        public double score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public double score(final SingleSubASTLeaf subAST) {
          return subAST.score(HIGHEST_ACTIVATIONS_SAVED_FIRST.scoreVisitor)
              / subAST.totalBenchmarkActivations.get();
        }

        @Override
        public double score(final SingleSubASTwithChildren subAST) {
          return subAST.score(HIGHEST_ACTIVATIONS_SAVED_FIRST.scoreVisitor)
              / subAST.totalBenchmarkActivations.get();
        }

        @Override
        public String getScoreDescription() {
          return "Share of total activations of benchmarks: ";
        }

        @Override
        public String getSimpleName() {
          return "percentage";
        }

      });

  @Override
  public int compare(final AbstractSubAST arg0, final AbstractSubAST arg1) {
    return Double.compare(arg1.score(this.scoreVisitor), arg0.score(this.scoreVisitor));
  }

  String getDescription() {
    return this.scoreVisitor.getScoreDescription();
  }

  String getSimpleName() {
    return this.scoreVisitor.getSimpleName();
  }

  ScoreVisitor getScoreVisitor() {
    return this.scoreVisitor;
  }
}
