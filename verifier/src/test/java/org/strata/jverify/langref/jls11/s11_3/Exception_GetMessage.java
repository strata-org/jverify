package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1 -- Throwable.getMessage returns the detail message set at
// construction time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.Exception_GetMessage#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Exception_GetMessage {
    static void run() {
        RuntimeException e = new RuntimeException("hello");
        check(e.getMessage().equals("hello"));
    }
}
