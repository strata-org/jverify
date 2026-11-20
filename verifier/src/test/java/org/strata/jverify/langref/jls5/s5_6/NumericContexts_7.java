package org.strata.jverify.langref.jls5.s5_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_6.NumericContexts_7#run")
class NumericContexts_7 {
    static void run() {

        // Unary numeric promotion: byte/short/char operand promoted to int.
        byte b = 5;

        // byte + short -> int (both promoted to int)
        short s2 = 7;
        int sumBS = b + s2;
        check(sumBS == 12);
    }
}
