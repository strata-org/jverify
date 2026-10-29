package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1 -- Throwable supports chaining: the (message, cause) constructor
// stores the cause, retrievable via getCause().
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.ExceptionChaining_GetCause#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ExceptionChaining_GetCause {
    static void run() {
        RuntimeException cause = new RuntimeException("inner");
        RuntimeException outer = new RuntimeException("outer", cause);
        check(outer.getCause() == cause);
    }
}
