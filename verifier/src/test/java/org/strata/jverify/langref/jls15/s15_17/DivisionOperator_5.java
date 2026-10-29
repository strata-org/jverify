package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division truncates toward zero,
// sign rules, long division, and floating-point division.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.DivisionOperator_5#run")
class DivisionOperator_5 {
    static void run() {

        // Exact integer division
        check(12 / 4 == 3);
    }
}
