package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- UnsupportedOperationException from mutating an immutable
// collection returned by List.of.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.UnsupportedOperationException_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class UnsupportedOperationException_1 {
    static void run() {
        boolean caught = false;
        try {
            java.util.List.of(1).add(2);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        check(caught);
    }
}
