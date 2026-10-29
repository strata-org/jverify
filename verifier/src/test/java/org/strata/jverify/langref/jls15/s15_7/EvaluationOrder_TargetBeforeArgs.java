package org.strata.jverify.langref.jls15.s15_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.7.1 -- the target reference of a method invocation is evaluated
// before the argument expressions.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_7.EvaluationOrder_TargetBeforeArgs#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EvaluationOrder_TargetBeforeArgs {
    static class R {
        int m(int x) { return x; }
    }

    static java.util.List<Integer> log = new java.util.ArrayList<>();

    static R newR() {
        log.add(0);
        return new R();
    }

    static int arg() {
        log.add(1);
        return 7;
    }

    static void run() {
        log.clear();
        newR().m(arg());
        check(log.equals(java.util.List.of(0, 1)));
    }
}
