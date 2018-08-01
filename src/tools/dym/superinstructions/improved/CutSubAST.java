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
               .append("\u001B[31m" + " (sub-AST cut)" + "\u001B[0m")
               .append('\n');
    return accumulator;
  }

  @Override
  long computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }
}
