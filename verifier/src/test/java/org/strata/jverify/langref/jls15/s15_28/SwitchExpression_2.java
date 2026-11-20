package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_2#run")
class SwitchExpression_2 {
    static void run() {

        // Arrow-form with a block and a yield statement (JLS 14.21).
        int n = 4;
        int doubled = switch (n) {
            case 0 -> 0;
            default -> {
                int t = n * 2;
                yield t;          // yield provides the value of the block
            }
        };
        check(doubled == 8);
    }
}
