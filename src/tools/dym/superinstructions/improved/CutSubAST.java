package tools.dym.superinstructions.improved;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public class CutSubAST extends SingleSubASTLeaf {

  CutSubAST(final SingleSubAST copyFrom) {
    super(copyFrom);
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    accumulator.append(prefix)
               .append(enclosedNodeString)
               .append(": ")
               .append(activationsByType)
               .append("\u001B[31m") // red text
               .append(" (sub-AST cut)")
               .append("\u001B[0m")// default-coloured text
               .append('\n');
    return accumulator;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }
}
