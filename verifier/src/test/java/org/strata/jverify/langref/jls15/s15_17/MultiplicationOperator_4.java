package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.MultiplicationOperator_4#run")
class MultiplicationOperator_4 {
    static void run() {

        // long multiplication
        long l = 1000000L * 1000000L;
        check(l == 1000000000000L);
    }
}
