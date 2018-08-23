package tools.dym.superinstructions.improved;

import java.util.ArrayList;
import java.util.Comparator;


class SubASTComparator implements Comparator<AbstractSubAST> {
  static interface ScoreVisitor {
    long score(GroupedSubAST subAST);

    long score(CutSubAST subAST);

    long score(SingleSubASTLeaf subAST);

    long score(SingleSubASTwithChildren subAST);

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
        public long score(final GroupedSubAST subAST) {
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
        public long score(final SingleSubASTwithChildren subAST) {
          long childrenActi = 0;
          final ArrayList<SingleSubAST> worklist = subAST.getChildren();
          while (!worklist.isEmpty()) {
            final SingleSubAST current = worklist.remove(worklist.size() - 1);
            // childrenActi += current.score(this);
            if (!(current instanceof CutSubAST)) {
              childrenActi += current.activationsByType.values().stream().reduce(0L,
                  Long::sum);
            }
            if (current instanceof SingleSubASTwithChildren) {
              ((SingleSubASTwithChildren) current).getChildren().forEach(worklist::add);
            }
          }
          return childrenActi;
        }

        @Override
        public long score(final SingleSubASTLeaf subAST) {
          return subAST.activationsByType.values().stream().reduce(0L, Long::sum);
        }

        @Override
        public long score(final CutSubAST subAST) {
          return 0;
        }

        @Override
        public long score(final GroupedSubAST subAST) {
          return subAST.enclosedNodes.stream()
                                     .mapToLong((singleSubAST) -> singleSubAST.score(this))
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

  @Override
  public int compare(final AbstractSubAST arg0, final AbstractSubAST arg1) {
    return (int) (arg1.score(this.scoreVisitor) - arg0.score(this.scoreVisitor));
  }

  String getDescription() {
    return this.scoreVisitor.getScoreDescription();
  }

  String getSimpleName() {
    return this.scoreVisitor.getSimpleName();
  }
}
