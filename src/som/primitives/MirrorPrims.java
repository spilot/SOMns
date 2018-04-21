package som.primitives;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import som.VM;
import som.compiler.MixinDefinition;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.primitives.reflection.AbstractSymbolDispatchNodeGen;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


public abstract class MirrorPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "objNestedClasses:")
  public abstract static class NestedClassesPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SMutableArray getNestedClasses(final SObjectWithClass rcvr) {
      SClass[] classes = rcvr.getSOMClass().getNestedClasses(rcvr);
      return new SMutableArray(classes, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:respondsTo:")
  public abstract static class RespondsToPrim extends BinaryComplexOperation {
    @Specialization
    @TruffleBoundary
    public final boolean objectResondsTo(final Object rcvr, final SSymbol selector) {
      VM.thisMethodNeedsToBeOptimized(
          "Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      return Types.getClassOf(rcvr).canUnderstand(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "objMethods:")
  public abstract static class MethodsPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SImmutableArray getMethod(final Object rcvr) {
      VM.thisMethodNeedsToBeOptimized(
          "Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      SInvokable[] is = Types.getClassOf(rcvr).getMethods();
      Object[] invokables = Arrays.copyOf(is, is.length, Object[].class);
      return new SImmutableArray(invokables, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:perform:")
  public abstract static class PerformPrim extends BinaryComplexOperation {
    @Child protected AbstractSymbolDispatch dispatch;

    @Override
    @SuppressWarnings("unchecked")
    public PerformPrim initialize(final SourceSection sourceSection) {
      assert sourceSection != null;
      super.initialize(sourceSection);
      dispatch = AbstractSymbolDispatchNodeGen.create(sourceSection);
      return this;
    }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector) {
      return dispatch.executeDispatch(frame, rcvr, selector, null);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:perform:withArguments:")
  public abstract static class PerformWithArgumentsPrim extends TernaryExpressionNode {
    @Child protected AbstractSymbolDispatch dispatch;

    @Override
    @SuppressWarnings("unchecked")
    public PerformWithArgumentsPrim initialize(final SourceSection sourceSection) {
      assert sourceSection != null;
      super.initialize(sourceSection);
      dispatch = AbstractSymbolDispatchNodeGen.create(sourceSection);
      return this;
    }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector, final SArray argsArr) {
      return dispatch.executeDispatch(frame, rcvr, selector, argsArr);
    }

    @Override
    public NodeCost getCost() {
      return dispatch.getCost();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefinition:")
  public abstract static class ClassDefinitionPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getClassDefinition(final SClass rcvr) {
      return rcvr.getMixinDefinition();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefNestedClassDefinitions:")
  public abstract static class NestedClassDefinitionsPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final Object getClassDefinition(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      Object[] nested = def.getNestedMixinDefinitions();
      return new SImmutableArray(nested, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefName:")
  public abstract static class ClassDefNamePrim extends UnaryExpressionNode {
    @Specialization
    public final SSymbol getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getName();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefFilePath:")
  public abstract static class ClassDefFilePathPrim extends UnaryExpressionNode {
    @Specialization
    public final String getFilePath(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getSourceSection().getSource().getPath();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefMethods:")
  public abstract static class ClassDefMethodsPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SImmutableArray getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;

      ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
      for (Dispatchable disp : def.getInstanceDispatchables().getValues()) {
        if (disp instanceof SInvokable) {
          methods.add((SInvokable) disp);
        }
      }
      return new SImmutableArray(methods.toArray(new Object[0]),
          Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDef:hasFactoryMethod:")
  public abstract static class ClassDefHasFactoryMethodPrim extends BinaryComplexOperation {
    @Specialization
    @TruffleBoundary
    public final boolean hasFactoryMethod(final Object mixinHandle,
        final SSymbol selector) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getFactoryMethods().containsKey(selector);
    }
  }
}
