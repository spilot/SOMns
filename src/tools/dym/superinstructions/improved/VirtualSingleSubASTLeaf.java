package tools.dym.superinstructions.improved;

public class VirtualSingleSubASTLeaf extends SingleSubASTLeaf {

  VirtualSingleSubASTLeaf(final SingleSubAST copyFrom) {
    super(copyFrom);
  }

  @Override
  void computeScore() {
    score = 0;
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
}
