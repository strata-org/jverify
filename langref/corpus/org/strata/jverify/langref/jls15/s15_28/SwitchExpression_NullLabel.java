package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a case null label matches a null selector
// value without a NullPointerException.
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
