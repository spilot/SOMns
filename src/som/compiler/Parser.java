/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.compiler;

import static som.compiler.Symbol.And;
import static som.compiler.Symbol.At;
import static som.compiler.Symbol.BeginComment;
import static som.compiler.Symbol.Char;
import static som.compiler.Symbol.Colon;
import static som.compiler.Symbol.Comma;
import static som.compiler.Symbol.Div;
import static som.compiler.Symbol.EndBlock;
import static som.compiler.Symbol.EndComment;
import static som.compiler.Symbol.EndTerm;
import static som.compiler.Symbol.Equal;
import static som.compiler.Symbol.EventualSend;
import static som.compiler.Symbol.Exit;
import static som.compiler.Symbol.Identifier;
import static som.compiler.Symbol.Keyword;
import static som.compiler.Symbol.KeywordSequence;
import static som.compiler.Symbol.LCurly;
import static som.compiler.Symbol.Less;
import static som.compiler.Symbol.Minus;
import static som.compiler.Symbol.MixinOperator;
import static som.compiler.Symbol.Mod;
import static som.compiler.Symbol.More;
import static som.compiler.Symbol.NONE;
import static som.compiler.Symbol.NewBlock;
import static som.compiler.Symbol.NewTerm;
import static som.compiler.Symbol.Not;
import static som.compiler.Symbol.Numeral;
import static som.compiler.Symbol.OperatorSequence;
import static som.compiler.Symbol.Or;
import static som.compiler.Symbol.Per;
import static som.compiler.Symbol.Period;
import static som.compiler.Symbol.Plus;
import static som.compiler.Symbol.Pound;
import static som.compiler.Symbol.RCurly;
import static som.compiler.Symbol.STString;
import static som.compiler.Symbol.Semicolon;
import static som.compiler.Symbol.SlotMutableAssign;
import static som.compiler.Symbol.Star;
import static som.interpreter.SNodeFactory.createImplicitReceiverSend;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;
import static som.vm.Symbols.symbolFor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.InlinableNodes;
import som.Output;
import som.compiler.Lexer.Peek;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.Variable.Local;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.literals.ArrayLiteralNode;
import som.interpreter.nodes.literals.BigIntegerLiteralNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.interpreter.nodes.literals.BooleanLiteralNode.FalseLiteralNode;
import som.interpreter.nodes.literals.BooleanLiteralNode.TrueLiteralNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.nodes.literals.ObjectLiteralNode;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.interpreter.nodes.literals.SymbolLiteralNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.debugger.Tags;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.CommentTag;
import tools.debugger.Tags.DelimiterClosingTag;
import tools.debugger.Tags.DelimiterOpeningTag;
import tools.debugger.Tags.IdentifierTag;
import tools.debugger.Tags.KeywordTag;
import tools.debugger.Tags.LiteralTag;
import tools.debugger.Tags.LocalVariableTag;
import tools.debugger.Tags.StatementSeparatorTag;
import tools.language.StructuralProbe;


public class Parser {

  protected final Lexer lexer;
  private final Source  source;

  private final SomLanguage language;

  protected Symbol sym;
  private String   text;
  private Symbol   nextSym;
  private String   nextText;

  private SourceSection            lastMethodsSourceSection;
  private final Set<SourceSection> syntaxAnnotations;
  private final StructuralProbe    structuralProbe;

  private final InlinableNodes<SSymbol> inlinableNodes;

  /**
   * TODO: fix AST inlining while parsing locals/slots, and remove the disabling.
   */
  private int parsingSlotDefs;

  private static final Symbol[] singleOpSyms = new Symbol[] {Not, And, Or, Star,
      Div, Mod, Plus, Equal, More, Less, Comma, At, Per, Minus, NONE};

  private static final Symbol[] binaryOpSyms = new Symbol[] {Or, Comma, Minus,
      Equal, Not, And, Or, Star, Div, Mod, Plus, Equal, More, Less, Comma, At,
      Per, NONE};

  private static final Symbol[] keywordSelectorSyms = new Symbol[] {Keyword,
      KeywordSequence};

  private static final Symbol[] literalSyms = new Symbol[] {Pound, STString,
      Numeral, Char};

  private static boolean arrayContains(final Symbol[] arr, final Symbol sym) {
    for (Symbol s : arr) {
      if (s == sym) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "Parser(" + source.getName() + ", " + this.getCoordinate().toString() + ")";
  }

  public static class ParseError extends ProgramDefinitionError {
    private static final long      serialVersionUID = 425390202979033628L;
    private final SourceCoordinate sourceCoordinate;
    private final String           text;
    private final String           rawBuffer;
    private final String           fileName;
    private final Symbol           expected;
    private final Symbol           found;

    ParseError(final String message, final Symbol expected, final Parser parser) {
      super(message);
      if (parser.lexer == null) {
        this.sourceCoordinate = new SourceCoordinate(0, 0, 0, 0);
        this.rawBuffer = "";
      } else {
        this.sourceCoordinate = parser.getCoordinate();
        this.rawBuffer = new String(parser.lexer.getCurrentLine());
      }
      this.text = parser.text;
      this.fileName = parser.source.getName();
      this.expected = expected;
      this.found = parser.sym;
    }

    protected String expectedSymbolAsString() {
      return expected.toString();
    }

    public SourceCoordinate getSourceCoordinate() {
      return sourceCoordinate;
    }

    @Override
    public String getMessage() {
      String msg = super.getMessage();

      String foundStr;
      if (Parser.printableSymbol(found)) {
        foundStr = found + " (" + text + ")";
      } else {
        foundStr = found.toString();
      }
      String expectedStr = expectedSymbolAsString();

      msg = msg.replace("%(expected)s", expectedStr);
      msg = msg.replace("%(found)s", foundStr);

      return msg;
    }

    @Override
    public String toString() {
      String msg = "%(file)s:%(line)d:%(column)d: error: " + super.getMessage();
      String foundStr;
      if (Parser.printableSymbol(found)) {
        foundStr = found + " (" + text + ")";
      } else {
        foundStr = found.toString();
      }
      msg += ": " + rawBuffer;
      String expectedStr = expectedSymbolAsString();

      msg = msg.replace("%(file)s", fileName);
      msg = msg.replace("%(line)d", java.lang.Integer.toString(sourceCoordinate.startLine));
      msg =
          msg.replace("%(column)d", java.lang.Integer.toString(sourceCoordinate.startColumn));
      msg = msg.replace("%(expected)s", expectedStr);
      msg = msg.replace("%(found)s", foundStr);
      return msg;
    }
  }

  public static class ParseErrorWithSymbols extends ParseError {
    private static final long serialVersionUID = 561313162441723955L;
    private final Symbol[]    expectedSymbols;

    ParseErrorWithSymbols(final String message, final Symbol[] expected,
        final Parser parser) {
      super(message, null, parser);
      this.expectedSymbols = expected;
    }

    @Override
    protected String expectedSymbolAsString() {
      StringBuilder sb = new StringBuilder();
      String deliminator = "";

      for (Symbol s : expectedSymbols) {
        sb.append(deliminator);
        sb.append(s);
        deliminator = ", ";
      }
      return sb.toString();
    }
  }

  public Parser(final String content, final long fileSize, final Source source,
      final StructuralProbe structuralProbe, final SomLanguage language) throws ParseError {
    this.source = source;
    this.language = language;

    sym = NONE;
    nextSym = NONE;

    if (fileSize == 0) {
      throw new ParseError("Provided file is empty.", NONE, this);
    }

    lexer = new Lexer(content);

    getSymbolFromLexer();

    this.syntaxAnnotations = new HashSet<>();
    this.structuralProbe = structuralProbe;

    if (language.getVM() != null) {
      this.inlinableNodes = language.getVM().getInlinableNodes();
    } else {
      this.inlinableNodes = null;
    }
  }

  Set<SourceSection> getSyntaxAnnotations() {
    return syntaxAnnotations;
  }

  public SourceCoordinate getCoordinate() {
    return lexer.getStartCoordinate();
  }

  private void compatibilityNewspeakVersionAndFileCategory() throws ParseError {
    if (sym == Identifier && "Newspeak3".equals(text)) {
      expect(Identifier, KeywordTag.class);
      expect(STString, LiteralTag.class);
    }
  }

  public MixinBuilder moduleDeclaration() throws ProgramDefinitionError {
    compatibilityNewspeakVersionAndFileCategory();

    comments();
    return classDeclaration(null, AccessModifier.PUBLIC);
  }

  protected String className() throws ParseError {
    String mixinName = text;
    expect(Identifier, IdentifierTag.class);
    return mixinName;
  }

  private MixinBuilder classDeclaration(final MixinBuilder outerBuilder,
      final AccessModifier accessModifier) throws ProgramDefinitionError {
    expectIdentifier("class", "Found unexpected token %(found)s. " +
        "Tried parsing a class declaration and expected 'class' instead.",
        KeywordTag.class);

    SourceCoordinate coord = getCoordinate();
    String mixinName = className();
    SourceSection nameSS = getSource(coord);

    MixinBuilder mxnBuilder = new MixinBuilder(outerBuilder, accessModifier,
        symbolFor(mixinName), nameSS, structuralProbe, language);

    MethodBuilder primaryFactory = mxnBuilder.getPrimaryFactoryMethodBuilder();
    coord = getCoordinate();

    // Newspeak-spec: this is not strictly sufficient for Newspeak
    // it could also parse a binary selector here, I think
    // but, doesn't seem so useful, so, let's keep it simple
    if (sym == Identifier || sym == Keyword) {
      messagePattern(primaryFactory);
    } else {
      // in the standard case, the primary factory method is #new
      primaryFactory.addArgument(Symbols.SELF, getEmptySource());
      primaryFactory.setSignature(Symbols.NEW);
    }
    mxnBuilder.setupInitializerBasedOnPrimaryFactory(getSource(coord));

    comments();

    expect(Equal, "Unexpected symbol %(found)s."
        + " Tried to parse the class declaration of " + mixinName
        + " and expect '=' before the (optional) inheritance declaration.",
        KeywordTag.class);

    inheritanceListAndOrBody(mxnBuilder);
    return mxnBuilder;
  }

  private void inheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    if (sym == NewTerm) {
      defaultSuperclassAndBody(mxnBuilder);
    } else {
      explicitInheritanceListAndOrBody(mxnBuilder);
    }
  }

  private void defaultSuperclassAndBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    SourceSection source = getEmptySource();
    MethodBuilder def = mxnBuilder.getClassInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getSelfRead(source);
    ExpressionNode superClass = createMessageSend(Symbols.OBJECT,
        new ExpressionNode[] {selfRead}, false, source, null, language);
    mxnBuilder.setSuperClassResolution(superClass);

    mxnBuilder.setSuperclassFactorySend(
        mxnBuilder.createStandardSuperFactorySend(source), true);

    classBody(mxnBuilder);
  }

  private void explicitInheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    SourceCoordinate superAndMixinCoord = getCoordinate();
    inheritanceClause(mxnBuilder);

    final boolean hasMixins = sym == MixinOperator;

    int i = 0;
    while (sym == MixinOperator) {
      i++;
      mixinApplication(mxnBuilder, i);
    }

    if (hasMixins) {
      mxnBuilder.setMixinResolverSource(getSource(superAndMixinCoord));
      SourceCoordinate initCoord = getCoordinate();
      if (accept(Period, StatementSeparatorTag.class)) {
        // TODO: what else do we need to do here?
        mxnBuilder.setInitializerSource(getSource(initCoord));
        mxnBuilder.finalizeInitializer();
        return;
      }
    }
    classBody(mxnBuilder);
  }

  private void mixinApplication(final MixinBuilder mxnBuilder, final int mixinId)
      throws ProgramDefinitionError {
    expect(MixinOperator, KeywordTag.class);
    SourceCoordinate coord = getCoordinate();

    ExpressionNode mixinResolution = inheritancePrefixAndSuperclass(mxnBuilder);
    mxnBuilder.addMixinResolver(mixinResolution);

    AbstractUninitializedMessageSendNode mixinFactorySend;
    SSymbol uniqueInitName;
    if (sym != NewTerm && sym != MixinOperator && sym != Period) {
      mixinFactorySend = (AbstractUninitializedMessageSendNode) messages(
          mxnBuilder.getInitializerMethodBuilder(),
          new ExpressionNode[] {
              mxnBuilder.getInitializerMethodBuilder().getSelfRead(getSource(coord))});

      uniqueInitName = MixinBuilder.getInitializerName(
          mixinFactorySend.getSelector(), mixinId);
      mixinFactorySend = (AbstractUninitializedMessageSendNode) MessageSendNode.adaptSymbol(
          uniqueInitName, mixinFactorySend, language.getVM());
    } else {
      uniqueInitName = MixinBuilder.getInitializerName(Symbols.NEW, mixinId);
      mixinFactorySend =
          (AbstractUninitializedMessageSendNode) createMessageSend(uniqueInitName,
              new ExpressionNode[] {
                  mxnBuilder.getInitializerMethodBuilder().getSelfRead(getSource(coord))},
              false, getSource(coord), null, language);
    }

    mxnBuilder.addMixinFactorySend(mixinFactorySend);
  }

  private void inheritanceClause(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    ExpressionNode superClassResolution = inheritancePrefixAndSuperclass(mxnBuilder);
    mxnBuilder.setSuperClassResolution(superClassResolution);

    if (sym != NewTerm && sym != MixinOperator) {
      // This factory method on the super class is actually called as
      // initializer of the super class after object creation.
      // The Newspeak spec isn't entirely straight forward on that, but it says
      // that it is a runtime error if it is not the primary factory method.
      // Which for me implies that this one is special. And indeed, it is
      // used to create the proper initialize method, on which we rely here.
      ExpressionNode superFactorySend = messages(
          mxnBuilder.getInitializerMethodBuilder(),
          new ExpressionNode[] {
              mxnBuilder.getInitializerMethodBuilder().getSuperReadNode(getEmptySource())});

      SSymbol initializerName = MixinBuilder.getInitializerName(
          ((AbstractUninitializedMessageSendNode) superFactorySend).getSelector());

      // TODO: the false we pass here, should that be conditional on the superFactorSend being
      // a #new send?
      mxnBuilder.setSuperclassFactorySend(
          MessageSendNode.adaptSymbol(
              initializerName,
              (AbstractUninitializedMessageSendNode) superFactorySend,
              language.getVM()),
          false);
    } else {
      mxnBuilder.setSuperclassFactorySend(
          mxnBuilder.createStandardSuperFactorySend(
              getEmptySource()),
          true);
    }
  }

  private ExpressionNode inheritancePrefixAndSuperclass(
      final MixinBuilder mxnBuilder) throws ParseError, MixinDefinitionError {
    MethodBuilder meth = mxnBuilder.getClassInstantiationMethodBuilder();
    SourceCoordinate coord = getCoordinate();

    if (acceptIdentifier("outer", KeywordTag.class)) {
      String outer = identifier();
      ExpressionNode self = meth.getOuterRead(outer, getSource(coord));
      if (sym == Identifier) {
        return unaryMessage(self, false, null);
      } else {
        return self;
      }
    }

    ExpressionNode self;
    if (acceptIdentifier("super", KeywordTag.class)) {
      self = meth.getSuperReadNode(getSource(coord));
    } else if (acceptIdentifier("self", KeywordTag.class)) {
      self = meth.getSelfRead(getSource(coord));
    } else {
      return implicitUnaryMessage(meth, unarySelector(), getSource(coord));
    }
    return unaryMessage(self, false, null);
  }

  private void classBody(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    classHeader(mxnBuilder);
    sideDeclaration(mxnBuilder);
    if (sym == Colon) {
      classSideDecl(mxnBuilder);
    }
  }

  private void classSideDecl(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    mxnBuilder.switchToClassSide();

    expect(Colon, KeywordTag.class);
    expect(NewTerm, null);

    while (sym != EndTerm) {
      comments();
      SourceCoordinate coord = getCoordinate();
      AccessModifier accessModifier = accessModifier();
      methodDeclaration(accessModifier, coord, mxnBuilder);
      comments();
    }

    expect(EndTerm, null);
  }

  private void classHeader(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    expect(NewTerm, null);
    classComment(mxnBuilder);

    SourceCoordinate coord = getCoordinate();
    if (sym == Or) {
      slotDeclarations(mxnBuilder);
    }

    if (sym == OperatorSequence && "||".equals(text)) {
      peekForNextSymbolFromLexer();
      if (nextSym == EndTerm) {
        expect(OperatorSequence, null);

        mxnBuilder.setInitializerSource(getSource(coord));
        expect(EndTerm, null);
        mxnBuilder.finalizeInitializer();
        return;
      } else {
        simSlotDeclarations(mxnBuilder);
      }
    }

    comments();
    if (sym != EndTerm) {
      initExprs(mxnBuilder);
    }

    mxnBuilder.setInitializerSource(getSource(coord));
    expect(EndTerm, null);
    mxnBuilder.finalizeInitializer();
  }

  private void classComment(final MixinBuilder mxnBuilder) throws ParseError {
    mxnBuilder.setComment(comments());
  }

  private String comments() throws ParseError {
    String comment = "";
    while (sym == BeginComment) {
      if (comment.length() > 0) {
        comment += "\n";
      }
      comment += comment();
    }

    return comment;
  }

  private String comment() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    expect(BeginComment, null);

    String comment = "";
    while (sym != EndComment) {
      comment += lexer.getCommentPart();
      getSymbolFromLexer();

      if (sym == BeginComment) {
        comment += "(*" + comments() + "*)";
      }
      if (sym == NONE) {
        throw new ParseError("Comment seems not to be closed", EndComment, this);
      }
    }
    expect(EndComment, null);
    language.getVM().reportSyntaxElement(CommentTag.class, getSource(coord));
    return comment;
  }

  private void slotDeclarations(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    // simultaneous slots clauses (spec 6.3.2)
    expect(Or, DelimiterOpeningTag.class);

    while (sym != Or) {
      slotDefinition(mxnBuilder);
    }

    comments();

    expect(Or, DelimiterClosingTag.class);
  }

  private void simSlotDeclarations(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    // simultaneous slots clauses (spec 6.3.2)
    Output.errorPrintln("Warning: Parsed simSlotDecls, but it isn't supported yet. "
        + lexer.getCurrentLineNumber() + ":" + lexer.getCurrentColumn());
    assert "||".equals(text);
    expect(OperatorSequence, DelimiterOpeningTag.class);

    while (sym != OperatorSequence) {
      slotDefinition(mxnBuilder);
    }

    comments();

    assert "||".equals(text);
    expect(OperatorSequence, DelimiterClosingTag.class);
  }

  private void slotDefinition(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    comments();
    if (sym == Or) {
      return;
    }

    SourceCoordinate coord = getCoordinate();
    AccessModifier acccessModifier = accessModifier();

    comments();

    String slotName = slotDecl();
    boolean immutable;
    ExpressionNode init;

    if (accept(Equal, KeywordTag.class)) {
      immutable = true;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period, StatementSeparatorTag.class);
    } else if (accept(SlotMutableAssign, KeywordTag.class)) {
      immutable = false;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period, StatementSeparatorTag.class);
    } else {
      immutable = false;
      init = null;
    }
    mxnBuilder.addSlot(symbolFor(slotName), acccessModifier, immutable, init,
        getSource(coord));
  }

  private AccessModifier accessModifier() {
    if (sym == Identifier) {
      if (acceptIdentifier("private", KeywordTag.class)) {
        return AccessModifier.PRIVATE;
      }
      if (acceptIdentifier("protected", KeywordTag.class)) {
        return AccessModifier.PROTECTED;
      }
      if (acceptIdentifier("public", KeywordTag.class)) {
        return AccessModifier.PUBLIC;
      }
    }
    return AccessModifier.PROTECTED;
  }

  private String slotDecl() throws ParseError {
    String id = identifier();

    comments();

    new TypeParser(this).parseType();

    comments();

    return id;
  }

  private void initExprs(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    MethodBuilder initializer = mxnBuilder.getInitializerMethodBuilder();
    mxnBuilder.addInitializerExpression(expression(initializer));

    while (accept(Period, StatementSeparatorTag.class)) {
      if (sym != EndTerm) {
        mxnBuilder.addInitializerExpression(expression(initializer));
      }
    }
  }

  private void sideDeclaration(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    expect(NewTerm, DelimiterOpeningTag.class);
    comments();

    SourceCoordinate coord = getCoordinate();
    AccessModifier accessModifier = accessModifier();
    peekForNextSymbolFromLexer();

    while (sym == Identifier && nextSym == Identifier) {
      nestedClassDeclaration(accessModifier, coord, mxnBuilder);
      comments();

      coord = getCoordinate();
      accessModifier = accessModifier();
      peekForNextSymbolFromLexer();
    }

    while (sym != EndTerm) {
      comments();
      methodDeclaration(accessModifier, coord, mxnBuilder);
      comments();

      coord = getCoordinate();
      accessModifier = accessModifier();
    }

    expect(EndTerm, DelimiterClosingTag.class);
  }

  private void nestedClassDeclaration(final AccessModifier accessModifier,
      final SourceCoordinate coord, final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    MixinBuilder nestedCls = classDeclaration(mxnBuilder, accessModifier);
    mxnBuilder.addNestedMixin(nestedCls.assemble(getSource(coord)));
  }

  private boolean symIn(final Symbol[] ss) {
    return arrayContains(ss, sym);
  }

  private boolean acceptIdentifier(final String identifier,
      final Class<? extends Tags> tag) {
    if (sym == Identifier && identifier.equals(text)) {
      accept(Identifier, tag);
      return true;
    }
    return false;
  }

  private boolean accept(final Symbol s, final Class<? extends Tags> tag) {
    if (sym == s) {
      SourceCoordinate coord = tag == null ? null : getCoordinate();
      getSymbolFromLexer();
      if (tag != null) {
        language.getVM().reportSyntaxElement(tag, getSource(coord));
      }
      return true;
    }
    return false;
  }

  private boolean acceptOneOf(final Symbol[] ss, final Class<? extends Tags> tag) {
    if (symIn(ss)) {
      SourceCoordinate coord = tag == null ? null : getCoordinate();
      getSymbolFromLexer();
      if (tag != null) {
        language.getVM().reportSyntaxElement(tag, getSource(coord));
      }
      return true;
    }
    return false;
  }

  private void expectIdentifier(final String identifier, final String msg,
      final Class<? extends Tags> tag) throws ParseError {
    if (acceptIdentifier(identifier, tag)) {
      return;
    }

    throw new ParseError(msg, Identifier, this);
  }

  private void expectIdentifier(final String identifier,
      final Class<? extends Tags> tag) throws ParseError {
    expectIdentifier(identifier, "Unexpected token. Expected '" + identifier +
        "', but found %(found)s", tag);
  }

  private void expect(final Symbol s, final String msg,
      final Class<? extends Tags> tag) throws ParseError {
    if (accept(s, tag)) {
      return;
    }

    throw new ParseError(msg, s, this);
  }

  protected void expect(final Symbol s, final Class<? extends Tags> tag) throws ParseError {
    expect(s, "Unexpected symbol. Expected %(expected)s, but found %(found)s", tag);
  }

  private boolean expectOneOf(final Symbol[] ss, final Class<? extends Tags> tag)
      throws ParseError {
    if (acceptOneOf(ss, tag)) {
      return true;
    }

    throw new ParseErrorWithSymbols("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  SourceSection getEmptySource() {
    SourceCoordinate coord = getCoordinate();
    return source.createSection(coord.charIndex, 0);
  }

  public SourceSection getSource(final SourceCoordinate coord) {
    assert lexer.getNumberOfCharactersRead() - coord.charIndex >= 0;
    SourceSection ss = source.createSection(coord.charIndex,
        Math.max(lexer.getNumberOfNonWhiteCharsRead() - coord.charIndex, 0));
    return ss;
  }

  private void methodDeclaration(final AccessModifier accessModifier,
      final SourceCoordinate coord, final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    MethodBuilder builder = new MethodBuilder(mxnBuilder, structuralProbe);

    comments();

    messagePattern(builder);

    comments();

    expect(Equal,
        "Unexpected symbol %(found)s. Tried to parse method declaration and expect '=' between message pattern, and method body.",
        KeywordTag.class);

    comments();

    ExpressionNode body = methodBlock(builder);
    builder.finalizeMethodScope();
    SInvokable meth = builder.assemble(body, accessModifier, getSource(coord));

    if (structuralProbe != null) {
      structuralProbe.recordNewMethod(meth);
    }
    mxnBuilder.addMethod(meth);
  }

  private void messagePattern(final MethodBuilder builder) throws ParseError {
    builder.addArgument(Symbols.SELF, getEmptySource());
    switch (sym) {
      case Identifier:
        unaryPattern(builder);
        break;
      case Keyword:
        keywordPattern(builder);
        break;
      default:
        binaryPattern(builder);
        break;
    }

    comments();

    new TypeParser(this).parseReturnType();
  }

  protected void unaryPattern(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    builder.setSignature(unarySelector());
    builder.addMethodDefinitionSource(getSource(coord));
  }

  protected void binaryPattern(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    builder.setSignature(binarySelector());
    builder.addMethodDefinitionSource(getSource(coord));

    coord = getCoordinate();
    builder.addArgument(symbolFor(argument()), getSource(coord));
  }

  protected void keywordPattern(final MethodBuilder builder) throws ParseError {
    StringBuilder kw = new StringBuilder();
    do {
      SourceCoordinate coord = getCoordinate();
      kw.append(keyword());
      builder.addMethodDefinitionSource(getSource(coord));

      coord = getCoordinate();
      builder.addArgument(symbolFor(argument()), getSource(coord));
    } while (sym == Keyword);

    builder.setSignature(symbolFor(kw.toString()));
  }

  private ExpressionNode methodBlock(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expect(NewTerm, DelimiterOpeningTag.class);

    ExpressionNode methodBody = blockContents(builder);
    expect(EndTerm, DelimiterClosingTag.class);
    lastMethodsSourceSection = getSource(coord);
    return methodBody;
  }

  protected SSymbol unarySelector() throws ParseError {
    return symbolFor(identifier());
  }

  protected SSymbol binarySelector() throws ParseError {
    String s = text;

    // Checkstyle: stop   @formatter:off
    if (accept(Or, null)) {
    } else if (accept(Comma, null)) {
    } else if (accept(Minus, null)) {
    } else if (accept(Equal, null)) {
    } else if (acceptOneOf(singleOpSyms, null)) {
    } else if (accept(OperatorSequence, null)) {
    } else {
      throw new ParseError("Unexpected symbol. Expected binary operator, "
          + "but found %(found)s", Symbol.NONE, this);
    }
    // Checkstyle: resume   @formatter:on

    return symbolFor(s);
  }

  private String identifier() throws ParseError {
    String s = text;
    expect(Identifier, null);
    return s;
  }

  protected String keyword() throws ParseError {
    String s = text;
    expect(Keyword, null);

    return s;
  }

  protected String setterKeyword() throws ParseError {
    String s = text;
    expect(Symbol.SetterKeyword, null);

    return s;
  }

  private String argument() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String id = identifier();

    comments();

    new TypeParser(this).parseType();

    comments();

    language.getVM().reportSyntaxElement(ArgumentTag.class, getSource(coord));
    return id;
  }

  private ExpressionNode blockContents(final MethodBuilder builder)
      throws ProgramDefinitionError {
    comments();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    if (accept(Or, DelimiterOpeningTag.class)) {
      locals(builder, expressions);
      expect(Or, DelimiterClosingTag.class);
    } else if (sym == OperatorSequence && "||".equals(text)) {
      expect(OperatorSequence, null);
    }
    builder.setVarsOnMethodScope();
    return blockBody(builder, expressions);
  }

  private void locals(final MethodBuilder builder,
      final List<ExpressionNode> expressions) throws ProgramDefinitionError {
    parsingSlotDefs += 1;

    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    // simultaneous slots clauses (spec 6.3.2)
    while (sym != Or) {
      localDefinition(builder, expressions);
    }

    parsingSlotDefs -= 1;
  }

  private void localDefinition(final MethodBuilder builder,
      final List<ExpressionNode> expressions) throws ProgramDefinitionError {
    comments();
    if (sym == Or) {
      return;
    }

    SourceCoordinate coord = getCoordinate();
    String slotName = slotDecl();
    SourceSection source = getSource(coord);

    language.getVM().reportSyntaxElement(LocalVariableTag.class, source);

    boolean immutable;
    ExpressionNode initializer;

    if (accept(Equal, KeywordTag.class)) {
      immutable = true;
      initializer = expression(builder);
      expect(Period, StatementSeparatorTag.class);
    } else if (accept(SlotMutableAssign, KeywordTag.class)) {
      immutable = false;
      initializer = expression(builder);
      expect(Period, StatementSeparatorTag.class);
    } else {
      immutable = false;
      initializer = null;
    }

    Local local = builder.addLocal(symbolFor(slotName), immutable, source);

    if (initializer != null) {
      SourceSection write = getSource(coord);
      ExpressionNode writeNode = local.getWriteNode(0, initializer, write);
      expressions.add(writeNode);
    }
  }

  private ExpressionNode blockBody(final MethodBuilder builder,
      final List<ExpressionNode> expressions) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();

    boolean sawPeriod = true;

    while (true) {
      comments();

      if (accept(Exit, KeywordTag.class)) {
        if (!sawPeriod) {
          expect(Period, null);
        }
        expressions.add(result(builder));

        comments();
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndBlock) {
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        ExpressionNode self = builder.getSelfRead(getEmptySource());
        expressions.add(self);
        return createSequence(expressions, getSource(coord));
      }

      if (!sawPeriod) {
        expect(Period, null);
      }

      expressions.add(expression(builder));
      sawPeriod = accept(Period, StatementSeparatorTag.class);
    }
  }

  private ExpressionNode result(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();

    ExpressionNode exp = expression(builder);
    accept(Period, StatementSeparatorTag.class);

    if (builder.isBlockMethod()) {
      return builder.getNonLocalReturn(exp).initialize(getSource(coord));
    } else {
      return exp;
    }
  }

  private ExpressionNode expression(final MethodBuilder builder)
      throws ProgramDefinitionError {
    comments();
    peekForNextSymbolFromLexer();

    if (sym == Symbol.SetterKeyword) {
      return setterSends(builder);
    } else {
      return evaluation(builder);
    }
  }

  protected ExpressionNode setterSends(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();

    if (sym != Symbol.SetterKeyword) {
      throw new ParseError("Expected setter send, but found instead a %(found)s",
          Symbol.SetterKeyword, this);
    }
    SSymbol setter = symbolFor(setterKeyword());

    peekForNextSymbolFromLexer();

    ExpressionNode value;
    if (sym == Symbol.SetterKeyword) {
      value = setterSends(builder);
    } else {
      value = evaluation(builder);
    }

    return builder.getSetterSend(setter, value, getSource(coord));
  }

  private ExpressionNode evaluation(final MethodBuilder builder)
      throws ProgramDefinitionError {
    comments();

    ExpressionNode exp;
    if (sym == Keyword) {
      exp = keywordMessage(builder, builder.getSelfRead(getEmptySource()), false, false, null);
    } else {
      exp = primary(builder);
    }

    if (symIsMessageSend()) {
      SourceCoordinate coord = getCoordinate();
      ExpressionNode[] lastReceiver = new ExpressionNode[] {exp};

      exp = messages(builder, lastReceiver);

      if (sym == Semicolon) {
        exp = msgCascade(exp, lastReceiver[0], builder, coord);
      }
    }

    comments();

    return exp;
  }

  private ExpressionNode msgCascade(final ExpressionNode nonEmptyMessage,
      final ExpressionNode lastReceiver, final MethodBuilder builder,
      final SourceCoordinate coord) throws ProgramDefinitionError {
    List<ExpressionNode> cascade = new ArrayList<>();
    SourceSection tmpSource = getSource(coord);

    nonEmptyMessage.adoptChildren();

    // first, create a temp variable, to which the result of the receiver is
    // written, and then replace receiver use with reads from temp
    Local tmp = builder.addMessageCascadeTemp(tmpSource);

    // evaluate last receiver, and write value to temp
    cascade.add(builder.getWriteNode(tmp.name, lastReceiver, tmpSource));

    // replace receiver with read from temp
    lastReceiver.replace(builder.getReadNode(tmp.name, tmpSource));

    // add the initial message of the cascade, it is now send to the temp read
    cascade.add(nonEmptyMessage);

    while (sym == Semicolon) {
      expect(Semicolon, KeywordTag.class);

      comments();

      ExpressionNode exp;
      if (sym == Keyword) {
        exp = keywordMessage(builder, builder.getReadNode(tmp.name, tmpSource), false, false,
            null);
      } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
        exp = binaryMessage(builder, builder.getReadNode(tmp.name, tmpSource), false, null);
      } else {
        assert sym == Identifier;
        exp = unaryMessage(builder.getReadNode(tmp.name, tmpSource), false, null);
      }
      cascade.add(exp);
    }

    return createSequence(cascade, getSource(coord));
  }

  private boolean symIsMessageSend() {
    return sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms) || sym == EventualSend;
  }

  private ExpressionNode primary(final MethodBuilder builder) throws ProgramDefinitionError {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        // Parse true, false, and nil as keyword-like constructs
        // (cf. Newspeak spec on reserved words)
        if (acceptIdentifier("true", LiteralTag.class)) {
          comments();
          return new TrueLiteralNode().initialize(getSource(coord));
        }

        if (acceptIdentifier("false", LiteralTag.class)) {
          comments();
          return new FalseLiteralNode().initialize(getSource(coord));
        }

        if (acceptIdentifier("nil", LiteralTag.class)) {
          comments();
          return new NilLiteralNode().initialize(getSource(coord));
        }
        if (acceptIdentifier("objL", LiteralTag.class)) {
          return literalObject(builder);
        }
        if ("outer".equals(text)) {
          return outerSend(builder);
        }

        SSymbol selector = unarySelector();

        comments();

        return implicitUnaryMessage(builder, selector, getSource(coord));
      }
      case NewTerm: {
        return nestedTerm(builder);
      }
      case NewBlock: {
        MethodBuilder bgenc = new MethodBuilder(builder);

        ExpressionNode blockBody = nestedBlock(bgenc);
        bgenc.finalizeMethodScope();

        SInvokable blockMethod = bgenc.assemble(blockBody,
            AccessModifier.BLOCK_METHOD, lastMethodsSourceSection);
        builder.addEmbeddedBlockMethod(blockMethod);

        ExpressionNode result;
        if (bgenc.requiresContext() || VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
          result = new BlockNodeWithContext(blockMethod, bgenc.accessesLocalOfOuterScope());
        } else {
          result = new BlockNode(blockMethod, bgenc.accessesLocalOfOuterScope());
        }
        result.initialize(lastMethodsSourceSection);
        return result;
      }
      case LCurly: {
        return literalArray(builder);
      }
      default: {
        if (symIn(literalSyms)) {
          return literal();
        }
      }
    }
    throw new ParseError("Unexpected symbol. Tried to parse a primary "
        + "expression but found %(found)s", sym, this);
  }

  private ExpressionNode outerSend(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expectIdentifier("outer", KeywordTag.class);
    String outer = identifier();

    comments();

    ExpressionNode operand = builder.getOuterRead(outer, getSource(coord));
    operand = binaryConsecutiveMessages(builder, operand, false, null);

    comments();
    return operand;
  }

  protected ExpressionNode binaryConsecutiveMessages(
      final MethodBuilder builder, ExpressionNode operand,
      boolean eventualSend, SourceSection sendOp) throws ProgramDefinitionError {
    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(builder, operand, eventualSend, sendOp);
      SourceCoordinate coord = getCoordinate();

      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }
    }
    return operand;
  }

  private ExpressionNode messages(final MethodBuilder builder,
      final ExpressionNode[] lastReceiver) throws ProgramDefinitionError {
    ExpressionNode msg = lastReceiver[0];

    SourceCoordinate coord = getCoordinate();
    boolean eventualSend = accept(EventualSend, KeywordTag.class);

    SourceSection sendOp = null;
    if (eventualSend) {
      sendOp = getSource(coord);
    }

    while (sym == Identifier) {
      lastReceiver[0] = msg;
      msg = unaryMessage(msg, eventualSend, sendOp);
      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }
    }

    if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      lastReceiver[0] = msg;
      msg = binaryConsecutiveMessages(builder, msg, eventualSend, sendOp);
      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }
    }

    if (sym == Keyword) {
      lastReceiver[0] = msg;
      msg = keywordMessage(builder, msg, true, eventualSend, sendOp);
    }

    return msg;
  }

  protected ExpressionNode implicitUnaryMessage(final MethodBuilder meth,
      final SSymbol selector, final SourceSection section) {
    return meth.getImplicitReceiverSend(selector, section);
  }

  protected ExpressionNode unaryMessage(final ExpressionNode receiver,
      final boolean eventualSend, final SourceSection sendOperator) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();

    comments();
    return createMessageSend(selector, new ExpressionNode[] {receiver},
        eventualSend, getSource(coord), sendOperator, language);
  }

  private ExpressionNode tryInliningBinaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final SourceCoordinate coord, final SSymbol msg,
      final ExpressionNode operand) throws ProgramDefinitionError {
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    arguments.add(receiver);
    arguments.add(operand);
    SourceSection source = getSource(coord);
    return inlineControlStructureIfPossible(builder, arguments, msg, source);
  }

  protected ExpressionNode binaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean eventualSend,
      final SourceSection sendOperator) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();

    comments();

    ExpressionNode operand = binaryOperand(builder);

    if (!eventualSend) {
      ExpressionNode node = tryInliningBinaryMessage(builder, receiver, coord,
          msg, operand);
      if (node != null) {
        return node;
      }
    }

    comments();

    return createMessageSend(msg, new ExpressionNode[] {receiver, operand},
        eventualSend, getSource(coord), sendOperator, language);
  }

  private ExpressionNode binaryOperand(final MethodBuilder builder)
      throws ProgramDefinitionError {
    ExpressionNode operand = primary(builder);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    // is evaluated as 2 * (3 asString)
    SourceCoordinate coord = getCoordinate();
    boolean evenutalSend = accept(EventualSend, KeywordTag.class);
    while (sym == Identifier) {
      SourceSection sendOp = null;
      if (evenutalSend) {
        sendOp = getSource(coord);
      }
      operand = unaryMessage(operand, evenutalSend, sendOp);
      evenutalSend = accept(EventualSend, KeywordTag.class);
    }

    assert !evenutalSend : "eventualSend should not be true, because that means we steal it from the next operation (think here shouldn't be one, but still...)";
    return operand;
  }

  // TODO: if the eventual send is not consumed by an expression (assignment, etc)
  // we don't need to create a promise

  protected ExpressionNode keywordMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean explicitRcvr,
      final boolean eventualSend, final SourceSection sendOperator)
      throws ProgramDefinitionError {
    assert !(!explicitRcvr && eventualSend);
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    StringBuilder kw = new StringBuilder();

    arguments.add(receiver);

    do {
      kw.append(keyword());
      comments();

      arguments.add(formula(builder));
      comments();
    } while (sym == Keyword);

    String msgStr = kw.toString();
    SSymbol msg = symbolFor(msgStr);

    SourceSection source = getSource(coord);
    if (!eventualSend) {
      ExpressionNode node = inlineControlStructureIfPossible(builder, arguments, msg, source);
      if (node != null) {
        return node;
      }
    }

    ExpressionNode[] args = arguments.toArray(new ExpressionNode[0]);
    if (explicitRcvr) {
      return createMessageSend(
          msg, args, eventualSend, source, sendOperator, language);
    } else {
      assert !eventualSend;
      return createImplicitReceiverSend(msg, args,
          builder.getScope(),
          builder.getMixin().getMixinId(), source, language.getVM());
    }
  }

  protected ExpressionNode inlineControlStructureIfPossible(final MethodBuilder builder,
      final List<ExpressionNode> arguments, final SSymbol msg, final SourceSection source)
      throws ProgramDefinitionError {
    if (parsingSlotDefs > 0) {
      return null;
    }

    return inlinableNodes.inline(msg, arguments, builder, source);
  }

  private ExpressionNode formula(final MethodBuilder builder)
      throws ProgramDefinitionError {
    ExpressionNode operand = binaryOperand(builder);
    SourceCoordinate coord = getCoordinate();
    boolean evenutalSend = accept(EventualSend, KeywordTag.class);
    SourceSection sendOp = null;
    if (evenutalSend) {
      sendOp = getSource(coord);
    }

    operand = binaryConsecutiveMessages(builder, operand, evenutalSend, sendOp);
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodBuilder builder)
      throws ProgramDefinitionError {
    expect(NewTerm, DelimiterOpeningTag.class);
    ExpressionNode exp = expression(builder);
    expect(EndTerm, DelimiterClosingTag.class);

    comments();

    return exp;
  }

  private LiteralNode literal() throws ParseError {
    switch (sym) {
      case Pound:
        return literalSymbol();
      case STString:
        return literalString();
      case Char:
        return literalChar();
      default:
        return literalNumber();
    }
  }

  private LiteralNode literalNumber() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    NumeralParser parser = lexer.getNumeralParser();
    expect(Numeral, null);
    SourceSection source = getSource(coord);

    if (parser.isInteger()) {
      return literalInteger(parser, source);
    } else {
      return literalDouble(parser, source);
    }

  }

  private LiteralNode literalInteger(final NumeralParser parser,
      final SourceSection source) throws ParseError {
    try {
      Number n = parser.getInteger();
      if (n instanceof Long) {
        return new IntegerLiteralNode((Long) n).initialize(source);
      } else {
        return new BigIntegerLiteralNode((BigInteger) n).initialize(source);
      }
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse integer. Expected a number but " +
          "got '" + text + "'", NONE, this);
    }
  }

  private LiteralNode literalDouble(final NumeralParser parser,
      final SourceSection source) throws ParseError {
    try {
      return new DoubleLiteralNode(parser.getDouble()).initialize(source);
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse double. Expected a number but " +
          "got '" + text + "'", NONE, this);
    }
  }

  private LiteralNode literalSymbol() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    SSymbol symb;
    expect(Pound, null);
    if (sym == STString) {
      String s = string();
      symb = symbolFor(s);
    } else {
      symb = selector();
    }

    return new SymbolLiteralNode(symb).initialize(getSource(coord));
  }

  private LiteralNode literalString() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    return new StringLiteralNode(s).initialize(getSource(coord));
  }

  private LiteralNode literalChar() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String s = character();

    return new StringLiteralNode(s).initialize(getSource(coord));
  }

  private LiteralNode literalArray(final MethodBuilder builder) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    expect(LCurly, DelimiterOpeningTag.class);

    boolean needsSeparator = false;
    while (true) {
      comments();
      if (sym == RCurly) {
        expect(RCurly, DelimiterClosingTag.class);
        return ArrayLiteralNode.create(expressions.toArray(new ExpressionNode[0]),
            getSource(coord));
      }
      if (needsSeparator) {
        expect(Period,
            "Could not parse statement. Expected a '.' but got '" + text + "'",
            StatementSeparatorTag.class);
      }
      expressions.add(expression(builder));
      needsSeparator = !accept(Period, StatementSeparatorTag.class);
    }
  }

  /**
   * This method builds the class for that supports an object literal node. The class
   * is anonymous - it is not referenced by any enclosing classes - and is named with
   * the following pattern <code>objL@line@column</code>.
   *
   * <p>
   * The class implements only the default factory (named <code>objL@line@column#new</code>),
   * which is called later when the object literal node is invoked
   * (@see {@link ObjectLiteralNode#executeGeneric}).
   *
   * <p>
   * The class declaration for the object literal is parsed in the same fashion as
   * regular Newspeak classes (@see #inheritanceListAndOrBody).
   *
   *
   * <p>
   * <strong>TODO</strong>: The current implementation is not compliant with Newspeak's
   * specification,
   * which states that an object literal features:
   *
   * <ol>
   * <li>an identifier (optional)
   * a keyword message for the primary factory (optional)
   * <li>a class declaration.
   * </ol>
   *
   * <p>
   * Therefore <code>()()</code>, <code>name ()()</code>, and
   * <code>name new: var ()()</code> are all permitted.
   * Realizing this syntax requires more advanced look ahead than what is currently
   * provided by the lexer.
   */
  private ExpressionNode literalObject(final MethodBuilder builder)
      throws ProgramDefinitionError {
    // Generate the class's signature
    SourceSection source = getSource(getCoordinate());
    SSymbol signature =
        symbolFor("objL@" + lexer.getCurrentLineNumber() + "@" + lexer.getCurrentColumn());
    MixinBuilder classBuilder = new MixinBuilder(builder,
        AccessModifier.PUBLIC, signature, source, structuralProbe, language);

    // Setup the builder and "new" factory for the implicit class
    MethodBuilder primaryFactory = classBuilder.getPrimaryFactoryMethodBuilder();
    primaryFactory.addArgument(Symbols.SELF, getEmptySource());
    primaryFactory.setSignature(Symbols.NEW);
    classBuilder.setupInitializerBasedOnPrimaryFactory(source);

    // Parse the object literal declaration
    inheritanceListAndOrBody(classBuilder);

    // Create the object literal node
    MixinDefinition mixinDef = classBuilder.assemble(source);
    ExpressionNode outerRead = builder.getSelfRead(source);
    ExpressionNode newMessage = createMessageSend(symbolFor("new"),
        new ExpressionNode[] {builder.getSelfRead(source)},
        false, source, source, language);
    return new ObjectLiteralNode(mixinDef, outerRead, newMessage).initialize(source);
  }

  protected SSymbol selector() throws ParseError {
    if (sym == OperatorSequence || symIn(singleOpSyms)) {
      return binarySelector();
    } else if (sym == Keyword || sym == KeywordSequence) {
      return keywordSelector();
    } else {
      return unarySelector();
    }
  }

  private SSymbol keywordSelector() throws ParseError {
    String s = text;
    expectOneOf(keywordSelectorSyms, null);
    SSymbol symb = symbolFor(s);
    return symb;
  }

  private String string() throws ParseError {
    String s = text;
    expect(STString, null);
    return s;
  }

  private String character() throws ParseError {
    String s = text;
    expect(Char, null);
    return s;
  }

  private String stripColons(final String str) {
    return str.replace(":", "");
  }

  private ExpressionNode nestedBlock(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expect(NewBlock, DelimiterOpeningTag.class);

    builder.addArgument(Symbols.BLOCK_SELF, getEmptySource());

    if (sym == Colon) {
      blockPattern(builder);
    }

    String outerMethodName = stripColons(builder.getOuter().getName());

    // generate Block signature
    String blockSig = "λ" + outerMethodName + "@" + coord.startLine + "@" + coord.startColumn;
    int argSize = builder.getNumberOfArguments();
    for (int i = 1; i < argSize; i++) {
      blockSig += ":";
    }

    builder.setSignature(symbolFor(blockSig));

    ExpressionNode expressions = blockContents(builder);

    expect(EndBlock, DelimiterClosingTag.class);
    lastMethodsSourceSection = getSource(coord);

    return expressions;
  }

  private void blockPattern(final MethodBuilder builder) throws ParseError {
    blockArguments(builder);
    expect(Or, KeywordTag.class);
  }

  private void blockArguments(final MethodBuilder builder) throws ParseError {
    do {
      expect(Colon, KeywordTag.class);
      SourceCoordinate coord = getCoordinate();
      builder.addArgument(symbolFor(argument()), getSource(coord));
    } while (sym == Colon);
  }

  private void getSymbolFromLexer() {
    sym = lexer.getSym();
    text = lexer.getText();
  }

  private void peekForNextSymbolFromLexer() {
    Peek peek = lexer.peek();
    nextSym = peek.nextSym;
    nextText = peek.nextText;
  }

  private static boolean printableSymbol(final Symbol sym) {
    return sym == Numeral || sym.compareTo(STString) >= 0;
  }
}
