package org.strata.jverify.langref.jls15.s15_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- in a compound assignment, the left-hand side variable
// (including any index expression) is evaluated exactly once.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_7.EvaluationOrder_LHSOnceCompound#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EvaluationOrder_LHSOnceCompound {
    static int calls = 0;

    static int idx() {
        calls++;
        return 0;
    }

    static void run() {
        calls = 0;
        int[] a = {10};
        a[idx()] += 5;
        check(calls == 1 && a[0] == 15);
    }
}
