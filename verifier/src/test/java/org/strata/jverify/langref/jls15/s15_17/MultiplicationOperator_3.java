package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.MultiplicationOperator_3#run")
class MultiplicationOperator_3 {
    static void run() {

        check((-3) * (-4) == 12);
    }
}
