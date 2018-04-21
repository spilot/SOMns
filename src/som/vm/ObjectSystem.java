package som.vm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.InlinableNodes;
import som.Output;
import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.SourcecodeCompiler;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.actors.SPromise;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors;
import tools.language.StructuralProbe;


public final class ObjectSystem {

  private final EconomicMap<URI, MixinDefinition> loadedModules;

  @CompilationFinal private MixinDefinition platformModule;
  @CompilationFinal private MixinDefinition kernelModule;

  @CompilationFinal private SClass platformClass; // is only set after completion of
                                                  // initialize()

  @CompilationFinal private boolean initialized = false;

  private final SourcecodeCompiler compiler;
  private final StructuralProbe    structuralProbe;

  private final Primitives primitives;

  private final InlinableNodes<SSymbol> inlinableNodes;

  private CompletableFuture<Object> mainThreadCompleted;

  private final VM vm;

  public ObjectSystem(final SourcecodeCompiler compiler,
      final StructuralProbe probe, final VM vm) {
    this.primitives = new Primitives(compiler.getLanguage());
    this.inlinableNodes = new InlinableNodes<>(Symbols.PROVIDER,
        Primitives.getInlinableNodes(), Primitives.getInlinableFactories());
    this.compiler = compiler;
    structuralProbe = probe;
    loadedModules = EconomicMap.create();
    this.vm = vm;
  }

  public void loadKernelAndPlatform(final String platformFilename,
      final String kernelFilename) throws IOException {
    platformModule = loadModule(platformFilename);
    kernelModule = loadModule(kernelFilename);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public Primitives getPrimitives() {
    return primitives;
  }

  public InlinableNodes<SSymbol> getInlinableNodes() {
    return inlinableNodes;
  }

  public SClass getPlatformClass() {
    assert platformClass != null;
    return platformClass;
  }

  public MixinDefinition loadModule(final String filename) throws IOException {
    File file = new File(filename);

    if (!file.exists()) {
      throw new FileNotFoundException(filename);
    }

    if (!file.isFile()) {
      throw new NotAFileException(filename);
    }

    Source source = Source.newBuilder(file).mimeType(SomLanguage.MIME_TYPE).build();
    return loadModule(source);
  }

  public MixinDefinition loadModule(final Source source) throws IOException {
    URI uri = source.getURI();
    if (loadedModules.containsKey(uri)) {
      return loadedModules.get(uri);
    }

    MixinDefinition module;
    try {
      module = compiler.compileModule(source, structuralProbe);
      loadedModules.put(uri, module);
      return module;
    } catch (ProgramDefinitionError e) {
      vm.errorExit(e.toString());
      throw new IOException(e);
    }
  }

  private SObjectWithoutFields constructVmMirror() {
    EconomicMap<SSymbol, Dispatchable> vmMirrorMethods = primitives.takeVmMirrorPrimitives();
    MixinScope scope = new MixinScope(null);

    MixinDefinition vmMirrorDef = new MixinDefinition(
        Symbols.VMMIRROR, null, null, null, null, null, null, null,
        vmMirrorMethods, null,
        null, new MixinDefinitionId(Symbols.VMMIRROR), AccessModifier.PUBLIC, scope, scope,
        true, true, true, null);
    scope.setMixinDefinition(vmMirrorDef, false);

    SClass vmMirrorClass = vmMirrorDef.instantiateClass(Nil.nilObject,
        new SClass[] {Classes.topClass, Classes.valueClass});
    return new SObjectWithoutFields(vmMirrorClass, vmMirrorClass.getInstanceFactory());
  }

  /**
   * Allocate the metaclass class.
   */
  public static SClass newMetaclassClass(final SObject kernel) {
    SClass metaclassClass = new SClass(kernel);
    SClass metaclassClassClass = new SClass(kernel);
    metaclassClass.setClass(metaclassClassClass);

    metaclassClass.initializeClass(Symbols.METACLASS, null);
    metaclassClassClass.initializeClass(Symbols.METACLASS_CLASS, null);

    // Connect the metaclass hierarchy
    metaclassClass.getSOMClass().setClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass newEmptyClassWithItsClass(final String name) {
    SClass clazz = new SClass(KernelObj.kernel);
    SClass clazzClazz = new SClass(KernelObj.kernel);

    initializeClassAndItsClass(name, clazz, clazzClazz);
    return clazz;
  }

  public static void initializeClassAndItsClass(final String name,
      final SClass clazz, final SClass clazzClazz) {
    clazz.initializeClass(Symbols.symbolFor(name), null);

    // Setup the metaclass hierarchy
    clazzClazz.initializeClass(Symbols.symbolFor(name + " class"), Classes.classClass);

    clazz.setClass(clazzClazz);
    clazzClazz.setClass(Classes.metaclassClass);
  }

  public SObjectWithoutFields initialize() {
    ObjectTransitionSafepoint.INSTANCE.register();
    assert platformModule != null && kernelModule != null;

    // these classes need to be defined by the Kernel module
    MixinDefinition topDef = kernelModule.getNestedMixinDefinition("Top");
    MixinDefinition thingDef = kernelModule.getNestedMixinDefinition("Thing");
    thingDef.addSyntheticInitializerWithoutSuperSendOnlyForThingClass();
    MixinDefinition valueDef = kernelModule.getNestedMixinDefinition("Value");
    MixinDefinition transferDef = kernelModule.getNestedMixinDefinition("TransferObject");
    MixinDefinition nilDef = kernelModule.getNestedMixinDefinition("Nil");

    MixinDefinition objectDef = kernelModule.getNestedMixinDefinition("Object");
    MixinDefinition classDef = kernelModule.getNestedMixinDefinition("Class");
    MixinDefinition metaclassDef = kernelModule.getNestedMixinDefinition("Metaclass");

    MixinDefinition arrayReadMixinDef =
        kernelModule.getNestedMixinDefinition("ArrayReadMixin");
    MixinDefinition arrayDef = kernelModule.getNestedMixinDefinition("Array");
    MixinDefinition valueArrayDef = kernelModule.getNestedMixinDefinition("ValueArray");
    MixinDefinition transferArrayDef = kernelModule.getNestedMixinDefinition("TransferArray");
    MixinDefinition symbolDef = kernelModule.getNestedMixinDefinition("Symbol");
    MixinDefinition integerDef = kernelModule.getNestedMixinDefinition("Integer");
    MixinDefinition stringDef = kernelModule.getNestedMixinDefinition("String");
    MixinDefinition doubleDef = kernelModule.getNestedMixinDefinition("Double");

    MixinDefinition booleanDef = kernelModule.getNestedMixinDefinition("Boolean");
    MixinDefinition trueDef = kernelModule.getNestedMixinDefinition("True");
    MixinDefinition falseDef = kernelModule.getNestedMixinDefinition("False");

    MixinDefinition blockDef = kernelModule.getNestedMixinDefinition("Block");

    // some basic assumptions about
    assert topDef.getNumberOfSlots() == 0;
    assert thingDef.getNumberOfSlots() == 0;
    assert objectDef.getNumberOfSlots() == 0;
    assert valueDef.getNumberOfSlots() == 0;
    assert transferDef.getNumberOfSlots() == 0;

    topDef.initializeClass(Classes.topClass, null); // Top doesn't have a super class
    thingDef.initializeClass(Classes.thingClass, Classes.topClass);
    valueDef.initializeClass(Classes.valueClass, Classes.thingClass, true, false, false);
    objectDef.initializeClass(Classes.objectClass, Classes.thingClass);
    classDef.initializeClass(Classes.classClass, Classes.objectClass);
    transferDef.initializeClass(Classes.transferClass, Classes.objectClass, false, true,
        false);

    metaclassDef.initializeClass(Classes.metaclassClass, Classes.classClass);
    nilDef.initializeClass(Classes.nilClass, Classes.valueClass);

    arrayReadMixinDef.initializeClass(Classes.arrayReadMixinClass, Classes.objectClass);
    arrayDef.initializeClass(Classes.arrayClass,
        new SClass[] {Classes.objectClass, Classes.arrayReadMixinClass}, false, false, true);
    valueArrayDef.initializeClass(Classes.valueArrayClass,
        new SClass[] {Classes.valueClass, Classes.arrayReadMixinClass}, false, false, true);
    transferArrayDef.initializeClass(Classes.transferArrayClass,
        new SClass[] {Classes.arrayClass, Classes.transferClass}, false, false, true);
    integerDef.initializeClass(Classes.integerClass, Classes.valueClass);
    stringDef.initializeClass(Classes.stringClass, Classes.valueClass);
    doubleDef.initializeClass(Classes.doubleClass, Classes.valueClass);
    symbolDef.initializeClass(Classes.symbolClass, Classes.stringClass);

    booleanDef.initializeClass(Classes.booleanClass, Classes.valueClass);
    trueDef.initializeClass(Classes.trueClass, Classes.booleanClass);
    falseDef.initializeClass(Classes.falseClass, Classes.booleanClass);

    blockDef.initializeClass(Classes.blockClass, Classes.objectClass);

    Nil.nilObject.setClass(Classes.nilClass);

    // fix up the metaclassClass group
    Classes.topClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.thingClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.valueClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.objectClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.transferClass.getSOMClass()
                         .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.classClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.metaclassClass.getSOMClass()
                          .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.nilClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.arrayReadMixinClass.getSOMClass()
                               .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.arrayClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.valueArrayClass.getSOMClass()
                           .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.transferArrayClass.getSOMClass()
                              .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.integerClass.getSOMClass()
                        .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.stringClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.doubleClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.symbolClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.booleanClass.getSOMClass()
                        .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.trueClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.falseClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.blockClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());

    SClass kernelClass = kernelModule.instantiateClass(Nil.nilObject, Classes.objectClass);
    KernelObj.kernel.setClass(kernelClass);

    // create and initialize the vmMirror object
    SObjectWithoutFields vmMirror = constructVmMirror();
    assert vmMirror.isValue();

    // initialize slots of kernel object
    setSlot(KernelObj.kernel, "vmMirror", vmMirror, kernelModule);
    setSlot(KernelObj.kernel, "ObjectSlot", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueSlot", Classes.valueClass, kernelModule);

    // Initialize the class cache slots
    setSlot(KernelObj.kernel, "Top", Classes.topClass, kernelModule);
    setSlot(KernelObj.kernel, "Thing", Classes.thingClass, kernelModule);
    setSlot(KernelObj.kernel, "Object", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "Value", Classes.valueClass, kernelModule);
    setSlot(KernelObj.kernel, "TransferObject", Classes.transferClass, kernelModule);
    setSlot(KernelObj.kernel, "Class", Classes.classClass, kernelModule);
    setSlot(KernelObj.kernel, "Metaclass", Classes.metaclassClass, kernelModule);
    setSlot(KernelObj.kernel, "Boolean", Classes.booleanClass, kernelModule);
    setSlot(KernelObj.kernel, "True", Classes.trueClass, kernelModule);
    setSlot(KernelObj.kernel, "False", Classes.falseClass, kernelModule);
    setSlot(KernelObj.kernel, "Nil", Classes.nilClass, kernelModule);
    setSlot(KernelObj.kernel, "Integer", Classes.integerClass, kernelModule);
    setSlot(KernelObj.kernel, "Double", Classes.doubleClass, kernelModule);
    setSlot(KernelObj.kernel, "Class", Classes.classClass, kernelModule);
    setSlot(KernelObj.kernel, "String", Classes.stringClass, kernelModule);
    setSlot(KernelObj.kernel, "Symbol", Classes.symbolClass, kernelModule);
    setSlot(KernelObj.kernel, "ArrayReadMixin", Classes.arrayReadMixinClass, kernelModule);
    setSlot(KernelObj.kernel, "Array", Classes.arrayClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueArray", Classes.valueArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "TransferArray", Classes.transferArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "Block", Classes.blockClass, kernelModule);

    initialized = true;

    platformClass = platformModule.instantiateModuleClass();

    ObjectTransitionSafepoint.INSTANCE.unregister();
    return vmMirror;
  }

  private static void setSlot(final SObject obj, final String slotName,
      final Object value, final MixinDefinition classDef) {
    SlotDefinition slot = (SlotDefinition) classDef.getInstanceDispatchables().get(
        Symbols.symbolFor(slotName));
    slot.setValueDuringBootstrap(obj, value);
  }

  private void handlePromiseResult(final SPromise promise) {
    int emptyFJPool = 0;
    while (emptyFJPool < 120) {
      if (promise.isCompleted()) {
        if (vm.isAvoidingExit()) {
          return;
        }

        if (promise.isErroredUnsync()) {
          vm.shutdownAndExit(1);
        } else {
          vm.shutdownAndExit(0);
        }
      }

      if (vm.shouldExit()) {
        if (vm.isAvoidingExit()) {
          return;
        }
        vm.shutdownAndExit(vm.lastExitCode());
      }

      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {}

      // never timeout when debugging
      if (vm.isPoolIdle() && !VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        emptyFJPool++;
      } else {
        emptyFJPool = 0;
      }
    }

    assert !vm.shouldExit();
    TracingActors.ReplayActor.printMissingMessages();
    Output.errorPrintln(
        "VM seems to have exited prematurely. The actor pool has been idle for "
            + emptyFJPool + " checks in a row.");
    vm.shutdownAndExit(1); // just in case it was disable for VM.errorExit
  }

  public void releaseMainThread(final int errorCode) {
    mainThreadCompleted.complete(errorCode);
  }

  @TruffleBoundary
  public void executeApplication(final SObjectWithoutFields vmMirror, final Actor mainActor) {
    mainThreadCompleted = new CompletableFuture<>();

    ObjectTransitionSafepoint.INSTANCE.register();
    Object platform = platformModule.instantiateObject(platformClass, vmMirror);
    ObjectTransitionSafepoint.INSTANCE.unregister();

    SSymbol start = Symbols.symbolFor("start");
    SourceSection source;

    try {
      // might fail if module doesn't have a #start method
      SInvokable disp = (SInvokable) platformModule.getInstanceDispatchables().get(start);
      source = disp.getSourceSection();
    } catch (Exception e) {
      source = SomLanguage.getSyntheticSource("",
          "ObjectSystem.executeApplication").createSection(1);
    }

    DirectMessage msg = new DirectMessage(mainActor, start,
        new Object[] {platform}, mainActor,
        null, EventualSendNode.createOnReceiveCallTargetForVMMain(
            start, 1, source, mainThreadCompleted, compiler.getLanguage()),
        false, false);
    mainActor.sendInitialStartMessage(msg, vm.getActorPool());

    try {
      Object result = mainThreadCompleted.get();

      if (result instanceof Long || result instanceof Integer) {
        int exitCode = (result instanceof Long) ? (int) (long) result : (int) result;
        if (vm.isAvoidingExit()) {
          return;
        } else {
          vm.shutdownAndExit(exitCode);
        }
      } else if (result instanceof SPromise) {
        handlePromiseResult((SPromise) result);
        return;
      } else {
        Output.errorPrintln("The application's #main: method returned a " + result.toString()
            + ", but it needs to return a Promise or Integer as return value.");
        vm.shutdownAndExit(1);
      }
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      vm.shutdownAndExit(1);
    }
  }

  @TruffleBoundary
  public Object execute(final String selector) {
    SInvokable method = (SInvokable) platformClass.getSOMClass().lookupMessage(
        Symbols.symbolFor(selector), AccessModifier.PUBLIC);
    try {
      ObjectTransitionSafepoint.INSTANCE.register();
      return method.invoke(new Object[] {platformClass});
    } finally {
      ObjectTransitionSafepoint.INSTANCE.unregister();
    }
  }
}
