package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NumberFormatException from parseInt of a non-numeric string.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.NumberFormatException_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class NumberFormatException_1 {
    static void run() {
        boolean caught = false;
        try {
            Integer.parseInt("xyz");
        } catch (NumberFormatException e) {
            caught = true;
        }
        check(caught);
    }
}
