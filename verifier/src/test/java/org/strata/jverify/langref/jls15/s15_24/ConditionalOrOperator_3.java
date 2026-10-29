package org.strata.jverify.langref.jls15.s15_24;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.24 -- Conditional-Or Operator ||: boolean result, truth table, and
// short-circuit evaluation (right operand not evaluated when left is true).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_24.ConditionalOrOperator_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ConditionalOrOperator_3 {
    static int sideEffect = 0;

    static boolean markAndReturn(boolean v) {
        sideEffect = sideEffect + 1;
        return v;
    }

    static void run() {

        boolean ft = false || true;
        check(ft);
    }
}
