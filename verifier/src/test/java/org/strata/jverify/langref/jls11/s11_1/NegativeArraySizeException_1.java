package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NegativeArraySizeException when an array is created with a
// negative size.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.NegativeArraySizeException_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class NegativeArraySizeException_1 {
    static void run() {
        int n = -1;
        boolean caught = false;
        try {
            int[] a = new int[n];
        } catch (NegativeArraySizeException e) {
            caught = true;
        }
        check(caught);
    }
}
