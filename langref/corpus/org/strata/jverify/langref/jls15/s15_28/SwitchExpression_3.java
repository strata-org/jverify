package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
class SwitchExpression_3 {
    static void run() {

        // Colon-form switch expression using yield from each group.
        int code = 2;
        String label = switch (code) {
            case 1: yield "one";
            case 2: yield "two";
            default: yield "other";
        };
        check(label.equals("two"));
    }
}
