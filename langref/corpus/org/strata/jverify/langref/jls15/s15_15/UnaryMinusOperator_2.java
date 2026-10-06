package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.4 -- Unary Minus Operator -: -x yields the arithmetic negation of its operand.
class UnaryMinusOperator_2 {
    static void run() {

        int x = 7;
        check(-(-x) == 7);
    }
}
