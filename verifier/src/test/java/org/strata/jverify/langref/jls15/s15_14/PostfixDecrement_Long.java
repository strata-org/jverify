package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.3 -- Postfix Decrement Operator -- applied to a long: yields the
// pre-decrement value; the variable becomes value-1.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixDecrement_Long#run")
class PostfixDecrement_Long {
    static void run() {

        long x = 5;
        long y = x--;
        check(x == 4 && y == 5);
    }
}
