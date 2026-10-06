package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
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
