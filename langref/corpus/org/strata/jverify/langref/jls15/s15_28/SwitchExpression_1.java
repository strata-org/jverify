package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
class SwitchExpression_1 {
    static void run() {

        // Arrow-form switch expression with expression results and a default.
        int day = 3;
        int kind = switch (day) {
            case 1, 7 -> 0;          // multiple case constants in one label
            case 2, 3, 4, 5, 6 -> 1; // arrow with single expression
            default -> -1;
        };
        check(kind == 1);
    }
}
