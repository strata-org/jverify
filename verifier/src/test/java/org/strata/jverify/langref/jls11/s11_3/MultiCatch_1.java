package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20 -- multi-catch: a single catch clause can list several
// exception types separated by '|'.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.MultiCatch_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class MultiCatch_1 {
    static void run() {
        boolean caught = false;
        try {
            throw new ArithmeticException("a");
        } catch (ArithmeticException | NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
