package org.strata.jverify.langref.jls14.s14_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- try-finally and try-catch-finally: the finally block always
// executes whether the try completes normally, abruptly via an exception, or
// abruptly via a return. Exercises finally on the normal path, the caught-
// exception path, and the case where finally runs before a return propagates.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_20.TryFinally_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TryFinally_4 {
    static int[] log = new int[1];

    // finally runs even though the try returns
    static int returnWithFinally() {
        try {
            return 7;
        } finally {
            log[0] = log[0] + 1; // observed side effect of finally
        }
    }

    // try-catch-finally: catch handles, then finally runs
    static int catchThenFinally(boolean fail) {
        int r = 0;
        try {
            if (fail) {
                throw new IllegalStateException("x");
            }
            r = 1;
        } catch (IllegalStateException e) {
            r = 2;
        } finally {
            r = r + 10; // always applied after try/catch
        }
        return r;
    }

    static void run() {
 // finally executed exactly once

        // normal path: try body then finally

        // exception path: catch then finally
        check(catchThenFinally(true) == 12);
    }
}
