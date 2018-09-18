package tools.dym.superinstructions.improved;

import java.util.Map;

import com.oracle.truffle.api.nodes.Node;

import tools.dym.superinstructions.improved.SubASTComparator.ScoreVisitor;


class SingleSubASTLeaf extends AbstractSubASTLeaf {
  SingleSubASTLeaf(final Node enclosedNode,
      final Map<String, Long> activationsByType,
      final long totalBenchmarkActivations) {
    super(enclosedNode, activationsByType, totalBenchmarkActivations);
  }

  SingleSubASTLeaf(final SingleSubAST copyFrom, final Map<String, Long> activationsByType) {
    super(copyFrom, activationsByType);
  }

  @Override
  public boolean congruent(final SingleSubAST o) {
    return o instanceof SingleSubASTLeaf
        && this.enclosedNodeType == ((SingleSubASTLeaf) o).enclosedNodeType;
  }

  @Override
  double computeScore(final ScoreVisitor scoreVisitor) {
    return scoreVisitor.score(this);
  }

  @Override
  StringBuilder toStringRecursive(final StringBuilder accumulator,
      final String prefix) {
    if (this.sourceSection == null) {
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
    } else {
      accumulator.append(prefix)
                 .append(this.enclosedNodeType.getSimpleName())
                 .append('(')
                 .append(this.sourceSection)
                 .append("): ")
                 .append(activationsByType)
                 .append('\n');
    }
    return accumulator;
  }

}
