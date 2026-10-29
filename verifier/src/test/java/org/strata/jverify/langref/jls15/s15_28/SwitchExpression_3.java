package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a switch expression yields a value; arrow rules,
// yield from a block rule, multiple case constants, and default are exercised.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
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
