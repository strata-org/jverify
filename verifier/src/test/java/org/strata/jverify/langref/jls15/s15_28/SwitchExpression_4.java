package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_4#run")
class SwitchExpression_4 {
    static void run() {

        // Colon-form switch expression using yield from each group.
        int code = 2;

        // switch expression as a sub-expression participating in arithmetic.
        int total = 10 + switch (code) {
            case 2 -> 5;
            default -> 0;
        };
        check(total == 15);
    }
}
