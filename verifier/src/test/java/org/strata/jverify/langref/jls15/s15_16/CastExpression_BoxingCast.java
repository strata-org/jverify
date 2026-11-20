package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a cast in boxing context converts an int value
// to Integer (autoboxing).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_BoxingCast#run")
class CastExpression_BoxingCast {
    static void run() {

        Integer i = (Integer) 5;
        check(i.intValue() == 5);
    }
}
