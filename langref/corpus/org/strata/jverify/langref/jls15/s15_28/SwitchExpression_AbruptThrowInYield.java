package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: an exception thrown from a case block
// completes the switch expression abruptly and propagates out.
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
