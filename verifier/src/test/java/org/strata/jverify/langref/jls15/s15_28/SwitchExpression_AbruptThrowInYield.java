package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: an exception thrown from a case block
// completes the switch expression abruptly and propagates out.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_AbruptThrowInYield#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchExpression_AbruptThrowInYield {
    static void run() {

        Object o = 5;
        boolean caught = false;
        try {
            int r = switch (o) {
                case Integer i -> { throw new RuntimeException("x"); }
                default -> 0;
            };
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}
