package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- StackOverflowError from unbounded recursion. The JVM raises
// StackOverflowError, not Error, so we catch it directly.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.StackOverflowError_Recursion#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class StackOverflowError_Recursion {
    static int recurse(int n) {
        return recurse(n + 1);
    }

    static void run() {
        boolean caught = false;
        try {
            recurse(0);
        } catch (StackOverflowError e) {
            caught = true;
        }
        check(caught);
    }
}
