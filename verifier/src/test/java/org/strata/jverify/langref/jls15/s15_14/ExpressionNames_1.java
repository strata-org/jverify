package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.1 -- Expression Names: a postfix expression that is an expression
// name (local variable / field access) denotes the value of that variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.ExpressionNames_1#run")
class ExpressionNames_1 {
    static int field = 7;

    static void run() {

        int local = 5;
        check(local == 5);
    }
}
