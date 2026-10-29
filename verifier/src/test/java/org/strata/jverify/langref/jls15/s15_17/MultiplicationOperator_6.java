package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.MultiplicationOperator_6#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class MultiplicationOperator_6 {
    static void run() {

        // double multiplication
        double d = 2.5 * 4.0;
        check(d == 10.0);
    }
}
