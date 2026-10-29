package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a String selector matches string constant
// case labels via String.equals semantics.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_String#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class SwitchExpression_String {
    static void run() {

        String s = "b";
        int r = switch (s) {
            case "a" -> 1;
            case "b" -> 2;
            default -> 0;
        };
        check(r == 2);
    }
}
