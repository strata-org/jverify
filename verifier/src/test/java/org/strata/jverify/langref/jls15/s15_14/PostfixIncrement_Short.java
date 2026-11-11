package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a short: narrowing conversion
// back to short stores the incremented value; the expression yields the
// pre-increment short value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixIncrement_Short#run")
class PostfixIncrement_Short {
    static void run() {

        short x = 5;
        short y = x++;
        check(x == 6 && y == 5);
    }
}
