package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- try-with-resources closes the resource when the try block
// completes.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.TryWithResources_SingleClose#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TryWithResources_SingleClose {
    static class R implements AutoCloseable {
        static int closed = 0;
        public void close() {
            closed++;
        }
    }

    static void run() {
        R.closed = 0;
        try (R r = new R()) {
            // no-op body
        }
        check(R.closed == 1);
    }
}
