package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment Operator ++: the value of the expression is
// the value of the variable BEFORE the new (incremented) value is stored.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixIncrementOperator_2#run")
class PostfixIncrementOperator_2 {
    static void run() {

        int x = 3;
        int y = x++;
        check(x == 4);
    }
}
