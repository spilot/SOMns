package tools.dym.superinstructions.improved;

class VirtualSubAST extends GroupedSubAST {
  VirtualSubAST(final SingleSubAST firstNode) {
    super(firstNode);
  }

  VirtualSubAST(final CompoundSubAST copyFrom) {
    // TODO is this constructor necessary?
    super(null);
    assert false;
  }
}
