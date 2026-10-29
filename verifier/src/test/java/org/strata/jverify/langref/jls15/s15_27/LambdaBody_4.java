package org.strata.jverify.langref.jls15.s15_27;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.2 -- Lambda Body: exercises both lambda body forms, an expression
// body and a block body (with statements, local variables and a return), plus a
// void-returning block body and capture of an effectively final local variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_27.LambdaBody_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LambdaBody_4 {
    interface IntFn { int apply(int a); }
    interface Action { void perform(); }

    static int sink = 0;

    static void run() {

        // Void-returning block body (statement expression as body).
        Action a = () -> { sink = 7; };
        a.perform();
        check(sink == 7);
    }
}
