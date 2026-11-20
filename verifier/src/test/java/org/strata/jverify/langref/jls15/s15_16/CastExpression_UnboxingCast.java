package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a cast in unboxing context converts an Integer
// value to int.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_UnboxingCast#run")
class CastExpression_UnboxingCast {
    static void run() {

        Integer boxed = 7;
        int i = (int) boxed;
        check(i == 7);
    }
}
