package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NullPointerException from dereferencing a null reference.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.NullPointerException_Deref#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NullPointerException_Deref {
    static void run() {
        String s = null;
        boolean caught = false;
        try {
            int x = s.length();
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
