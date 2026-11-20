package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- multiple resources are closed in reverse of their
// declaration order.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.TryWithResources_ReverseClose#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TryWithResources_ReverseClose {
    static StringBuilder log = new StringBuilder();

    static class R1 implements AutoCloseable {
        public void close() {
            log.append("1");
        }
    }

    static class R2 implements AutoCloseable {
        public void close() {
            log.append("2");
        }
    }

    static void run() {
        log.setLength(0);
        try (R1 r1 = new R1(); R2 r2 = new R2()) {
            // no-op body
        }
        check(log.toString().equals("21"));
    }
}
