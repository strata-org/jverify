package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment Operator ++ applied to a long: the
// expression yields the pre-increment value; the variable becomes value+1.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixIncrement_Long#run")
class PostfixIncrement_Long {
    static void run() {

        long x = 5;
        long y = x++;
        check(x == 6 && y == 5);
    }
}
