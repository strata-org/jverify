package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Numeric Additive Operators: long addition wraps around modulo
// 2^64; Long.MAX_VALUE + 1L equals Long.MIN_VALUE.
class NumericAdd_LongOverflow {
    static void run() {

        long v = Long.MAX_VALUE + 1L;
        check(v == Long.MIN_VALUE);
    }
}
