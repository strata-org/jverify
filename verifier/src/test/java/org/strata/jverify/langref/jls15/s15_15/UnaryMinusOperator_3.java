package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.4 -- Unary Minus Operator -: -x yields the arithmetic negation of its operand.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.UnaryMinusOperator_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class UnaryMinusOperator_3 {
    static void run() {

        // negation of Integer.MIN_VALUE overflows back to itself (two's complement)
        int min = -2147483648;
        check(-min == min);
    }
}
