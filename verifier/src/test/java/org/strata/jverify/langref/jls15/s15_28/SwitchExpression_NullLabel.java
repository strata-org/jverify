package org.strata.jverify.langref.jls15.s15_28;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a case null label matches a null selector
// value without a NullPointerException.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_28.SwitchExpression_NullLabel#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchExpression_NullLabel {
    static void run() {

        String s = null;
        int r = switch (s) {
            case null -> 0;
            case "x" -> 1;
            default -> -1;
        };
        check(r == 0);
    }
}
