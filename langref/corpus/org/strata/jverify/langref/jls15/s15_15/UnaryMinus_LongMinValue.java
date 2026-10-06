package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.4 -- Unary Minus - applied to Long.MIN_VALUE: because there is no
// representable positive counterpart, the result wraps back to Long.MIN_VALUE.
class UnaryMinus_LongMinValue {
    static void run() {

        long v = -Long.MIN_VALUE;
        check(v == Long.MIN_VALUE);
    }
}
