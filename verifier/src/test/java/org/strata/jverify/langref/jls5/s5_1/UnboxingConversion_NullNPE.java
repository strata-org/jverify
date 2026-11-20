package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: unboxing a null wrapper reference throws NullPointerException.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.UnboxingConversion_NullNPE#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class UnboxingConversion_NullNPE {
    static void run() {

        Integer x = null;
        boolean caught = false;
        try {
            int i = x;
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
