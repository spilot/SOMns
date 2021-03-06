package tools.dym.superinstructions.improved;

import java.util.Map;

import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


public class CutSubAST extends AbstractSubASTLeaf {
  SingleSubAST[] children;

  CutSubAST(final Node enclosedNode,
      final Map<String, Long> activationsByType,
      final long totalBenchmarkActivations) {
    super(enclosedNode, activationsByType, totalBenchmarkActivations);
  }

  CutSubAST(final SingleSubAST copyFrom) {
    super(copyFrom);
    this.children = copyFrom.isLeaf() ? null : ((SingleSubASTwithChildren) copyFrom).children;
  }

  CutSubAST(final SingleSubAST copyFrom, final Map<String, Long> activationsByType) {
    super(copyFrom, activationsByType);
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    if (VmSettings.SUPERINSTRUCTIONS_REPORT_VERBOSE) {
      accumulator.append("\u001B[31m"); // red text
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
      if (children != null) {

        for (final SingleSubAST child : children) {
          accumulator.append("\u001B[31m"); // red text
          child.toStringRecursive(accumulator, "\u001B[31m" + prefix + "  ");
        }
      }

      accumulator.append("\u001B[0m");// default-coloured text
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
                 .append("\u001B[31m") // red text
                 .append(" (sub-AST cut)")
                 .append("\u001B[0m")// default-coloured text
                 .append('\n');
    }
    return accumulator;
  }

  @Override
  double computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  public boolean congruent(final SingleSubAST arg) {
    // pruning sites are always congruent
    return arg instanceof CutSubAST;
  }
}
