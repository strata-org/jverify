package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.1 -- Expression Names: a postfix expression that is an expression
// name (local variable / field access) denotes the value of that variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.ExpressionNames_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ExpressionNames_2 {
    static int field = 7;

    static void run() {

        check(field == 7);
    }
}
