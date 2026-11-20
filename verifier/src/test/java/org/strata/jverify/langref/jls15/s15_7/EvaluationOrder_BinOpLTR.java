package org.strata.jverify.langref.jls15.s15_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.7.1 -- the left-hand operand of a binary operator is fully
// evaluated before any part of the right-hand operand.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_7.EvaluationOrder_BinOpLTR#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EvaluationOrder_BinOpLTR {
    static java.util.List<Integer> log = new java.util.ArrayList<>();

    static int f() {
        log.add(0);
        return 1;
    }

    static int g() {
        log.add(1);
        return 2;
    }

    static void run() {
        log.clear();
        int r = f() + g();
        check(r == 3 && log.equals(java.util.List.of(0, 1)));
    }
}
