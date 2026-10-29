package org.strata.jverify.langref.jls5.s5_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_6.NumericContexts_1#run")
class NumericContexts_1 {
    static void run() {

        // Unary numeric promotion: byte/short/char operand promoted to int.
        byte b = 5;
        int negB = -b;              // unary -, b promoted to int
        check(negB == -5);
    }
}
