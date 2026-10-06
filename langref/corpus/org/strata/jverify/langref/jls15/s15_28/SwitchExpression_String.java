package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: a String selector matches string constant
// case labels via String.equals semantics.
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
