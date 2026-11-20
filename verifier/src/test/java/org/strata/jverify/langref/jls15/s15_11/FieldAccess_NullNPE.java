package org.strata.jverify.langref.jls15.s15_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.11 -- Field Access: accessing a non-static field through a null
// reference throws NullPointerException at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_11.FieldAccess_NullNPE#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FieldAccess_NullNPE {
    static class C {
        int v = 5;
    }

    static void run() {

        C c = null;
        boolean caught = false;
        try {
            int v = c.v;
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
