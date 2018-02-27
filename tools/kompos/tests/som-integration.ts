import { expect } from "chai";
import { spawn } from "child_process";
import {
  SOM, HandleStoppedAndGetStackTrace, TestConnection, execSom,
  expectStack
} from "./test-setup";


describe("Command-line Behavior", function() {
  it("should show help", done => {
    let sawOutput = false;
    const somProc = spawn(SOM, ["-h"]);
    somProc.stdout.on("data", (_data) => { sawOutput = true; });
    somProc.on("exit", (code) => {
      expect(sawOutput).to.be.true;
      expect(code).to.equal(0);
      done();
    });
  });

});

describe("Stack trace output", () => {
  it("should be correct for #doesNotUnderstand", () => {
    const result = execSom(["dnu"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.ns::\n\
\tBlock>>#on:do:                               Kernel.ns::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#λstart@@                       Platform.ns::\n\
\tPingPong>>#main:                             pingpong.ns::\n\
\tPingPong>>#testDNU                           pingpong.ns::\n\
");
  });

  it("should be correct for `system printStackTrace`", () => {
    const result = execSom(["stack"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.ns::\n\
\tBlock>>#on:do:                               Kernel.ns::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#λstart@@                       Platform.ns::\n\
\tPingPong>>#main:                             pingpong.ns::\n\
\tPingPong>>#testPrintStackTrace               pingpong.ns::\n");
  });
});

describe("Language Debugger Integration", function() {
  let conn: TestConnection;
  let ctrl: HandleStoppedAndGetStackTrace;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  describe("execute `1 halt` and get suspended event", () => {
    before("Start SOMns and Connect", () => {
      conn = new TestConnection(["halt"]);
      ctrl = new HandleStoppedAndGetStackTrace([], conn, conn.fullyConnected);
    });

    after(closeConnectionAfterSuite);

    it("should halt on expected source section", () => {
      return ctrl.stackPs[0].then(msg => {
        expectStack(msg.stackFrames, 6, "PingPong>>#testHalt", 106);
      });
    });
  });
});
