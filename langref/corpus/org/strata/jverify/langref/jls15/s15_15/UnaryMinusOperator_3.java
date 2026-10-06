package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.4 -- Unary Minus Operator -: -x yields the arithmetic negation of its operand.
class UnaryMinusOperator_3 {
    static void run() {

        // negation of Integer.MIN_VALUE overflows back to itself (two's complement)
        int min = -2147483648;
        check(-min == min);
    }
}
