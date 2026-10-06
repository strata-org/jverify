package org.strata.jverify.langref.jls15.s15_28;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 -- switch Expressions: type patterns in case labels bind a variable
// of the matched type for use in the case's expression.
class SwitchExpression_TypePattern {
    static void run() {

        Object o = 42;
        int r = switch (o) {
            case Integer i -> i;
            case String s -> s.length();
            default -> -1;
        };
        check(r == 42);
    }
}
