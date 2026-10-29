package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.3 -- Run-Time Handling of an Exception: a thrown exception causes a
// transfer of control to the innermost dynamically-enclosing catch clause whose
// catchable type matches the exception's class.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.ThrowAndCatch#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ThrowAndCatch {
    static void run() {
        int observed = 0;
        try {
            throw new RuntimeException("boom");
        } catch (RuntimeException e) {
            observed = 1;
        }
        check(observed == 1);
    }
}
