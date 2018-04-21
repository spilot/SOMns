package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.Invokable;
import som.interpreter.nodes.InstantiationNode.ClassInstantiationNode;
import som.interpreter.nodes.InstantiationNodeFactory.ClassInstantiationNodeGen;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;


/**
 * Similar to {@link CachedSlotRead field reads}, access to class objects
 * is realized as a simple message send in Newspeak, and as a result, part of
 * the dispatch chain.
 *
 * <p>
 * The Newspeak semantics defines that class objects are allocated lazily.
 * This is realized here using read/write nodes on a slot of the enclosing
 * object, where the initialized class object is cached.
 */
public final class ClassSlotAccessNode extends CachedSlotRead {
  private final MixinDefinition mixinDef;
  private final SlotDefinition  slotDef;

  @Child protected DirectCallNode         superclassAndMixinResolver;
  @Child protected ClassInstantiationNode instantiation;

  @Child protected CachedSlotRead  read;
  @Child protected CachedSlotWrite write;

  public ClassSlotAccessNode(final MixinDefinition mixinDef, final SlotDefinition slotDef,
      final CachedSlotRead read, final CachedSlotWrite write) {
    super(SlotAccess.CLASS_READ, read.guard, read.nextInCache);

    // TODO: can the slot read be an unwritten read? I'd think so.
    this.read = read;
    this.write = write;
    this.mixinDef = mixinDef;
    this.slotDef = slotDef;
    this.instantiation = ClassInstantiationNodeGen.create(mixinDef);
  }

  @Override
  public SClass read(final SObject rcvr) {
    // here we need to synchronize, because this is actually something that
    // can happen concurrently, and we only want a single instance of the
    // class object
    Object cachedValue = read.read(rcvr);

    assert cachedValue != null;
    if (cachedValue != Nil.nilObject) {
      return (SClass) cachedValue;
    }

    synchronized (rcvr) {
      try {
        // recheck guard under synchronized, don't want to access object if
        // layout might have changed, we are going to slow path in that case
        read.guard.entryMatches(rcvr);
        cachedValue = read.read(rcvr);
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedValue = rcvr.readSlot(slotDef);
      }

      // check whether cache is initialized with class object
      if (cachedValue == Nil.nilObject) {
        return instantiateAndWriteUnsynced(rcvr);
      } else {
        assert cachedValue instanceof SClass;
        return (SClass) cachedValue;
      }
    }
  }

  /**
   * Caller needs to hold lock on {@code this}.
   */
  private SClass instantiateAndWriteUnsynced(final SObject rcvr) {
    SClass classObject = instantiateClassObject(rcvr);

    try {
      // recheck guard under synchronized, don't want to access object if
      // layout might have changed
      //
      // at this point the guard will fail, if it failed for the read guard,
      // but we simply recheck here to avoid impact on fast path
      write.guard.entryMatches(rcvr);
      write.doWrite(rcvr, classObject);
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      rcvr.writeSlot(slotDef, classObject);
    }
    return classObject;
  }

  private void createResolverCallTargets() {
    CompilerAsserts.neverPartOfCompilation();
    Invokable invokable = mixinDef.getSuperclassAndMixinResolutionInvokable();
    superclassAndMixinResolver = insert(Truffle.getRuntime().createDirectCallNode(
        invokable.createCallTarget()));
  }

  private SClass instantiateClassObject(final SObject rcvr) {
    if (superclassAndMixinResolver == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      createResolverCallTargets();
    }

    Object superclassAndMixins = superclassAndMixinResolver.call(new Object[] {rcvr});
    SClass classObject = instantiation.execute(rcvr, superclassAndMixins);
    return classObject;
  }
}
