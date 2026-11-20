package org.strata.jverify.langref.jls15.s15_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.7.4 -- Actual argument lists of a method invocation are evaluated
// left-to-right.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_7.EvaluationOrder_ArgsLTR#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EvaluationOrder_ArgsLTR {
    static int[] log = new int[10];
    static int idx = 0;

    static int f(int v) {
        log[idx++] = v;
        return v;
    }

    static int add(int a, int b) {
        return a + b;
    }

    static void run() {
        idx = 0;
        int r = add(f(1), f(2));
        check(r == 3 && idx == 2 && log[0] == 1 && log[1] == 2);
    }
}
