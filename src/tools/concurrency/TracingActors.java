package tools.concurrency;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.Output;
import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.SReplayPromise;
import som.vm.VmSettings;
import tools.concurrency.TraceParser.MessageRecord;
import tools.debugger.WebDebugger;


public class TracingActors {
  public static class TracingActor extends Actor {
    // TODO: fix this code so that actorId can be final again... (adapt constructor of
    // ReplayActor)
    protected long actorId;
    private int    traceBufferId;

    /**
     * Flag that indicates if a step-to-next-turn action has been made in the previous message.
     */
    protected boolean stepToNextTurn;

    public TracingActor(final VM vm) {
      super(vm);
      this.actorId = TracingActivityThread.newEntityId();
    }

    @Override
    public int getNextTraceBufferId() {
      int result = traceBufferId;
      traceBufferId += 1;
      return result;
    }

    @Override
    public final long getId() {
      return actorId;
    }

    public boolean isStepToNextTurn() {
      return stepToNextTurn;
    }

    @Override
    public void setStepToNextTurn(final boolean stepToNextTurn) {
      this.stepToNextTurn = stepToNextTurn;
    }

    public static void handleBreakpointsAndStepping(final EventualMessage msg,
        final WebDebugger dbg, final Actor actor) {
      if (!VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        return;
      }

      if (msg.getHaltOnReceive() || ((TracingActor) actor).isStepToNextTurn()) {
        dbg.prepareSteppingUntilNextRootNode();
        if (((TracingActor) actor).isStepToNextTurn()) { // reset flag
          actor.setStepToNextTurn(false);
        }
      }

      // check if a step-return-from-turn-to-promise-resolution has been triggered
      if (msg.getHaltOnPromiseMessageResolution()) {
        dbg.prepareSteppingUntilNextRootNode();
      }
    }
  }

  public static final class ReplayActor extends TracingActor {
    protected int                              children;
    protected final Queue<MessageRecord>       expectedMessages;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    private static List<ReplayActor>           actorList;

    static {
      if (VmSettings.DEBUG_MODE) {
        actorList = new ArrayList<>();
      }
    }

    @TruffleBoundary
    public ReplayActor(final VM vm) {
      super(vm);
      if (Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        ReplayActor parent = (ReplayActor) t.currentMessage.getTarget();
        long parentId = parent.getId();
        int childNo = parent.addChild();

        actorId = TraceParser.getReplayId(parentId, childNo);
        expectedMessages = TraceParser.getExpectedMessages(actorId);

      } else {
        expectedMessages = TraceParser.getExpectedMessages(0L);
      }

      if (VmSettings.DEBUG_MODE) {
        synchronized (actorList) {
          actorList.add(this);
        }
      }
    }

    @Override
    protected ExecAllMessages createExecutor(final VM vm) {
      return new ExecAllMessagesReplay(this, vm);
    }

    @Override
    @TruffleBoundary
    public synchronized void send(final EventualMessage msg, final ForkJoinPool actorPool) {
      assert msg.getTarget() == this;

      if (firstMessage == null) {
        firstMessage = msg;
      } else {
        appendToMailbox(msg);
      }

      // actor remains dormant until the expected message arrives
      if ((!this.isExecuting) && this.replayCanProcess(msg)) {
        isExecuting = true;
        execute(actorPool);
      }
    }

    /**
     * Prints a list of expected Messages and remaining mailbox content.
     *
     * @return true if there are actors expecting messages, false otherwise.
     */
    public static boolean printMissingMessages() {
      if (!(VmSettings.REPLAY && VmSettings.DEBUG_MODE)) {
        return false;
      }

      boolean result = false;
      for (ReplayActor a : actorList) {
        ReplayActor ra = a;
        if (ra.expectedMessages != null && ra.expectedMessages.peek() != null) {
          result = true; // program did not execute all messages
          if (ra.expectedMessages.peek() instanceof TraceParser.PromiseMessageRecord) {
            Output.println(a.getName() + " [" + ra.getId() + "] expecting PromiseMessage from "
                + ra.expectedMessages.peek().sender + " PID "
                + ((TraceParser.PromiseMessageRecord) ra.expectedMessages.peek()).pId);
          } else {
            Output.println(a.getName() + " [" + ra.getId() + "] expecting Messagefrom "
                + ra.expectedMessages.peek().sender);
          }

          if (a.firstMessage != null) {
            printMsg(a.firstMessage);
            if (a.mailboxExtension != null) {
              for (EventualMessage em : a.mailboxExtension) {
                printMsg(em);
              }
            }
          }

          for (EventualMessage em : a.leftovers) {
            printMsg(em);
          }
        } else if (a.firstMessage != null || a.mailboxExtension != null) {

          int n = a.firstMessage != null ? 1 : 0;
          n += a.mailboxExtension != null ? a.mailboxExtension.size() : 0;

          Output.println(
              a.getName() + " [" + a.getId() + "] has " + n + " unexpected messages");
        }
      }
      return result;
    }

    private static void printMsg(final EventualMessage msg) {
      if (msg instanceof PromiseMessage) {
        Output.println("\t" + "PromiseMessage " + msg.getMessageId() + " " + msg.getSelector()
            + " from " + msg.getSender().getId() + " PID "
            + ((SReplayPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor());
      } else {
        Output.println(
            "\t" + "Message" + msg.getSelector() + " from " + msg.getSender().getId());
      }
    }

    protected boolean replayCanProcess(final EventualMessage msg) {
      if (!VmSettings.REPLAY) {
        return true;
      }

      assert expectedMessages != null;

      if (expectedMessages.size() == 0) {
        // actor no longer executes messages
        return false;
      }

      MessageRecord other = expectedMessages.peek();

      // handle promise messages
      if (other instanceof TraceParser.PromiseMessageRecord) {
        if (msg instanceof PromiseMessage) {
          if (((SReplayPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor() != ((TraceParser.PromiseMessageRecord) other).pId) {
            return false;
          }
        } else {
          return false;
        }
      }

      return msg.getSender().getId() == other.sender;
    }

    protected int addChild() {
      return children++;
    }

    private static void removeFirstExpectedMessage(final ReplayActor a) {
      MessageRecord first = a.expectedMessages.peek();
      MessageRecord removed = a.expectedMessages.remove();
      assert first == removed;
    }

    private static class ExecAllMessagesReplay extends ExecAllMessages {
      ExecAllMessagesReplay(final Actor actor, final VM vm) {
        super(actor, vm);
      }

      private Queue<EventualMessage> determineNextMessages(
          final List<EventualMessage> postponedMsgs) {
        final ReplayActor a = (ReplayActor) actor;
        int numReceivedMsgs = 1 + (mailboxExtension == null ? 0 : mailboxExtension.size());
        numReceivedMsgs += postponedMsgs.size();

        Queue<EventualMessage> todo = new LinkedList<>();

        if (a.replayCanProcess(firstMessage)) {
          todo.add(firstMessage);
          removeFirstExpectedMessage(a);
        } else {
          postponedMsgs.add(firstMessage);
        }

        if (mailboxExtension != null) {
          for (EventualMessage msg : mailboxExtension) {
            postponedMsgs.add(msg);
          }
        }

        boolean foundNextMessage = true;
        while (foundNextMessage) {
          foundNextMessage = false;
          for (EventualMessage msg : postponedMsgs) {
            if (a.replayCanProcess(msg)) {
              todo.add(msg);
              removeFirstExpectedMessage(a);
              postponedMsgs.remove(msg);
              foundNextMessage = true;
              break;
            }
          }
        }

        assert todo.size()
            + postponedMsgs.size() == numReceivedMsgs : "We shouldn't lose any messages here.";
        return todo;
      }

      @Override
      protected void processCurrentMessages(final ActorProcessingThread currentThread,
          final WebDebugger dbg) {
        assert actor instanceof ReplayActor;
        assert size > 0;

        final ReplayActor a = (ReplayActor) actor;
        Queue<EventualMessage> todo = determineNextMessages(a.leftovers);

        for (EventualMessage msg : todo) {
          currentThread.currentMessage = msg;
          handleBreakpointsAndStepping(firstMessage, dbg, a);
          msg.execute();
        }

        currentThread.createdMessages += todo.size();
      }
    }
  }
}
