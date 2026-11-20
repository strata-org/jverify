package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NullPointerException from a method call on a null receiver.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.NullPointerException_MethodCall#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NullPointerException_MethodCall {
    static void run() {
        Object o = null;
        boolean caught = false;
        try {
            o.hashCode();
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
